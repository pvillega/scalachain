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

import com.aracon.scalachain.block.{ Block, SmartContract }
import com.aracon.scalachain.contracts.ScalaCoinContract
import com.aracon.scalachain.error.{
  MessageError,
  NetworkError,
  NoBootstrapPeers,
  NoNodesAvailable
}
import com.aracon.scalachain.messages.{ CreateContract, Message, TransferMoney }

import scala.collection.mutable
import scala.util.Random

// A wallet is a client that connects to the network to run operations on it
class Wallet(val walletId: UUID, network: Network) {

  private[this] val logger = org.log4s.getLogger

  private val name: String = walletId.toString.takeWhile(_ != '-')

  override def toString: String = s"Wallet-$name"

  def info(msg: String): Unit = logger.info(s"[$toString] $msg")

  def error(msg: String): Unit = logger.error(s"[$toString] $msg")

  private[network] val blockchain: mutable.Buffer[Block] = mutable.Buffer.empty[Block]

  def localBlockchainCopy: List[Block] = blockchain.toList

  def requestLatestChain(): Either[NetworkError, Unit] = {
    info(s"Obtain latest known chain from a network node in $network")
    network.getAllNodes.toList match {
      case Nil => Left(NoBootstrapPeers)
      case nodeList =>
        val longestChain = nodeList
          .map(_.getLocalBlockchainCopy)
          .fold(List[Block]()) { (chain1, chain2) =>
            (chain1.lastOption, chain2.lastOption) match {
              case (Some(block1), Some(block2)) if block1.index >= block2.index => chain1
              case (Some(block1), Some(block2)) if block1.index < block2.index  => chain2
              case (Some(_), None)                                              => chain1
              case (None, Some(_))                                              => chain2
              case _                                                            => List[Block]()
            }
          }

        info(s"Longest chain found ends with block ${longestChain.lastOption}")
        blockchain.clear()
        blockchain ++= longestChain
        updateContractStore(localBlockchainCopy)
        Right(())
    }
  }

  // references all known contracts in the blockchain for easy access, updated with each blockchain update
  private[network] val contractStore: mutable.HashMap[UUID, SmartContract] =
    mutable.HashMap.empty[UUID, SmartContract]

  def currentContractStore: Map[UUID, SmartContract] = contractStore.toMap

  private[network] def updateContractStore(blockchain: List[Block]): Unit = {
    info(s"Updating contract store")

    blockchain.filter(_.blockData.isInstanceOf[SmartContract]) match {
      case Nil => ()
      case list =>
        contractStore.clear()
        list.foreach { block =>
          val contract = block.blockData.asInstanceOf[SmartContract]
          contract.restoreContractLatestState(blockchain)
          contractStore += contract.contractUniqueId -> contract
        }
    }
  }

  def getBalanceForContract(contractId: UUID): Long = {
    info(s"Retrieving wallet balance in contract $contractId")
    contractStore
      .get(contractId)
      .map(_.asInstanceOf[ScalaCoinContract].getBalance(walletId))
      .getOrElse(0L)
  }

  private[network] def sendMessage(message: Message): Either[MessageError, Unit] = {
    info(s"Sending message $message to a node in network $network")
    Random.shuffle(network.getAllNodes).headOption match {
      case None => Left(NoNodesAvailable)
      case Some(node) =>
        val result = node.processMessage(message)
        requestLatestChain()
        result
    }
  }

  def transferMoney(contractId: UUID, to: UUID, amount: Long): Either[MessageError, Unit] = {
    val message = TransferMoney(contractId, walletId, to, amount)
    sendMessage(message)
  }

  def createContract(contract: SmartContract): Either[MessageError, Unit] = {
    val message = CreateContract(contract)
    sendMessage(message)
  }

}
