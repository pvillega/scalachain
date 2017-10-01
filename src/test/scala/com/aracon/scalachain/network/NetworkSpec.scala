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
import com.aracon.scalachain.block.EmptyBlockData
import org.scalacheck.Gen

class NetworkSpec extends SpecNetworkPackageHelper {
  "addNode" - {
    "adds the node to the network" in {
      forAll(nodeGen) { (node) =>
        val network = new Network()

        network.addNode(node)

        network.getNode(node.nodeId) should contain(node)
      }
    }
    "doesn't add the node again if it was already in the network" in {
      forAll(nodeGen) { (node) =>
        val network = new Network()

        network.addNode(node)
        network.addNode(node)
        network.addNode(node)
        network.addNode(node)

        network.getAllNodes should be(Set(network.originNode, node))
      }
    }
  }

  "removeNode" - {
    "removes the node from the network" in {
      forAll(nodeGen) { (node) =>
        val network = new Network()

        network.addNode(node)

        network.getNode(node.nodeId) should contain(node)

        network.removeNode(node)

        network.getNode(node.nodeId) should be(None)
      }
    }
    "if trying to remove a non-existing node" - {
      "operation does nothing raising no error" in {
        forAll(nodeGen) { (node) =>
          val network = new Network()

          network.removeNode(node)

          network.getNode(node.nodeId) should be(None)
        }
      }
      "operation doesn't alter existing node list" in {
        forAll(nodeGen) { (node) =>
          val network = new Network()

          network.removeNode(node)

          network.getAllNodes should be(Set(network.originNode))
        }
      }
    }
  }

  "resetNetwork" - {
    "for an empty Network it has no effect as it preserves origin node" in {
      val network = new Network()
      network.resetNetwork()
      network.getAllNodes should be(Set(network.originNode))
    }
    "should remove all nodes from the network, except the origin node" in {
      forAll(nodeGen, nodeGen, nodeGen) { (node1, node2, node3) =>
        val network = new Network()

        network.addNode(node1)
        network.addNode(node2)
        network.addNode(node3)

        network.resetNetwork()

        network.getAllNodes should be(Set(network.originNode))
      }
    }
  }

  "getNode" - {
    "return None if the network doesn't contain a node with the given uuid" in {
      forAll(Gen.uuid) { uuid =>
        val network = new Network()
        network.getNode(uuid) should be(None)
      }
    }
    "return the node that matches given uuid if it is part of the network" in {
      forAll(nodeGen) { node =>
        val network = new Network()
        network.addNode(node)

        network.getNode(node.nodeId) should contain(node)
      }
    }
  }

  "getAllNodes" - {
    "for an empty Network contains the origin node and only it" in {
      val network = new Network()
      network.getAllNodes should be(Set(network.originNode))
    }
    "returns all nodes in the network" in {
      forAll(nodeGen, nodeGen, nodeGen) { (node1, node2, node3) =>
        val network = new Network()

        network.addNode(node1)
        network.addNode(node2)
        network.addNode(node3)

        network.getAllNodes should be(Set(network.originNode, node1, node2, node3))
      }
    }
  }

  "syncNetwork" - {
    "without network sync" - {
      "a new block propagates to all peer nodes of the root node" in fixture {
        (rootNode, _, network) =>
          val nodePeers = rootNode.getKnownPeers

          rootNode.addBlockToNetwork(EmptyBlockData)
          rootNode.addBlockToNetwork(EmptyBlockData)
          rootNode.addBlockToNetwork(EmptyBlockData)

          nodePeers.foreach { node =>
            network.getNode(node.nodeId).get.getLocalBlockchainCopy should be(
              rootNode.getLocalBlockchainCopy
            )
          }
      }
      "a new block doesn't propagate to all non-peer nodes of a network due to lack of sync" in fixture {
        (rootNode, allNodes, _) =>
          val nodePeers = rootNode.getKnownPeers
          val notPeers  = allNodes.filterNot(node => nodePeers.contains(node))

          rootNode.addBlockToNetwork(EmptyBlockData)
          rootNode.addBlockToNetwork(EmptyBlockData)
          rootNode.addBlockToNetwork(EmptyBlockData)

          notPeers.foreach { node =>
            node.getLocalBlockchainCopy should not be rootNode.getLocalBlockchainCopy
          }
      }
    }

    "with network sync" - {
      "a new block propagates to all peer nodes of the root node" in fixture {
        (rootNode, _, network) =>
          val nodePeers = rootNode.getKnownPeers

          rootNode.addBlockToNetwork(EmptyBlockData)
          rootNode.addBlockToNetwork(EmptyBlockData)
          rootNode.addBlockToNetwork(EmptyBlockData)

          network.syncNetworkOnce()

          nodePeers.foreach { node =>
            network.getNode(node.nodeId).get.getLocalBlockchainCopy should be(
              rootNode.getLocalBlockchainCopy
            )
          }
      }
      "a new block propagates to all non-peer nodes of a network after enough syncs have happened" in fixture {
        (rootNode, allNodes, network) =>
          val nodePeers = rootNode.getKnownPeers
          val notPeers  = allNodes.filterNot(node => nodePeers.contains(node))

          rootNode.addBlockToNetwork(EmptyBlockData)
          rootNode.addBlockToNetwork(EmptyBlockData)
          rootNode.addBlockToNetwork(EmptyBlockData)

          // given the structure of the tree, all nodes should get the data in at most 2 sync steps.
          // root sends to peer 1 and 2
          // first sync will update at least leaves of peer 1 and 2, potentially other leaves
          // second sync will update for sure leaves of leaves
          network.syncNetworkOnce()
          network.syncNetworkOnce()

          notPeers.foreach { node =>
            node.getLocalBlockchainCopy should be(rootNode.getLocalBlockchainCopy)
          }

      }
    }
  }

  private def fixture(f: (Node, List[Node], Network) => Unit): Unit = {
    // build a network that is tree like (leaves don't see each other) and make sure all nodes get new block in the end
    val network = new Network()

    val node = new Node(UUID.randomUUID())
    node.blockchain += emptyBlock

    val peer1    = createNewPeerForNode(node, network)
    val leave1_1 = createNewPeerForNode(peer1, network)
    val leave1_2 = createNewPeerForNode(peer1, network)

    val peer2      = createNewPeerForNode(node, network)
    val leave2_1   = createNewPeerForNode(peer2, network)
    val leave2_2   = createNewPeerForNode(peer2, network)
    val leave2_1_1 = createNewPeerForNode(leave2_1, network)
    val leave2_1_2 = createNewPeerForNode(leave2_1, network)

    val allNodes =
      List(peer1, leave1_1, leave1_2, peer2, leave2_1, leave2_2, leave2_1_1, leave2_1_2)

    f(node, allNodes, network)
  }
}
