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

import cats.data.Validated.{ Invalid, Valid }
import com.aracon.scalachain.error.{
  NoBootstrapPeers,
  WrongBlockHash,
  WrongIndex,
  WrongPreviousHash
}
import com.aracon.scalachain.{ Block, EmptyBlockData, FastCryptographicHash }
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{ FreeSpec, Matchers }

class NodeSpec extends FreeSpec with Matchers with GeneratorDrivenPropertyChecks {

  "appendPeer" - {
    "appends a new peer to the list of peers" in {
      val node      = new Node(UUID.randomUUID())
      val newPeerId = UUID.randomUUID()

      node.appendPeer(newPeerId)

      node.getKnownPeers.lastOption should contain(newPeerId)

    }
    "if the peerId received was already known, we do nothing" in {
      val node      = new Node(UUID.randomUUID())
      val newPeerId = UUID.randomUUID()

      node.appendPeer(newPeerId)
      node.appendPeer(newPeerId)
      node.appendPeer(newPeerId)

      node.getKnownPeers.size should be(1)
    }
  }

  "connectToNetwork" - {
    "if no bootstrap nodes are provided" - {
      val bootstrapPeers: List[UUID] = Nil
      "method returns an error state" in {
        val node = new Node(UUID.randomUUID())
        node.connectToNetwork(bootstrapPeers) should be(Left(NoBootstrapPeers))
      }
      "local copy of the blockchain is empty" in {
        val node = new Node(UUID.randomUUID())
        node.connectToNetwork(bootstrapPeers)

        node.getLocalBlockchainCopy should be(Nil)
      }
      "the list of known peers is empty" in {
        val node = new Node(UUID.randomUUID())
        node.connectToNetwork(bootstrapPeers)

        node.getKnownPeers should be(Nil)
      }
      "node is not part of the network" in {
        val node = new Node(UUID.randomUUID())
        node.connectToNetwork(bootstrapPeers)

        Network.network.get(node.nodeId) should be(None)
      }
    }
    "once completed" - {
      def fixture(f: (List[UUID], List[Block]) => Unit): Unit = {
        forAll(chainGen, chainGen, chainGen) { (chain1, chain2, chain3) =>
          Network.network.clear()

          createNewPeerForNode(new Node(UUID.randomUUID()), chain1)
          createNewPeerForNode(new Node(UUID.randomUUID()), chain2)
          createNewPeerForNode(new Node(UUID.randomUUID()), chain3)

          val bootstrapPeers: List[UUID] = Network.network.keySet.toList
          val bootstrapChain =
            Network.network.values.map(_.getLocalBlockchainCopy).maxBy(_.last.index)

          f(bootstrapPeers, bootstrapChain)
        }

        Network.network.clear()
      }

      "method returns success" in fixture { (bootstrapPeers, _) =>
        val node = new Node(UUID.randomUUID())
        node.connectToNetwork(bootstrapPeers) should be(Right(()))
      }
      "the node has a local copy of the network blockchain" in fixture {
        (bootstrapPeers, bootstrapChain) =>
          val node = new Node(UUID.randomUUID())
          node.connectToNetwork(bootstrapPeers)

          node.getLocalBlockchainCopy should be(bootstrapChain)
      }
      "the node stores bootstrap list of nodes" in fixture { (bootstrapPeers, _) =>
        val node = new Node(UUID.randomUUID())
        node.connectToNetwork(bootstrapPeers)

        node.getKnownPeers should be(bootstrapPeers)
      }
      "node becomes part of the network" in fixture { (bootstrapPeers, _) =>
        val node = new Node(UUID.randomUUID())
        node.connectToNetwork(bootstrapPeers)

        Network.network.get(node.nodeId) should contain(node)
      }
    }
  }

  "getLatestBlock" - {
    "returns None if we don't have a local copy of the blockchain" in {
      val node = new Node(UUID.randomUUID())
      node.getLatestBlock should be(None)
    }
    "returns the latest block of the local copy of the blockchain" in {
      val node     = new Node(UUID.randomUUID())
      val expected = emptyBlock
      node.blockchain += expected

      node.getLatestBlock should contain(expected)
    }
  }

