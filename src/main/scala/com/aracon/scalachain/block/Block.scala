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

package com.aracon.scalachain.block

// A blockchain, as per the name, is a set of blocks that store data and keep some ordering. This case class represents one of such blocks.
// In a real blockchain the blocks may contain many pieces of data and use https://en.wikipedia.org/wiki/Merkle_tree to avoid tampering.
// Here we keep it simple.
final case class Block(index: Long,
                       previousHash: String,
                       timestamp: Long,
                       hash: String,
                       blockData: BlockData) {
  override def toString: String = s"Block[$index][hash=$hash][prevHash=$previousHash]"
}
