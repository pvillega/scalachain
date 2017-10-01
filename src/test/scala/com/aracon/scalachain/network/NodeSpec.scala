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

import cats.data.NonEmptyList
import cats.data.Validated.{ Invalid, Valid }
import com.aracon.scalachain.SpecNetworkPackageHelper
import com.aracon.scalachain.crypto.FastCryptographicHash
import com.aracon.scalachain.error._
import com.aracon.scalachain.block.{ Block, EmptyBlockData }
import com.aracon.scalachain.contracts.{ ScalaCoinContract, ScalaCoinTransfer }
import com.aracon.scalachain.messages.{ CreateContract, ScalaCoinMessage, TransferMoney }
import org.scalacheck.Gen

class NodeSpec extends SpecNetworkPackageHelper {

  "appendPeer" - {
    "appends a new peer to the list of peers" in fixture { (node, _) =>
      forAll(nodeGen, nodeGen) { (node, peer) =>
        node.appendPeer(peer)

        node.getKnownPeers should be(List(peer))
      }
    }
    "if the peerId received was already known, we do nothing" in fixture { (node, _) =>
      forAll(nodeGen, nodeGen) { (node, peer) =>
        node.appendPeer(peer)
        node.appendPeer(peer)
        node.appendPeer(peer)

        node.getKnownPeers should be(List(peer))
      }
    }
  }

  "connectToNetwork" - {
    "if no bootstrap nodes are provided" - {
      val bootstrapPeers: List[UUID] = Nil
      "method returns an error state" in fixture { (node, network) =>
        node.connectToNetwork(bootstrapPeers, network) should be(Left(NoBootstrapPeers))
      }
      "local copy of the blockchain is empty" in fixture { (node, network) =>
        node.connectToNetwork(bootstrapPeers, network)

        node.getLocalBlockchainCopy should be(Nil)
      }
      "the list of known peers is empty" in fixture { (node, network) =>
        node.connectToNetwork(bootstrapPeers, network)

        node.getKnownPeers should be(Nil)
      }
      "node is not part of the network" in fixture { (node, network) =>
        node.connectToNetwork(bootstrapPeers, network)

        network.getNode(node.nodeId) should be(None)
      }
    }
    "once completed" - {
      def connectionCompletedFixture(f: (List[UUID], List[Block], Network) => Unit): Unit =
        forAll(chainGen, chainGen, chainGen, chainGen, nodeGen) {
          (chain1, chain2, chain3, chain4, someNode) =>
            val network = new Network()

            val largestBlock = (chain1 ::: chain2 ::: chain3 ::: chain4).maxBy(_.index)
            val longestChain = chain3 :+ largestBlock.copy(index = largestBlock.index + 1)

            someNode.blockchain ++= chain4.toBuffer

            createNewPeerForNode(someNode, network, chain1)
            createNewPeerForNode(someNode, network, chain2)
            createNewPeerForNode(someNode, network, longestChain)

            val bootstrapPeers: List[UUID] = network.getAllNodes.toList.map(_.nodeId)
            val bootstrapChain             = longestChain

            f(bootstrapPeers, bootstrapChain, network)
        }

      "method returns success" in connectionCompletedFixture { (bootstrapPeers, _, network) =>
        val node = new Node(UUID.randomUUID())
        node.connectToNetwork(bootstrapPeers, network) should be(Right(()))
      }
      "the node has a local copy of the network blockchain" in connectionCompletedFixture {
        (bootstrapPeers, bootstrapChain, network) =>
          val node = new Node(UUID.randomUUID())
          node.connectToNetwork(bootstrapPeers, network)

          node.getLocalBlockchainCopy should be(bootstrapChain)
      }
      "the node stores bootstrap list of nodes" in connectionCompletedFixture {
        (bootstrapPeers, _, network) =>
          val node = new Node(UUID.randomUUID())
          node.connectToNetwork(bootstrapPeers, network)

          node.getKnownPeers.map(_.nodeId).toSet should be(bootstrapPeers.toSet)
      }
      "node becomes part of the network" in connectionCompletedFixture {
        (bootstrapPeers, _, network) =>
          val node = new Node(UUID.randomUUID())
          node.connectToNetwork(bootstrapPeers, network)

          network.getNode(node.nodeId) should contain(node)
      }
    }
  }