  "synchronizeWithNetwork" - {
    "does nothing if the node receive no nodes from peers" - {
      "due to no peers" in {
        forAll(chainGen, chainGen) { (chain1, chain2) =>
          val node         = new Node(UUID.randomUUID())
          val largestBlock = (chain1 ::: chain2).maxBy(_.index)
          node.blockchain += largestBlock

          node.synchronizeWithNetwork()

          node.getLocalBlockchainCopy should be(List(largestBlock))
        }
      }
      "due to peers with no blocks" in {
        forAll(chainGen, chainGen) { (chain1, chain2) =>
          val node         = new Node(UUID.randomUUID())
          val largestBlock = (chain1 ::: chain2).maxBy(_.index)
          node.blockchain += largestBlock

          createNewPeerForNode(node)
          createNewPeerForNode(node)

          node.synchronizeWithNetwork()

          node.getLocalBlockchainCopy should be(List(largestBlock))
        }
      }
    }
    "if the node already has the latest block know by the network, it stops here" in {
      forAll(chainGen, chainGen) { (chain1, chain2) =>
        val node         = new Node(UUID.randomUUID())
        val largestBlock = (chain1 ::: chain2).maxBy(_.index)
        node.blockchain += largestBlock

        createNewPeerForNode(node, chain1)
        createNewPeerForNode(node, chain2)

        node.synchronizeWithNetwork()

        node.getLocalBlockchainCopy should be(List(largestBlock))
      }
    }
    "if a latest block received has an index larger than the current latest block in the local chain, requests a copy of the blockchain from the network and updates the local copy" in {
      forAll(chainGen, chainGen) { (chain1, chain2) =>
        val node         = new Node(UUID.randomUUID())
        val largestBlock = (chain1 ::: chain2).maxBy(_.index)
        node.blockchain += Block(largestBlock.index - 1, "", 0L, "", EmptyBlockData)

        createNewPeerForNode(node, chain1)
        createNewPeerForNode(node, chain2)

        node.synchronizeWithNetwork()

        val expected =
          if (chain1.lastOption.fold(-1L)(_.index) >= chain2.lastOption.fold(-1L)(_.index)) chain1
          else chain2
        node.getLocalBlockchainCopy should be(expected)
      }
    }
  }

  "queryLatestBlockFromPeers" - {
    "returns Nil if there are no known peers" in {
      val node = new Node(UUID.randomUUID())
      node.queryLatestBlockFromPeers should be(Nil)
    }
    "retrieves the latest block of all known peers" in {
      forAll(chainGen, chainGen) { (chain1, chain2) =>
        val node = new Node(UUID.randomUUID())

        createNewPeerForNode(node, chain1)
        createNewPeerForNode(node, chain2)

        val expected = List(chain1.last, chain2.last)
        node.queryLatestBlockFromPeers should be(expected)
      }
    }
  }

  "updateBlockchainFromNetwork" - {
    "stores the longest chain from the network, based on node index" in {
      forAll(chainGen, chainGen) { (chain1, chain2) =>
        val node = new Node(UUID.randomUUID())

        createNewPeerForNode(node, chain1)
        createNewPeerForNode(node, chain2)

        node.updateBlockchainFromNetwork()

        val expected =
          if (chain1.lastOption.fold(-1L)(_.index) >= chain2.lastOption.fold(-1L)(_.index)) chain1
          else chain2
        node.getLocalBlockchainCopy should be(expected)
      }
    }
    "doesn't replace the local chain if it is longer than the ones received" in {
      forAll(chainGen, chainGen) { (chain1, chain2) =>
        val biggestChain =
          if (chain1.lastOption.fold(-1L)(_.index) >= chain2.lastOption.fold(-1L)(_.index)) chain1
          else chain2

        val localChain = biggestChain :+ Block(biggestChain.last.index + 1,
                                               "",
                                               0L,
                                               "",
                                               EmptyBlockData)
        val node = new Node(UUID.randomUUID())
        localChain.foreach(block => node.blockchain += block)

        createNewPeerForNode(node, chain1)
        createNewPeerForNode(node, chain2)

        node.updateBlockchainFromNetwork()

        node.getLocalBlockchainCopy should be(localChain)
      }
    }
  }

  "generateBlock" - {
    "appends the new block to the local chain" in {
      fail()
    }
    "sends the new block to all the known peers" in {
      fail()
    }
    "causes peers to synchronize with the network after receiving the block if it is not the next in sequence they expected" in {
      fail()
    }
    "if two nodes generate the same block index, ????" in {
      //TODO: proof of work/stake here??
      fail()
    }
    "a block generated by a peer ends up propagating across all the network even when some peers only see part of the network" in {
      // build a network that is tree like (leaves don't see each other) and make sure all nodes get new block in the end
      fail()
    }
  }

