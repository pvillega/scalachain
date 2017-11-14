/*
 * Copyright 2017 Pere Villega
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aracon.scalachain.network

import java.util.UUID

import com.aracon.scalachain.SpecNetworkPackageHelper
import com.aracon.scalachain.block.{ Block, SmartContract }
import com.aracon.scalachain.contracts.{ ScalaCoinContract, ScalaCoinTransfer }
import com.aracon.scalachain.error.{ InvalidContractReference, NoBootstrapPeers, NoNodesAvailable }
import com.aracon.scalachain.messages.{ CreateContract, TransferMoney }
import org.scalacheck.Gen
import org.scalatest.Assertion

import scala.collection.mutable

class WalletSpec extends SpecNetworkPackageHelper {

  "localBlockchainCopy" - {
    "should be empty for a new wallet" in {
      val network = new Network()
      val wallet  = new Wallet(UUID.randomUUID(), network)
      wallet.localBlockchainCopy should be(Nil)
    }
    "should be a copy of the blockchain stored in the wallet" in {
      forAll(chainGen) { chain =>
        val network = new Network()
        val wallet  = new Wallet(UUID.randomUUID(), network)
        wallet.blockchain ++= chain
        wallet.localBlockchainCopy should be(chain)
      }
    }
  }

  "requestLatestChain" - {
    "returns a Failure if the network has no nodes" in {
      val network = new Network()
      network.removeNode(network.originNode)
      val wallet = new Wallet(UUID.randomUUID(), network)

      wallet.requestLatestChain() should be(Left(NoBootstrapPeers))
    }
    "if the network has at least one active node" - {
      def verifyResult[A](f: (Network, List[Block]) => Assertion): Assertion =
        forAll(chainGen, chainGen, chainGen, nodeGen) { (chain1, chain2, chain3, node1) =>
          val network = new Network()

          val largestBlock = (chain1 ::: chain2 ::: chain3).maxBy(_.index)
          val longestChain = chain1 :+ largestBlock.copy(index = largestBlock.index + 1)

          createNewPeerForNode(node1, network, chain1)
          createNewPeerForNode(node1, network, chain2)
          createNewPeerForNode(node1, network, chain3)
          createNewPeerForNode(node1, network, longestChain)

          f(network, longestChain)
        }

      def verifyContractsLoaded(f: (Network, Wallet) => Assertion) =
        forAll(scalaCoinContractAndTransactionsGen, scalaCoinContractAndTransactionsGen) { (chain1, chain2) =>
          val network = new Network()
          network.originNode.blockchain ++= (chain1 ::: chain2)
          val wallet = new Wallet(UUID.randomUUID(), network)

          wallet.requestLatestChain()

          f(network, wallet)
        }

      "returns a Right result on request completion" in {
        verifyResult { (network, _) =>
          val wallet = new Wallet(UUID.randomUUID(), network)
          wallet.requestLatestChain() should be(Right(()))
        }
      }
      "stores locally the longest chain stored by known nodes in the network" in {
        verifyResult { (network, longestChain) =>
          val wallet = new Wallet(UUID.randomUUID(), network)
          wallet.requestLatestChain()
          wallet.localBlockchainCopy should be(longestChain)
        }
      }
      "updates the contract store with all the contracts in the blockchain that was retrieved" in {
        verifyContractsLoaded { (network, wallet) =>
          val chain = network.originNode.getLocalBlockchainCopy

          val expected = chain
            .filter(_.blockData.isInstanceOf[ScalaCoinContract])
            .map { b =>
              val contract = b.blockData.asInstanceOf[ScalaCoinContract]
              contract.contractUniqueId -> contract
            }
            .toMap

          wallet.currentContractStore should be(expected)
        }
      }
      "all contracts in the contract store have had their status updated according to contract data in the blockchain" in {
        verifyContractsLoaded { (network, wallet) =>
          val chain = network.originNode.getLocalBlockchainCopy

          val contract = chain
            .filter(_.blockData.isInstanceOf[ScalaCoinContract])
            .map(_.blockData.asInstanceOf[ScalaCoinContract])
            .head

          val onlyTransactions = chain
            .filter(_.blockData.isInstanceOf[ScalaCoinTransfer])
            .map(_.blockData.asInstanceOf[ScalaCoinTransfer])
            .filter(_.contract === contract.contractUniqueId)

          val copyCoin = new ScalaCoinContract(contract.contractUniqueId,
                                               contract.creatorId,
                                               contract.initialBalance)
          onlyTransactions.foreach { transfer =>
            copyCoin.transfer(transfer.from, transfer.to, transfer.amount)
          }
          val expectedBalances = copyCoin.allBalances

          val walletContract =
            wallet.currentContractStore(contract.contractUniqueId).asInstanceOf[ScalaCoinContract]
          walletContract.allBalances should be(expectedBalances)
        }
      }
    }
  }

  "currentContractStore" - {
    "is an empty map for a new wallet" in {
      val network = new Network()
      val wallet  = new Wallet(UUID.randomUUID(), network)
      wallet.currentContractStore should be(Map.empty)
    }

    "returns a copy of the current store status for a wallet that's storing a contract" in {
      val network  = new Network()
      val wallet   = new Wallet(UUID.randomUUID(), network)
      val contract = new ScalaCoinContract(UUID.randomUUID(), UUID.randomUUID(), 1)
      wallet.contractStore ++= mutable.HashMap(contract.contractUniqueId -> contract)

      val expected = Map(
        contract.contractUniqueId -> contract
      )
      wallet.currentContractStore should be(expected)
    }
  }

  "updateContractStore" - {
    "if blockchain is empty the contract store is not modified" in {
      val network   = new Network()
      val wallet    = new Wallet(UUID.randomUUID(), network)
      val prevState = wallet.currentContractStore

      wallet.updateContractStore(Nil)
      wallet.currentContractStore should be(prevState)
    }
    "for a non-empty chain" - {
      "all existing contracts are stored in the contract store" in {
        forAll(scalaCoinContractAndTransactionsGen) { chain =>
          val network = new Network()
          val wallet  = new Wallet(UUID.randomUUID(), network)

          val contract = chain
            .filter(_.blockData.isInstanceOf[ScalaCoinContract])
            .map(_.blockData.asInstanceOf[ScalaCoinContract])
            .head

          val expected = mutable.HashMap[UUID, SmartContract](
            contract.contractUniqueId -> contract
          )

          wallet.updateContractStore(chain)

          wallet.currentContractStore should be(expected)
        }
      }
      "multiple contracts of same type with different contract id are stored in the contract store as different contracts" in {
        forAll(scalaCoinContractAndTransactionsGen, scalaCoinContractAndTransactionsGen) { (chain1, chain2) =>
          val network = new Network()
          val wallet  = new Wallet(UUID.randomUUID(), network)

          val contract1 = chain1
            .filter(_.blockData.isInstanceOf[ScalaCoinContract])
            .map(_.blockData.asInstanceOf[ScalaCoinContract])
            .head

          val contract2 = chain2
            .filter(_.blockData.isInstanceOf[ScalaCoinContract])
            .map(_.blockData.asInstanceOf[ScalaCoinContract])
            .head

          val expected = mutable.HashMap[UUID, SmartContract](
            contract1.contractUniqueId -> contract1,
            contract2.contractUniqueId -> contract2
          )

          wallet.updateContractStore(chain1 ::: chain2)

          wallet.currentContractStore should be(expected)
        }
      }
      "contracts loaded in contract store have had their status updated according to contract data in the blockchain" in {
        forAll(scalaCoinContractAndTransactionsGen) { chain =>
          val network = new Network()
          val wallet  = new Wallet(UUID.randomUUID(), network)

          val contract = chain
            .filter(_.blockData.isInstanceOf[ScalaCoinContract])
            .map(_.blockData.asInstanceOf[ScalaCoinContract])
            .head

          val onlyTransactions = chain
            .filter(_.blockData.isInstanceOf[ScalaCoinTransfer])
            .map(_.blockData.asInstanceOf[ScalaCoinTransfer])

          val copyCoin = new ScalaCoinContract(contract.contractUniqueId,
                                               contract.creatorId,
                                               contract.initialBalance)
          onlyTransactions.foreach { transfer =>
            copyCoin.transfer(transfer.from, transfer.to, transfer.amount)
          }
          val expectedBalances = copyCoin.allBalances

          wallet.updateContractStore(chain)

          val walletContract =
            wallet.currentContractStore(contract.contractUniqueId).asInstanceOf[ScalaCoinContract]
          walletContract.allBalances should be(expectedBalances)
        }
      }
    }
  }

  "getBalanceForContract" - {

    "returns 0 if the contract id is not known" in {
      forAll(Gen.uuid) { (contractId) =>
        val network = new Network()
        val wallet  = new Wallet(UUID.randomUUID(), network)

        wallet.getBalanceForContract(contractId) should be(0)
      }
    }
    "returns 0 if the user has no balance in the given contract" in {
      forAll(Gen.uuid, Gen.uuid, Gen.uuid) { (contractId, walletId, notTheWallet) =>
        val network = new Network()
        val wallet  = new Wallet(walletId, network)

        val contract = new ScalaCoinContract(contractId, notTheWallet, 1000)
        wallet.contractStore ++= mutable.HashMap(contract.contractUniqueId -> contract)

        wallet.getBalanceForContract(contract.contractUniqueId) should be(0)
      }
    }
    "returns user balance for the given contract if the user has a balance in that contract" in {
      forAll(Gen.uuid, Gen.uuid, Gen.posNum[Long]) { (contractId, walletId, balance) =>
        val network = new Network()
        val wallet  = new Wallet(walletId, network)

        val contract = new ScalaCoinContract(contractId, walletId, balance)
        wallet.contractStore ++= mutable.HashMap(contract.contractUniqueId -> contract)

        wallet.getBalanceForContract(contract.contractUniqueId) should be(balance)
      }
    }
  }

  "sendMessage" - {
    "fails if" - {
      "the network has no nodes that can receive the message" in {
        val network = new Network()
        network.removeNode(network.originNode)

        val wallet  = new Wallet(UUID.randomUUID(), network)
        val message = TransferMoney(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L)

        wallet.sendMessage(message) should be(Left(NoNodesAvailable))
      }
      "the message can't be processed by the nodes" in {
        val network = new Network()

        val wallet = new Wallet(UUID.randomUUID(), network)
        // transfer will fail due to unknown contract
        val message = TransferMoney(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L)

        wallet.sendMessage(message) should be(Left(InvalidContractReference))
      }
    }
    "receives a success message if the node has processed the action" in {
      val network = new Network()

      val wallet = new Wallet(UUID.randomUUID(), network)
      val message =
        CreateContract(new ScalaCoinContract(UUID.randomUUID(), UUID.randomUUID(), 1000))

      wallet.sendMessage(message) should be(Right(()))
    }
    "on completion it triggers an update to retrieve the latest blockchain from the network so changes caused are visible to the wallet" in {
      val network = new Network()

      val wallet = new Wallet(UUID.randomUUID(), network)
      val message =
        CreateContract(new ScalaCoinContract(UUID.randomUUID(), UUID.randomUUID(), 1000))

      wallet.localBlockchainCopy should be(Nil)

      wallet.sendMessage(message)

      wallet.localBlockchainCopy should be(network.originNode.getLocalBlockchainCopy)
    }
  }
}
