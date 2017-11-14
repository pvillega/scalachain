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

package com.aracon.scalachain.contracts

import java.util.UUID

import com.aracon.scalachain.SpecNetworkPackageHelper
import com.aracon.scalachain.error.{
  InsuficientFundsSenderError,
  InvalidAmountError,
  OverflowError
}
import org.scalacheck.Gen

class ScalaCoinContractSpec extends SpecNetworkPackageHelper {

  "getBalance" - {
    "returns 0 if the user is not a recognised user" in {
      forAll(scalaCoinGen, Gen.uuid) { (scalaCoin, userId) =>
        whenever(scalaCoin.creatorId != userId) {
          scalaCoin.getBalance(userId) should be(0)
        }
      }
    }
    "returns the balance of the user if we can find it" in {
      forAll(scalaCoinGen, Gen.uuid, Gen.posNum[Long]) { (scalaCoin, userId, userBalance) =>
        scalaCoin.balances += (userId -> userBalance)
        scalaCoin.getBalance(userId) should be(userBalance)
      }
    }
    "on a new object the balance of the creator must be the initial balance of the currency" in {
      forAll(scalaCoinGen) { (scalaCoin) =>
        scalaCoin.getBalance(scalaCoin.creatorId) should be(scalaCoin.initialBalance)
      }
    }
  }

  "transfer" - {
    "if the amount is negative, return an error" in {
      forAll(scalaCoinGen, Gen.uuid, Gen.posNum[Long], Gen.uuid, Gen.negNum[Long]) {
        (scalaCoin, senderId, senderBalance, receiverId, amountSent) =>
          scalaCoin.balances += (senderId -> senderBalance)

          scalaCoin.transfer(senderId, receiverId, amountSent) should be(Left(InvalidAmountError))
      }
    }
    "if the sender doesn't have enough cash, return an error" in {
      forAll(scalaCoinGen, Gen.uuid, Gen.posNum[Long], Gen.uuid) {
        (scalaCoin, senderId, senderBalance, receiverId) =>
          scalaCoin.balances += (senderId -> senderBalance)

          scalaCoin.transfer(senderId, receiverId, senderBalance + 1) should be(
            Left(InsuficientFundsSenderError)
          )
      }
    }
    "if the amount would cause overflow if sent, return an error" in {
      forAll(scalaCoinGen, Gen.uuid, Gen.posNum[Long], Gen.uuid) {
        (scalaCoin, senderId, senderBalance, receiverId) =>
          scalaCoin.balances += (senderId   -> senderBalance)
          scalaCoin.balances += (receiverId -> Long.MaxValue)

          scalaCoin.transfer(senderId, receiverId, senderBalance) should be(Left(OverflowError))
      }
    }
    "after a transfer the balance of the sender has been reduced by the given amount" in {
      forAll(scalaCoinGen, Gen.uuid, Gen.posNum[Long], Gen.uuid) {
        (scalaCoin, senderId, senderBalance, receiverId) =>
          scalaCoin.balances += (senderId -> senderBalance)
          val toSend = senderBalance / 2L

          scalaCoin.transfer(senderId, receiverId, toSend)

          scalaCoin.getBalance(senderId) should be(senderBalance - toSend)
      }
    }
    "after a transfer the balance of the receiver has been increased by the given amount" in {
      forAll(scalaCoinGen, Gen.uuid, Gen.posNum[Long], Gen.uuid) {
        (scalaCoin, senderId, senderBalance, receiverId) =>
          scalaCoin.balances += (senderId -> senderBalance)
          val toSend = senderBalance / 2L

          scalaCoin.transfer(senderId, receiverId, toSend)

          scalaCoin.getBalance(receiverId) should be(toSend)
      }
    }
  }

  "allBalances" - {
    "has a single initial balance for a new contract" in {
      forAll(scalaCoinGen) { (scalaCoin) =>
        val expected = Map(
          scalaCoin.creatorId -> scalaCoin.initialBalance
        )
        scalaCoin.allBalances should be(expected)
      }
    }
    "returns all balances for all users" in {
      forAll(scalaCoinGen) { (scalaCoin) =>
        val newUser = UUID.randomUUID()
        scalaCoin.transfer(scalaCoin.creatorId, newUser, 1)

        val expected = Map(
          scalaCoin.creatorId -> (scalaCoin.initialBalance - 1),
          newUser             -> 1
        )
        scalaCoin.allBalances should be(expected)
      }
    }
  }

