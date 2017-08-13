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

package com.aracon.scalachain

import scorex.crypto.hash.{ Blake2b256, CryptographicHash32 }

// A blockchain, as per the name, is a set of blocks that store data and keep some ordering. This case class represents one of such blocks.
final case class Block(index: Long,
                       previousHash: String,
                       timestamp: Long,
                       hash: String,
                       blockData: BlockData) {
//  val hash: String = FastCryptographicHash.calculateHash(index, previousHash, timestamp, blockData)

  override def toString: String = s"Block[$index][hash=$hash][prevHash=$previousHash]"
}

// Taken from Scorex: https://github.com/ScorexFoundation/Scorex/blob/master/src/main/scala/scorex/core/crypto/hash/FastCryptographicHash.scala
object FastCryptographicHash extends CryptographicHash32 {
  override def hash(input: Message): Digest = Blake2b256.hash(input)

  //TODO: split this method
  // TODO: Note there is no proof of work involved here so this won't work on distributed system. Just for demo purposes.
  def calculateHash(index: Long,
                    previousHash: String,
                    timestamp: Long,
                    blockData: BlockData): String = {
    val toEncrypt: Message = stringToByteArray(index.toString) ++
    stringToByteArray(previousHash) ++
    stringToByteArray(timestamp.toString) ++
    blockData.serialise

    new String(FastCryptographicHash(toEncrypt))
  }

  private def stringToByteArray(s: String): Array[Byte] = s.toCharArray.map(_.toByte)
}
