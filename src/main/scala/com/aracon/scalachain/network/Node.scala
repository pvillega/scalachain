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

import cats.data.Validated
import cats.data.Validated.{ Invalid, Valid }
import com.aracon.scalachain.{ Block, FastCryptographicHash }
import com.aracon.scalachain.error._

import scala.collection.mutable

// Represents a node in the network that manages the blockchain.
// Side-effect-tastic
class Node(val nodeId: UUID) {

  val name: String = nodeId.toString.takeWhile(_ != '-')

  override def toString: String = s"Node-$name"

  // logger to help us keep an eye on node status changes
  private[this] val logger = org.log4s.getLogger

  def info(msg: String): Unit  = logger.info(s"[$name] $msg")
  def error(msg: String): Unit = logger.error(s"[$name] $msg")

  private[network] val blockchain: mutable.Buffer[Block] = mutable.Buffer.empty[Block]

  private val peers: mutable.Buffer[UUID] = mutable.Buffer.empty[UUID]

  private[network] def appendBlock(block: Block): Unit =
    asUnit {
      getLatestBlock match {
        case Some(lastestBlock) if validateReceivedBlock(lastestBlock, block).isInvalid =>
          info(
            s"Ignore invalid block $block. Triggering a network request for blockchain in case we are out of sync"
          )
          synchronizeWithNetwork()
        case _ =>
          info(s"Add block $block")
          blockchain += block
      }
    }

  def getLocalBlockchainCopy: List[Block] = blockchain.toList

  private[network] def appendPeer(peerId: UUID): Unit =
    asUnit {
      if (!peers.contains(peerId)) {
        info(s"Add peer $peerId")
        peers += peerId
      }
    }

  def getKnownPeers: List[UUID] = peers.toList

  def getLatestBlock: Option[Block] = blockchain.lastOption

  def connectToNetwork(peers: List[UUID]): Either[NetworkError, Unit] = {
    info(s"Connect to network using peers $peers")
    peers match {
      case Nil =>
        error("Couldn't connect to network as no peers for bootstrapping provided")
        Left(NoBootstrapPeers)
      case knownPeers =>
        info("Store known peers")
        knownPeers.foreach(appendPeer)
        info("Register in network map")
        asUnit { Network.network += (nodeId -> this) }
        info("Sync with network")
        synchronizeWithNetwork()
        info("Done connecting")
        Right(())
    }
  }

  // TODO: def mineBlock ??

  //TODO: method to validate chain and request new if invalid??

  private[network] def synchronizeWithNetwork(): Unit = {
    info("Sync with network - request latest block from known peers")
    queryLatestBlockFromPeers match {
      case Nil =>
        info("Sync with network - received no blocks from peers. Do nothing")
      case list =>
        val largestKnownBlockInNetwork = list.maxBy(_.index)

        if (largestKnownBlockInNetwork.index > getLatestIndexOf(getLocalBlockchainCopy)) {
          info("Sync with network - we are out of sync, request network for the latest chain")
          updateBlockchainFromNetwork()
        }
    }
  }

  private[network] def queryLatestBlockFromPeers: List[Block] =
    runOnNetwork(_.getLatestBlock).flatten

  private[network] def updateBlockchainFromNetwork(): Unit = {
    info("Request Blockchain from network - request the most up to date chain from peers")
    val allKnownChains = runOnNetwork(_.getLocalBlockchainCopy)
    info("Request Blockchain from network - select longest chain (higher index)")
    val longestChain = allKnownChains.maxBy(getLatestIndexOf)
    info(
      s"Request Blockchain from network - received longest chain with index ${getLatestIndexOf(longestChain)}. Local chain has index ${getLatestIndexOf(getLocalBlockchainCopy)}"
    )

    if (getLatestIndexOf(longestChain) > getLatestIndexOf(getLocalBlockchainCopy)) {
      info(
        "Request Blockchain from network - chain received is longer than local chain, replace local with copy"
      )
      blockchain.clear()
      asUnit { blockchain ++= longestChain.toBuffer }
    }
  }

  private[network] def validateReceivedBlock(latestLocalBlock: Block,
                                             receivedBlock: Block): Validated[BlockError, Unit] = {
    info(s"Validate received block - validating $receivedBlock")
    val calculatedHash = FastCryptographicHash.calculateHash(receivedBlock.index,
                                                             receivedBlock.previousHash,
                                                             receivedBlock.timestamp,
                                                             receivedBlock.blockData)

    if (latestLocalBlock.index + 1 != receivedBlock.index) {
      error(
        s"Validate received block - Received block has invalid index. Expected ${latestLocalBlock.index + 1}"
      )
      Invalid(WrongIndex)
    } else if (latestLocalBlock.hash != receivedBlock.previousHash) {
      error(
        s"Validate received block - Received block has invalid previous hash. Expected ${latestLocalBlock.hash}"
      )
      Invalid(WrongPreviousHash)
    } else if (calculatedHash != receivedBlock.hash) {
      error(s"Validate received block - Received block has invalid hash. Expected $calculatedHash")
      Invalid(WrongBlockHash)
    } else {
      info("Validate received block -  Block is valid")
      Valid(())
    }
  }

  private def getLatestIndexOf(chain: List[Block]): Long =
    chain.lastOption.fold(-1L)(_.index)

  private def runOnNetwork[A](f: Node => A): List[A] =
    for {
      nodeId <- getKnownPeers
      node   <- Network.network.get(nodeId)
    } yield f(node)

  // Added so we know we are purposefully ignoring the results of some expressions, as per compiler and wart-remover warnings
  private def asUnit[A](f: => A): Unit = {
    val ignoreResult = f
  }
}