  "getLatestBlock" - {
    "returns None if we don't have a local copy of the blockchain" in fixture { (node, _) =>
      node.getLatestBlock should be(None)
    }
    "returns the latest block of the local copy of the blockchain" in fixture { (node, _) =>
      node.blockchain += emptyBlock

      node.getLatestBlock should contain(emptyBlock)
    }
  }

  "synchronizeWithNetwork" - {
    "does nothing if the node receive no nodes from peers" - {
      "due to no peers" in fixtureGen { (chain1, chain2, node, _) =>
        val largestBlock = (chain1 ::: chain2).maxBy(_.index)
        node.blockchain += largestBlock

        node.synchronizeWithNetwork()

        node.getLocalBlockchainCopy should be(List(largestBlock))
      }
      "due to peers with no blocks" in fixtureGen { (chain1, chain2, node, network) =>
        val largestBlock = (chain1 ::: chain2).maxBy(_.index)
        node.blockchain += largestBlock

        createNewPeerForNode(node, network)
        createNewPeerForNode(node, network)

        node.synchronizeWithNetwork()

        node.getLocalBlockchainCopy should be(List(largestBlock))
      }
    }
    "if a latest block received has an index larger than the current latest block in the local chain, requests a copy of the blockchain from the network and updates the local copy" in fixtureGen {
      (chain1, chain2, node, network) =>
        val largestBlock = (chain1 ::: chain2).maxBy(_.index)
        node.blockchain += Block(largestBlock.index - 1, "", 0L, "", EmptyBlockData)

        createNewPeerForNode(node, network, chain1)
        createNewPeerForNode(node, network, chain2)

        node.synchronizeWithNetwork()

        val expected =
          if (chain1.lastOption.fold(-1L)(_.index) >= chain2.lastOption.fold(-1L)(_.index)) chain1
          else chain2
        node.getLocalBlockchainCopy should be(expected)
    }
    "if the local chain fails validation it requests a copy of the blockchain from the network even if the node already has the latest block known, as we may have local corruption" in fixtureGen {
      (chain1, chain2, node, network) =>
        val largestBlock = (chain1 ::: chain2).maxBy(_.index)
        node.blockchain ++= (chain1 :+ largestBlock)

        val expected = chain2 :+ largestBlock

        createNewPeerForNode(node, network, expected)
        createNewPeerForNode(node, network, expected)

        node.synchronizeWithNetwork()

        node.getLocalBlockchainCopy should be(expected)
    }
    "if the node already has the latest block know by the network and a valid chain, it stops here" in fixtureGen {
      (chain1, chain2, node, network) =>
        val hashBlock1 = FastCryptographicHash.calculateBlockHash(1L, "", 0L, EmptyBlockData)
        val block1     = Block(1L, "", 0L, hashBlock1, EmptyBlockData)
        node.blockchain += block1

        forAll(Gen.posNum[Int]) { length =>
          // create a valid chain of certain length
          (1 to length).foreach { _ =>
            val nextBlock = node.createNextBlock(EmptyBlockData)
            node.blockchain += nextBlock
          }
        }

        val largestBlock = node.getLatestBlock.get

        createNewPeerForNode(node, network, chain1 :+ largestBlock)
        createNewPeerForNode(node, network, chain2 :+ largestBlock)

        val expected = node.getLocalBlockchainCopy

        node.synchronizeWithNetwork()

        node.getLocalBlockchainCopy should be(expected)
    }
  }

