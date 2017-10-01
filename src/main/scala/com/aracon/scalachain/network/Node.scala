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

import cats._
import cats.data._
import cats.implicits._
import com.aracon.scalachain.crypto.FastCryptographicHash
import com.aracon.scalachain.block.{ Block, BlockData, SmartContract }
import com.aracon.scalachain.contracts.{ ScalaCoinContract, ScalaCoinTransfer }
import com.aracon.scalachain.error.{ InvalidLocalChainError, _ }
import com.aracon.scalachain.messages.{ CreateContract, Message, TransferMoney }

import scala.collection.mutable

// Represents a node in the network that manages the blockchain.
// Side-effect-tastic code :)
class Node(val nodeId: UUID) {

  val name: String = nodeId.toString.takeWhile(_ != '-')

  override def toString: String = s"Node-$name"

  // logger to help us keep an eye on node status changes
  private[this] val logger = org.log4s.getLogger

  def info(msg: String): Unit = logger.info(s"[$toString] $msg")

  def error(msg: String): Unit = logger.error(s"[$toString] $msg")

  private[scalachain] val blockchain: mutable.Buffer[Block] = mutable.Buffer.empty[Block]

  private val peers: mutable.Buffer[Node] = mutable.Buffer.empty[Node]

  private[network] def appendBlock(block: Block): Unit =
    getLatestBlock match {
      case Some(latestBlock) if validateReceivedBlock(latestBlock, block).isValid =>
        info(s"Add block $block")
        blockchain += block

      case _ =>
        info(
          s"Ignore invalid block $block. Triggering a network request for blockchain in case we are out of sync"
        )
        synchronizeWithNetwork()
    }

  def getLocalBlockchainCopy: List[Block] = blockchain.toList

  private[scalachain] def appendPeer(peer: Node): Unit =
    if (!peers.contains(peer)) {
      info(s"Add peer $peer")
      peers += peer
    }

  def getKnownPeers: List[Node] = peers.toList

  def getLatestBlock: Option[Block] = blockchain.lastOption

  def connectToNetwork(peers: List[UUID], network: Network): Either[NetworkError, Unit] = {
    info(s"Connect to network using peers $peers")
    peers match {
      case Nil =>
        error("Couldn't connect to network as no peers for bootstrapping provided")
        Left(NoBootstrapPeers)
      case knownPeers =>
        info("Register in network map")
        network.addNode(this)
        info("Store known peers")
        knownPeers.foreach { nodeId =>
          network.getNode(nodeId).fold(()) { node =>
            appendPeer(node)
          }
        }
        info("Sync with network")
        synchronizeWithNetwork()
        info("Done connecting")
        Right(())
    }
  }

  def processMessage(message: Message): Either[MessageError, Unit] = message match {
    case CreateContract(smartContract) =>
      findContractById(smartContract.contractUniqueId).fold[Either[MessageError, Unit]](
        Right(addBlockToNetwork(smartContract))
      )(_ => Left(ContractReferenceAlreadyExists))
    case TransferMoney(contractId, from, to, amount) =>
      findContractById(contractId)
        .fold[Either[MessageError, Unit]](Left(InvalidContractReference)) { contract =>
          val scalaCoinContract = contract.asInstanceOf[ScalaCoinContract]
          scalaCoinContract
            .transfer(from, to, amount)
            .fold(
              err => Left(ContractRejectedMessage(err)),
              _ => {
                val transferAction = ScalaCoinTransfer(contractId, from, to, amount)
                Right(addBlockToNetwork(transferAction))
              }
            )
        }
    case _ => Left(UnknownMessage)
  }

  private[network] def findContractById(contractId: UUID): Option[SmartContract] =
    getLocalBlockchainCopy
      .find(
        b =>
          b.blockData.isInstanceOf[SmartContract] && b.blockData
            .asInstanceOf[SmartContract]
            .contractUniqueId === contractId
      )
      .map { b =>
        val contract = b.blockData.asInstanceOf[SmartContract]
        contract.restoreContractLatestState(getLocalBlockchainCopy)
        contract
      }

  def addBlockToNetwork(blockData: BlockData): Unit = {
    info(s"Add new block to network - with data $blockData")
    val newBlock = createNextBlock(blockData)
    appendBlock(newBlock)
    info(s"Add new block to network - block added to local chain, broadcasting to network")
    runOnNetwork(_.appendBlock(newBlock))
    info(s"Add new block to network - broadcast completed")
  }