  "validateReceivedBlock" - {
    "will fail validation" - {
      "if the index is not the index of the latest block known by the node + 1" in {
        val node      = new Node(UUID.randomUUID())
        val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)

        forAll(Gen.chooseNum(3L, Long.MaxValue)) { wrongIndex =>
          val hash =
            FastCryptographicHash.calculateHash(wrongIndex, lastBlock.hash, 0L, EmptyBlockData)
          val faultyIndexBlock = Block(wrongIndex, lastBlock.hash, 0L, hash, EmptyBlockData)
          node.validateReceivedBlock(lastBlock, faultyIndexBlock) should be(Invalid(WrongIndex))
        }
      }
      "if the previous hash of the new block doesn't match the hash of the previous block in sequence" in {
        val node = new Node(UUID.randomUUID())

        forAll(Gen.alphaNumStr) { wrongHash =>
          val lastBlock = Block(1L, "", 0L, wrongHash + "1", EmptyBlockData)

          val hash                    = FastCryptographicHash.calculateHash(2L, wrongHash, 0L, EmptyBlockData)
          val faultyPreviousHashBlock = Block(2L, wrongHash, 0L, hash, EmptyBlockData)
          node.validateReceivedBlock(lastBlock, faultyPreviousHashBlock) should be(
            Invalid(WrongPreviousHash)
          )
        }
      }
      "if the hash of the block received doesn't match the hash calculated for the block by the current node" in {
        val node      = new Node(UUID.randomUUID())
        val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)

        forAll(Gen.alphaNumStr) { wrongHash =>
          val faultyHashBlock = Block(2L, lastBlock.hash, 0L, wrongHash, EmptyBlockData)
          node.validateReceivedBlock(lastBlock, faultyHashBlock) should be(Invalid(WrongBlockHash))
        }
      }
    }
    "will pass validation if the block is a valid one" in {
      val node      = new Node(UUID.randomUUID())
      val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)

      val hash       = FastCryptographicHash.calculateHash(2L, lastBlock.hash, 0L, EmptyBlockData)
      val validBlock = Block(2L, lastBlock.hash, 0L, hash, EmptyBlockData)

      node.validateReceivedBlock(lastBlock, validBlock) should be(Valid(()))
    }
  }

  "appendBlock" - {
    "if the new block fails validation" - {
      "the new block is not added to the local chain" in {
        val node      = new Node(UUID.randomUUID())
        val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)
        node.blockchain += lastBlock

        val faultyHashBlock =
          Block(2L, lastBlock.hash, 0L, UUID.randomUUID().toString, EmptyBlockData)

        node.appendBlock(faultyHashBlock)

        node.getLatestBlock should contain(lastBlock)
      }
      "triggers a synchronize network to ensure we have a current copy of the block chain as we may be out of date" in {
        forAll(chainGen) { (chain1) =>
          val node      = new Node(UUID.randomUUID())
          val lastBlock = Block(0L, "", 0L, "", EmptyBlockData)
          node.blockchain += lastBlock

          createNewPeerForNode(node, chain1)

          val faultyBlock =
            Block(20L, UUID.randomUUID().toString, 0L, UUID.randomUUID().toString, EmptyBlockData)

          node.appendBlock(faultyBlock)

          node.getLocalBlockchainCopy should be(chain1)
        }
      }
    }
    "if the new block passes validation" - {
      "append block to the end of current local copy of chain" in {
        val node      = new Node(UUID.randomUUID())
        val lastBlock = Block(1L, "", 0L, "", EmptyBlockData)
        node.blockchain += lastBlock

        val hash       = FastCryptographicHash.calculateHash(2L, lastBlock.hash, 0L, EmptyBlockData)
        val validBlock = Block(2L, lastBlock.hash, 0L, hash, EmptyBlockData)

        node.appendBlock(validBlock)

        node.getLatestBlock should contain(validBlock)
      }
    }
  }

  private val emptyBlock: Block = Block(0L, "", 0L, "", EmptyBlockData)

  private def createNewPeerForNode(node: Node, peerBlockchain: List[Block] = Nil): Node = {
    val peerId = UUID.randomUUID()
    val peer   = new Node(peerId)
    peerBlockchain.foreach(block => peer.blockchain += block)

    Network.network += (peerId -> peer)

    node.appendPeer(peerId)
    peer
  }

  private val blockGen: Gen[Block] = for {
    index     <- Gen.posNum[Long]
    prevHash  <- Gen.alphaNumStr
    hash      <- Gen.alphaNumStr
    timestamp <- Gen.posNum[Long]
  } yield Block(index, prevHash, timestamp, hash, EmptyBlockData)

  private val chainGen: Gen[List[Block]] = Gen.nonEmptyListOf(blockGen).map(_.sortBy(_.index))
}