  "validateLocalBlockchain" - {
    "returns invalid if the local chain is empty as we should always have at least the origin node" in fixture {
      (node, _) =>
        node.validateLocalBlockchain should be(Invalid(NonEmptyList.of(InvalidLocalChainError)))
    }
    "returns invalid if the local chain has at least an invalid node" in fixtureGen {
      (start, end, node, _) =>
        // we use 2 chain generators as we consider a single node a valid case and chainGen can create single-node-chains
        node.blockchain ++= (start ::: end)

        node.validateLocalBlockchain should be(Invalid(NonEmptyList.of(InvalidLocalChainError)))
    }
    "returns valid if our local chain has a single node as we assume it to be the origin node" in fixture {
      (node, _) =>
        node.blockchain += emptyBlock

        node.validateLocalBlockchain should be(Valid(()))
    }
    "returns valid if our local chain is valid" in fixture { (node, network) =>
      val hashBlock1 = FastCryptographicHash.calculateBlockHash(1L, "", 0L, EmptyBlockData)
      val block1     = Block(1L, "", 0L, hashBlock1, EmptyBlockData)
      node.blockchain ++= (block1 :: Nil).toBuffer

      forAll(Gen.posNum[Int]) { length =>
        // create a valid chain of certain length
        (1 to length).foreach { _ =>
          val nextBlock = node.createNextBlock(EmptyBlockData)
          node.blockchain += nextBlock
        }

        node.validateLocalBlockchain should be(Valid(()))
      }
    }
  }

  "queryLatestBlockFromPeers" - {
    "returns Nil if there are no known peers" in fixture { (node, _) =>
      node.queryLatestBlockFromPeers should be(Nil)
    }
    "retrieves the latest block of all known peers" in fixtureGen {
      (chain1, chain2, node, network) =>
        createNewPeerForNode(node, network, chain1)
        createNewPeerForNode(node, network, chain2)

        val expected = List(chain1.last, chain2.last)
        node.queryLatestBlockFromPeers should be(expected)
    }
  }

  "replaceLocalBlockchainWithNetworkCopy" - {
    "stores the longest chain from the network, based on node index" in fixtureGen {
      (chain1, chain2, node, network) =>
        createNewPeerForNode(node, network, chain1)
        createNewPeerForNode(node, network, chain2)

        node.replaceLocalBlockchainWithNetworkCopy()

        val expected =
          if (chain1.lastOption.fold(-1L)(_.index) >= chain2.lastOption.fold(-1L)(_.index)) chain1
          else chain2
        node.getLocalBlockchainCopy should be(expected)
    }
    "doesn't replace the local chain if the network returned empty chains" in fixtureGen {
      (chain1, _, node, network) =>
        node.blockchain ++= chain1

        createNewPeerForNode(node, network)
        createNewPeerForNode(node, network)

        node.replaceLocalBlockchainWithNetworkCopy()

        node.getLocalBlockchainCopy should be(chain1)
    }
  }

  "createNextBlock" - {
    "block created has index of last known block + 1" in fixture { (node, network) =>
      val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)
      node.blockchain += lastBlock

      node.createNextBlock(EmptyBlockData).index should be(2L)

    }
    "block created has previous hash matching hash of last known block" in fixture { (node, _) =>
      val lastBlock = Block(1L, "", 0L, "123qwe456", EmptyBlockData)
      node.blockchain += lastBlock

      node.createNextBlock(EmptyBlockData).previousHash should be("123qwe456")
    }
    "block created has expected hash" in fixture { (node, _) =>
      val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)
      node.blockchain += lastBlock

      val newBlock = node.createNextBlock(EmptyBlockData)

      val expectedHash =
        FastCryptographicHash.calculateBlockHash(2L, "", newBlock.timestamp, EmptyBlockData)

