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

// Contains global objects that represent the bootstrap node of the node network as well as all nodes in the network
object Network {

  val originNodeId: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
  val originNode: Node   = new Node(originNodeId)

  val network: mutable.HashMap[UUID, Node] = mutable.HashMap(originNode.nodeId -> originNode)
}
