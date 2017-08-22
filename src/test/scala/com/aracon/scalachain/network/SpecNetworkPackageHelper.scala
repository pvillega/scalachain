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

import com.aracon.scalachain.block.{ Block, EmptyBlockData }
import org.scalacheck.Gen
import org.scalatest.{ FreeSpec, Matchers }
import org.scalatest.prop.GeneratorDrivenPropertyChecks

trait SpecNetworkPackageHelper extends FreeSpec with Matchers with GeneratorDrivenPropertyChecks {
  val emptyBlock: Block = Block(0L, "", 0L, "", EmptyBlockData)

  def createNewPeerForNode(node: Node, network: Network, peerBlockchain: List[Block] = Nil): Node = {
    val peerId = UUID.randomUUID()
    val peer   = new Node(peerId)
    peer.blockchain ++= peerBlockchain

    peer.appendPeer(node)
    node.appendPeer(peer)

    network.addNode(node)
    network.addNode(peer)

    peer
  }

  val blockGen: Gen[Block] = for {
    index     <- Gen.posNum[Long]
    prevHash  <- Gen.alphaNumStr
    hash      <- Gen.alphaNumStr
    timestamp <- Gen.posNum[Long]
  } yield Block(index, prevHash, timestamp, hash, EmptyBlockData)

  val chainGen: Gen[List[Block]] = Gen.nonEmptyListOf(blockGen).map(_.sortBy(_.index))

  val nodeGen: Gen[Node] = Gen.uuid.map(new Node(_))
}
