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

package com.aracon.scalachain.crypto

import com.aracon.scalachain.block.BlockData
import scorex.crypto.hash.{ Blake2b256, CryptographicHash32 }

// Taken from Scorex: https://github.com/ScorexFoundation/Scorex/blob/master/src/main/scala/scorex/core/crypto/hash/FastCryptographicHash.scala
object FastCryptographicHash extends CryptographicHash32 {

  override def hash(input: Message): Digest = Blake2b256.hash(input)

  def calculateBlockHash(index: Long,
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