      newBlock.hash should be(expectedHash)
    }
    "block created contains given block data" in fixture { (node, _) =>
      val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)
      node.blockchain += lastBlock

      node.createNextBlock(EmptyBlockData).blockData should be(EmptyBlockData)
    }
  }

  "addBlockToNetwork" - {
    "appends the new block to the local chain" in fixture { (node, _) =>
      node.blockchain += emptyBlock

      node.getLocalBlockchainCopy.length should be(1)

      node.addBlockToNetwork(EmptyBlockData)

      node.getLocalBlockchainCopy.length should be(2)

      val newBlock = node.getLatestBlock.head
      newBlock.index should be(1L)
      newBlock.previousHash should be("")
      newBlock.blockData should be(EmptyBlockData)
    }
    "sends the new block to all the known peers" in fixture { (node, network) =>
      node.blockchain += emptyBlock

      val peer1 = createNewPeerForNode(node, network, List(emptyBlock))
      val peer2 = createNewPeerForNode(node, network, List(emptyBlock))

      node.addBlockToNetwork(EmptyBlockData)
      val newBlock = node.getLatestBlock.head

      peer1.getLocalBlockchainCopy.length should be(2)
      peer1.getLatestBlock.head should be(newBlock)

      peer2.getLocalBlockchainCopy.length should be(2)
      peer2.getLatestBlock.head should be(newBlock)
    }
    "causes peers to synchronize with the network after receiving the block if it is not the next in sequence they expected" in fixture {
      (node, network) =>
        node.blockchain += emptyBlock

        val peer1 =
          createNewPeerForNode(node, network, Block(-1L, "", 0L, "", EmptyBlockData) :: Nil)

        node.addBlockToNetwork(EmptyBlockData)

        peer1.getLocalBlockchainCopy should be(node.getLocalBlockchainCopy)
    }
    "if two nodes generate the same block index, ????" in {
      //TODO: proof of work/stake here??
      fail()
    }

  }

  "validateReceivedBlock" - {
    "will fail validation" - {
      "if the index is not the index of the latest block known by the node + 1" in fixture {
        (node, network) =>
          val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)

          forAll(Gen.chooseNum(3L, Long.MaxValue)) { wrongIndex =>
            val hash =
              FastCryptographicHash.calculateBlockHash(wrongIndex,
                                                       lastBlock.hash,
                                                       0L,
                                                       EmptyBlockData)
            val faultyIndexBlock = Block(wrongIndex, lastBlock.hash, 0L, hash, EmptyBlockData)
            node.validateReceivedBlock(lastBlock, faultyIndexBlock) should be(
              Invalid(NonEmptyList.of(WrongIndex))
            )
          }
      }
      "if the previous hash of the new block doesn't match the hash of the previous block in sequence" in fixture {
        (node, network) =>
          forAll(Gen.alphaNumStr) { wrongHash =>
            val lastBlock = Block(1L, "", 0L, wrongHash + "1", EmptyBlockData)

            val hash =
              FastCryptographicHash.calculateBlockHash(2L, wrongHash, 0L, EmptyBlockData)
            val faultyPreviousHashBlock = Block(2L, wrongHash, 0L, hash, EmptyBlockData)
            node.validateReceivedBlock(lastBlock, faultyPreviousHashBlock) should be(
              Invalid(NonEmptyList.of(WrongPreviousHash))
            )
          }
      }
      "if the hash of the block received doesn't match the hash calculated for the block by the current node" in fixture {
        (node, network) =>
          val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)

          forAll(Gen.alphaNumStr) { wrongHash =>
            val faultyHashBlock = Block(2L, lastBlock.hash, 0L, wrongHash, EmptyBlockData)
            node.validateReceivedBlock(lastBlock, faultyHashBlock) should be(
              Invalid(NonEmptyList.of(WrongBlockHash))
            )
          }
      }
    }
    "will pass validation if the block is a valid one" in fixture { (node, network) =>
      val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)

      val hash       = FastCryptographicHash.calculateBlockHash(2L, lastBlock.hash, 0L, EmptyBlockData)
      val validBlock = Block(2L, lastBlock.hash, 0L, hash, EmptyBlockData)

      node.validateReceivedBlock(lastBlock, validBlock) should be(Valid(()))
    }
  }

  "appendBlock" - {
    "if the new block fails validation" - {
      "the new block is not added to the local chain" in fixture { (node, _) =>
        node.blockchain += emptyBlock

        val faultyHashBlock =
          Block(1L, emptyBlock.hash, 0L, UUID.randomUUID().toString, EmptyBlockData)

        node.appendBlock(faultyHashBlock)

        node.getLatestBlock should contain(emptyBlock)
      }
      "triggers a synchronize network to ensure we have a current copy of the block chain as we may be out of date" in fixtureGen {
        (chain1, _, node, network) =>
          node.blockchain += emptyBlock

          createNewPeerForNode(node, network, chain1)

          val faultyBlock =
            Block(20L, UUID.randomUUID().toString, 0L, UUID.randomUUID().toString, EmptyBlockData)

          node.appendBlock(faultyBlock)

          node.getLocalBlockchainCopy should be(chain1)
      }
    }
    "if the new block passes validation" - {
      "append block to the end of current local copy of chain" in fixture { (node, _) =>
        node.blockchain += emptyBlock

        val hash =
          FastCryptographicHash.calculateBlockHash(1L, emptyBlock.hash, 0L, EmptyBlockData)
        val validBlock = Block(1L, emptyBlock.hash, 0L, hash, EmptyBlockData)

        node.appendBlock(validBlock)

        node.getLatestBlock should contain(validBlock)
      }
    }
  }

  "findContractById" - {
    "returns None if there is no contract in the chain with the given id" in fixture { (node, _) =>
      forAll(Gen.uuid, Gen.uuid, Gen.uuid, Gen.posNum[Long]) {
        (contractId, anotherId, from, amount) =>
          whenever(contractId !== anotherId) {
            val scalaCoinContract = new ScalaCoinContract(contractId, from, amount)
            val block             = Block(0, "", 0, "", scalaCoinContract)
            node.blockchain += block

            node.findContractById(anotherId) should be(None)
          }
      }
    }
    "returns the contract with the given id if it is stored in the blockchain" in fixture {
      (node, _) =>
        forAll(Gen.uuid, Gen.uuid, Gen.posNum[Long]) { (contractId, from, amount) =>
          val scalaCoinContract = new ScalaCoinContract(contractId, from, amount)
          val block             = Block(0, "", 0, "", scalaCoinContract)
          node.blockchain += block

          node.findContractById(contractId) should be(Some(scalaCoinContract))
        }
    }
    "A found contract includes the latest state stored in the blockchain for that contract" in fixture {
      (node, _) =>
        forAll(Gen.uuid, Gen.uuid, Gen.uuid, Gen.posNum[Long]) { (contractId, from, to, amount) =>
          val scalaCoinContract = new ScalaCoinContract(contractId, from, amount)
          val blocks =
          Block(0, "", 0, "", scalaCoinContract) ::
          Block(0, "", 0, "", ScalaCoinTransfer(contractId, from, to, 1)) ::
          Block(0, "", 0, "", ScalaCoinTransfer(contractId, to, from, 1)) ::
          Nil
          node.blockchain ++= blocks

          val expected = Map(
            from -> amount,
            to   -> 0L
          )

          val contract = node.findContractById(contractId).get.asInstanceOf[ScalaCoinContract]
          contract.allBalances should be(expected)
        }
    }
  }

  "processMessage" - {
    "returns error if we don't recognise the message" in {
      val node = new Node(UUID.randomUUID())
      node.processMessage(new ScalaCoinMessage {}) should be(Left(UnknownMessage))
    }
    "CreateContract message" - {
      "returns error" - {
        "if a contract already exists with the same unique id in the chain" in withNode { (node) =>
          forAll(Gen.uuid, Gen.uuid, Gen.posNum[Long]) { (contractId, from, amount) =>
            val scalaCoinContract = new ScalaCoinContract(contractId, from, amount)
            val contractCreation  = CreateContract(scalaCoinContract)

            node.processMessage(contractCreation)

            node.processMessage(contractCreation) should be(Left(ContractReferenceAlreadyExists))
          }
        }
      }
      "if message is valid" - {
        "adds to the chain a new block that contains the received contract" in withNode { (node) =>
          forAll(Gen.uuid, Gen.uuid, Gen.posNum[Long]) { (contractId, from, amount) =>
            val scalaCoinContract = new ScalaCoinContract(contractId, from, amount)
            val contractCreation  = CreateContract(scalaCoinContract)

            node.processMessage(contractCreation)

            node.getLocalBlockchainCopy.last.blockData should be(scalaCoinContract)
          }
        }
        "returns ok" in withNode { (node) =>
          forAll(Gen.uuid, Gen.uuid, Gen.posNum[Long]) { (contractId, from, amount) =>
            val contractCreation = CreateContract(new ScalaCoinContract(contractId, from, amount))

            node.processMessage(contractCreation) should be(Right(()))
          }
        }
      }
    }
    "TransferMoney message" - {
      "returns error" - {
        "if we can't find an existing ScalaCoin contract with the given id" in withNode { (node) =>
          forAll(Gen.uuid, Gen.uuid, Gen.uuid, Gen.posNum[Long]) {
            (contractId, from, to, amount) =>
              val transferMoney = TransferMoney(contractId, from, to, amount)
              node.processMessage(transferMoney) should be(Left(InvalidContractReference))
          }
        }
        "if contract fails processing message" in withNode { (node) =>
          forAll(Gen.uuid, Gen.uuid, Gen.uuid, Gen.posNum[Long]) {
            (contractId, from, to, amount) =>
              val contract      = CreateContract(new ScalaCoinContract(contractId, from, amount))
              val transferMoney = TransferMoney(contractId, from, to, amount + 1)

              node.processMessage(contract)
              node.processMessage(transferMoney) should be(
                Left(ContractRejectedMessage(InsuficientFundsSenderError))
              )
          }
        }
      }
      "if message is valid" - {
        "adds to the chain a new ScalaCoinTransfer with the same data as the received message" in withNode {
          (node) =>
            forAll(Gen.uuid, Gen.uuid, Gen.uuid, Gen.posNum[Long]) {
              (contractId, from, to, amount) =>
                val contract      = CreateContract(new ScalaCoinContract(contractId, from, amount))
                val transferMoney = TransferMoney(contractId, from, to, amount)

                node.processMessage(contract)
                node.processMessage(transferMoney)

                node.getLocalBlockchainCopy.last.blockData should be(
                  ScalaCoinTransfer(contractId, from, to, amount)
                )
            }
        }
        "returns ok" in withNode { (node) =>
          forAll(Gen.uuid, Gen.uuid, Gen.uuid, Gen.posNum[Long]) {
            (contractId, from, to, amount) =>
              val contract      = CreateContract(new ScalaCoinContract(contractId, from, amount))
              val transferMoney = TransferMoney(contractId, from, to, amount)

              node.processMessage(contract)
              node.processMessage(transferMoney) should be(Right(()))
          }
        }
      }
    }
  }

  private def fixture(f: (Node, Network) => Unit): Unit = {
    val network = new Network()
    val node    = new Node(UUID.randomUUID())

    f(node, network)
  }

  private def withNode(f: (Node) => Unit): Unit = {
    val network = new Network()
    val node    = new Node(UUID.randomUUID())

    node.appendPeer(network.originNode)
    network.addNode(node)

    f(node)
  }

  private def fixtureGen(f: (List[Block], List[Block], Node, Network) => Unit): Unit =
    forAll(chainGen, chainGen, nodeGen) { (chain1, chain2, node) =>
      val network = new Network()
      f(chain1, chain2, node, network)
    }
}
