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

import cats.data.{ NonEmptyList, State, Validated, ValidatedNel }
import cats.implicits._
import com.aracon.scalachain.FastCryptographicHash.Message
import scorex.crypto.hash.{ Blake2b256, CryptographicHash32 }

trait ScalaChain[A] {
  // the blockchain is an array of blocks of which there is a single copy in the system. As we are ignoring distributed systems
  // we use State monad to store the blockchain
  type BlockChain = State[Array[Block], A]

  // default initial state in the chain, for example Array(Block(0L, "0", 0L, EmptyBlockData))
  def initialBlock: Array[Block]
  def initialChain: BlockChain

  // adds a new piece of data to the chain, creating a new block to store the data
  def addDataInNewBlock(currentChain: BlockChain, blockData: BlockData): BlockChain =
    currentChain.modify { chain =>
      val newBlock =
        Block(chain.last.index + 1, chain.last.hash, System.currentTimeMillis(), "", blockData)
      chain :+ newBlock
    }

  // validates the integrity of the blockchain, ensuring all blocks are valid and in sequence
  def validateBlockChain(chain: Array[Block]): ValidatedNel[String, Unit] =
    chain
      .zip(chain.tail)
      .map { case (prev, next) => verifyCurrentBlock(prev, next) }
      .reduce((a, b) => a.combine(b))

  private def verifyCurrentBlock(previousBlock: Block,
                                 currentBlock: Block): ValidatedNel[String, Unit] = {
    val v = ().validNel[String]

    val indexValidation = v.ensure(
      NonEmptyList.of("Index of current block is not expected index")
    )(_ => previousBlock.index + 1 === currentBlock.index)
    val previousHashValidation = v.ensure(
      NonEmptyList
        .of("Current block's reference to 'previous hash' doesn't match previous block hash")
    )(_ => previousBlock.hash === currentBlock.previousHash)
    val hashValidation = v.ensure(
      NonEmptyList
        .of("Current block's hash doens't match calculated hash for the block")
    )(
      _ =>
        FastCryptographicHash.calculateHash(currentBlock.index,
                                            currentBlock.previousHash,
                                            currentBlock.timestamp,
                                            currentBlock.blockData) === currentBlock.hash
    )

    indexValidation |+| previousHashValidation |+| hashValidation
  }
}