  "restoreContractLatestState" - {
    "given an empty block chain it doesn't modify any balances" in {
      forAll(scalaCoinGen) { (scalaCoin) =>
        val prevBalances = scalaCoin.allBalances
        scalaCoin.restoreContractLatestState(Nil)

        scalaCoin.allBalances should be(prevBalances)
      }
    }
    "given a non-empty block chain without any ScalaCoin transfer messages, it doesn't modify any balances" in {
      forAll(scalaCoinGen, chainGen) { (scalaCoin, blockchain) =>
        val prevBalances = scalaCoin.allBalances
        scalaCoin.restoreContractLatestState(blockchain)

        scalaCoin.allBalances should be(prevBalances)
      }
    }
    "given a non-empty block chain with ScalaCoin transfer messages for another contract, it doesn't modify any balances" in {
      forAll(Gen.uuid, scalaCoinContractAndTransactionsGen) { (contractId, blockchain) =>
        val contract = blockchain
          .filter(_.blockData.isInstanceOf[ScalaCoinContract])
          .map(_.blockData.asInstanceOf[ScalaCoinContract])
          .head

        // we get new contract with different contract id but same creator so transfers are not ignored due to mismatched 'from' id
        val scalaCoin    = new ScalaCoinContract(contractId, contract.creatorId, 1000)
        val prevBalances = scalaCoin.allBalances

        scalaCoin.restoreContractLatestState(blockchain)

        scalaCoin.allBalances should be(prevBalances)
      }
    }
    "given a non-empty block chain with ScalaCoin transfer messages, the new state will match the processing of all those messages in order to the initial state" in {
      forAll(scalaCoinContractAndTransactionsGen) { (blockchainWithTransfers) =>
        val contract = blockchainWithTransfers
          .filter(_.blockData.isInstanceOf[ScalaCoinContract])
          .map(_.blockData.asInstanceOf[ScalaCoinContract])
          .head

        val onlyTransactions = blockchainWithTransfers
          .filter(_.blockData.isInstanceOf[ScalaCoinTransfer])
          .map(_.blockData.asInstanceOf[ScalaCoinTransfer])

        val copyCoin = new ScalaCoinContract(contract.contractUniqueId,
                                             contract.creatorId,
                                             contract.initialBalance)
        onlyTransactions.foreach { transfer =>
          copyCoin.transfer(transfer.from, transfer.to, transfer.amount)
        }
        val expectedBalances = copyCoin.allBalances

        contract.restoreContractLatestState(blockchainWithTransfers)

        contract.allBalances should be(expectedBalances)
      }
    }
    "given a non-empty block chain with ScalaCoin transfer messages, restoring state twice in a row returns a correct value (status doesn't carry over)" in {
      forAll(scalaCoinContractAndTransactionsGen) { (blockchainWithTransfers) =>
        val contract = blockchainWithTransfers
          .filter(_.blockData.isInstanceOf[ScalaCoinContract])
          .map(_.blockData.asInstanceOf[ScalaCoinContract])
          .head

        val onlyTransactions = blockchainWithTransfers
          .filter(_.blockData.isInstanceOf[ScalaCoinTransfer])
          .map(_.blockData.asInstanceOf[ScalaCoinTransfer])

        val copyCoin = new ScalaCoinContract(contract.contractUniqueId,
                                             contract.creatorId,
                                             contract.initialBalance)
        onlyTransactions.foreach { transfer =>
          copyCoin.transfer(transfer.from, transfer.to, transfer.amount)
        }
        val expectedBalances = copyCoin.allBalances

        contract.restoreContractLatestState(blockchainWithTransfers)
        contract.restoreContractLatestState(blockchainWithTransfers)

        contract.allBalances should be(expectedBalances)
      }
    }
  }

}