  private[network] def createNextBlock(blockData: BlockData): Block = {
    info(s"Create new block - with data $blockData")
    getLatestBlock match {
      case None =>
        // this *should* never happen as when we join the network we will get the latest blockchain, which should contain at least the origin node
        error(
          "Create new block - we couldn't load latest local block, syncing with network and retrying"
        )
        synchronizeWithNetwork()
        createNextBlock(blockData)
      case Some(last) =>
        val timestamp = System.currentTimeMillis()
        val hash =
          FastCryptographicHash.calculateBlockHash(last.index + 1, last.hash, timestamp, blockData)
        Block(last.index + 1, last.hash, timestamp, hash, blockData)
    }
  }

  private[network] def synchronizeWithNetwork(): Unit = {
    info(s"Sync with network - request latest block from ${peers.size} known peers")
    queryLatestBlockFromPeers match {
      case Nil =>
        info(
          "Sync with network - received no blocks from peers. Do nothing as we may be disconnected from network"
        )
      case list =>
        val largestKnownBlockInNetwork = list.maxBy(_.index)
        val missingLargestNetworkBlock = largestKnownBlockInNetwork.index > getLatestIndexOf(
          getLocalBlockchainCopy
        )
        val invalidLocalChain = validateLocalBlockchain.isInvalid

        if (invalidLocalChain || missingLargestNetworkBlock) {
          info("Sync with network - we are out of sync, request network for the latest chain")
          replaceLocalBlockchainWithNetworkCopy()
        }
    }
  }

  private[network] def validateLocalBlockchain: ValidatedNel[InvalidLocalChainError.type, Unit] =
    getLocalBlockchainCopy match {
      case Nil =>
        error(
          "Validate local chain - local chain is Empty, return failure as we should always have at least the origin node!"
        )
        InvalidLocalChainError.invalidNel[Unit]
      case _ :: Nil =>
        info(
          "Validate local chain - local chain has a single node, we assume it is the origin node and mark it as valid. Otherwise we could get infinite loops in a new chain."
        )
        ().validNel[InvalidLocalChainError.type]
      case localCopy =>
        info("Validate local chain - local chain has multiple nodes, validating each one.")
        localCopy
          .zip(localCopy.drop(1))
          .map { case (prev, next) => validateReceivedBlock(prev, next) }
          .foldLeft(().validNel[BlockError])((a, b) => a.combine(b))
          .leftMap(_ => NonEmptyList.of(InvalidLocalChainError))
    }

  private[network] def queryLatestBlockFromPeers: List[Block] =
    runOnNetwork(_.getLatestBlock).flatten

  private[network] def replaceLocalBlockchainWithNetworkCopy(): Unit = {
    info("Replace Blockchain with network copy - request the most up to date chain from peers")
    val allKnownChains = runOnNetwork(_.getLocalBlockchainCopy)
    info("Replace Blockchain with network copy - select longest chain (higher index)")
    allKnownChains.maxBy(getLatestIndexOf) match {
      case Nil =>
        info(
          "Replace Blockchain with network copy - we received empty chains from peers, ignoring replacement step."
        )
      case longestChain =>
        info(
          s"Replace Blockchain with network copy - received longest chain with index ${getLatestIndexOf(longestChain)}. Local chain has index ${getLatestIndexOf(getLocalBlockchainCopy)}"
        )
        blockchain.clear()
        blockchain ++= longestChain.toBuffer
    }
  }

  private[network] def validateReceivedBlock(
      latestLocalBlock: Block,
      receivedBlock: Block
  ): ValidatedNel[BlockError, Unit] = {
    info(s"Validate received block - validating $receivedBlock")
    val calculatedHash = FastCryptographicHash.calculateBlockHash(receivedBlock.index,
                                                                  receivedBlock.previousHash,
                                                                  receivedBlock.timestamp,
                                                                  receivedBlock.blockData)
    val v = ().validNel[BlockError]

    val indexValidation = v.ensure {
      error(
        s"Validate received block - Received block has invalid index. Expected ${latestLocalBlock.index + 1}"
      )
      NonEmptyList.of(WrongIndex)
    }(_ => latestLocalBlock.index + 1 === receivedBlock.index)

    val previousHashValidation = v.ensure {
      error(
        s"Validate received block - Received block has invalid previous hash. Expected ${latestLocalBlock.hash}"
      )
      NonEmptyList.of(WrongPreviousHash)
    }(_ => latestLocalBlock.hash === receivedBlock.previousHash)

    val hashValidation = v.ensure {
      error(s"Validate received block - Received block has invalid hash. Expected $calculatedHash")
      NonEmptyList.of(WrongBlockHash)
    }(_ => calculatedHash === receivedBlock.hash)

    (indexValidation |@| previousHashValidation |@| hashValidation).map { (_, _, _) =>
      ()
    }

  }

  private def getLatestIndexOf(chain: List[Block]): Long =
    chain.lastOption.fold(-1L)(_.index)

  private def runOnNetwork[A](f: Node => A): List[A] =
    getKnownPeers.map(f)

}
