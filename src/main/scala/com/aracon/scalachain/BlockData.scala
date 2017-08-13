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

import java.io.{ ByteArrayOutputStream, ObjectOutputStream }

import com.aracon.scalachain.FastCryptographicHash.Message

// trait that all data stored in blocks within ScalaChain must extend
trait BlockData {
  def serialise: Message = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val oos                           = new ObjectOutputStream(stream)
    oos.writeObject(this)
    oos.close()
    stream.toByteArray
  }
}
case object EmptyBlockData extends BlockData
