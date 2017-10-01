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

import scala.collection.mutable
import java.util.concurrent._

import com.aracon.scalachain.block.{ Block, EmptyBlockData }
import com.aracon.scalachain.crypto.FastCryptographicHash

// Contains objects that represent the bootstrap node of the node network as well as all nodes in the network
class Network() {

  private[this] val logger = org.log4s.getLogger

  override def toString: String = s"Network-${getAllNodes.map(_.toString).mkString("|")}"

  val originNodeId: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
  val originNode: Node = {
    val node = new Node(originNodeId)

    val hashOriginBlock = FastCryptographicHash.calculateBlockHash(1L, "", 0L, EmptyBlockData)
    val originBlock     = Block(1L, "", 0L, hashOriginBlock, EmptyBlockData)
    node.blockchain += originBlock

    node
  }

  private val network: mutable.HashMap[UUID, Node] =
    mutable.HashMap(originNode.nodeId -> originNode)

  def resetNetwork(): Unit = {
    network.clear()
    network += originNode.nodeId -> originNode
  }

  def addNode(node: Node): Unit =
    network.get(node.nodeId) match {
      case None => network += (node.nodeId -> node)
      case _    => ()
    }

  def removeNode(node: Node): Unit =
    network.remove(node.nodeId)

  def getNode(nodeId: UUID): Option[Node] = network.get(nodeId)

  def getAllNodes: Set[Node] = network.values.toSet

  // Executor to run network automatic sync of nodes, to simulate self-updating network
  private val ex = new ScheduledThreadPoolExecutor(1)

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      ex.shutdown()
      if (!ex.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.error("Network Sync executor did not terminate in the specified time (5 seconds).")
        val droppedTasks = ex.shutdownNow
        logger.error(
          s"Executor was abruptly shut down. ${droppedTasks.size} tasks will not be executed."
        )
      } else {
        logger.info(s"Network Sync executor terminated")
      }
    }
  })

  // Please note once started this will keep running until the jvm exits. Use 'syncNetworkOnce' for tests.
  def startNetworkAutoSync(delay: Long = 1,
                           period: Long = 1,
                           timeUnit: TimeUnit = TimeUnit.MILLISECONDS): Unit = {
    val task = new Runnable {
      def run(): Unit = syncNetworkOnce()
    }
    val ignore = ex.scheduleAtFixedRate(task, delay, period, timeUnit)
  }

  def syncNetworkOnce(): Unit =
    network.values.foreach(_.synchronizeWithNetwork())

}
