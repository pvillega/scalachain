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

import com.aracon.scalachain.block.{ Block, BlockData, SmartContract }
import com.aracon.scalachain.error.{
  InsuficientFundsSenderError,
  InvalidAmountError,
  OverflowError,
  ScalaCoinError
}

import scala.collection.mutable
import cats.implicits._

// Transaction event stored in the blockchain. In 'real life' the events also update a 'state trie' associated to the
// contract so you can retrieve the latest state without re-reading the full chain, but we don't need that for this example :)
final case class ScalaCoinTransfer(contract: UUID, from: UUID, to: UUID, amount: Long)
    extends BlockData

// This contract manages a virtual token that can be used as currency, on top of our network
// When loaded, all related events in the chain will be processed so it can retrieve the latest 'known' state in the chain.
class ScalaCoinContract(val contractUniqueId: UUID, val creatorId: UUID, val initialBalance: Long)
    extends SmartContract {
  private[this] val logger = org.log4s.getLogger

  private val contractName = contractUniqueId.toString.takeWhile(_ != '-')
  private val creatorName  = creatorId.toString.takeWhile(_ != '-')

  override def toString: String =
    s"ScalaCoin-$contractName[creator=$creatorName][initialBalance=$initialBalance]"

  private[contracts] val balances: mutable.HashMap[UUID, Long] =
    mutable.HashMap(creatorId -> initialBalance)

  def allBalances: Map[UUID, Long] = balances.toMap

  def getBalance(userId: UUID): Long = {
    val balance = balances.getOrElse(userId, 0L)
    logger.info(s"Check balance of user $userId - $balance")
    balance
  }

  def transfer(from: UUID, to: UUID, amount: Long): Either[ScalaCoinError, Unit] = {
    val result: Either[ScalaCoinError, Unit] =
      if (amount < 0) Left(InvalidAmountError)
      else if (getBalance(from) < amount) Left(InsuficientFundsSenderError)
      else if (getBalance(to) + amount < getBalance(to)) Left(OverflowError)
      else {
        // Transactionality? What for? :P
        balances.update(from, getBalance(from) - amount)
        balances.update(to, getBalance(to) + amount)
        Right(())
      }
    logger.info(s"Transfer $amount with result $result from $from to $to")
    result
  }

  def restoreContractLatestState(blockchain: List[Block]): Unit = {
    logger.info(
      s"Restoring state for 'ScalaCoinTransfer' contract $contractUniqueId with new blockchain of size ${blockchain.size}"
    )
    val onlyTransactions = blockchain
      .filter(_.blockData.isInstanceOf[ScalaCoinTransfer])
      .map(_.blockData.asInstanceOf[ScalaCoinTransfer])

    balances.clear()
    balances += (creatorId -> initialBalance)

    onlyTransactions.foreach(
      trf => if (trf.contract === contractUniqueId) transfer(trf.from, trf.to, trf.amount)
    )
  }
}
