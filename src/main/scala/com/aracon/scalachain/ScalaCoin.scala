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

import java.util.UUID

import cats.data.State
import cats.implicits._

import scala.collection.mutable.ListBuffer

// an output is a set of money with a unique id that belongs to someone.
final case class Output(id: UUID, owner: Long, amount: BigDecimal)

// A transaction converts an Output (used as Input) to one or more Outputs, usually with different owners.
// The effective result is moving money around (changing ownership of money)
// The input of a transaction must match an existing unspent output id, to avoid double-spending of money.
final case class Transaction(inputId: UUID, outputs: List[Output]) extends BlockData

// Implement a rudimentary coin on top of ScalaChain. We store as value in the state a list of unspent outputs,
// global to the blockchain, which are equivalent to the balance of all the users.
object ScalaCoin extends ScalaChain[ListBuffer[Output]] {

  // Initial state of the world, all the coins in existence. We won't mint new ones in this model!
  private val originTransactionUUID = UUID.randomUUID()
  private val originOutputUUID      = UUID.randomUUID()
  private val originOutput          = Output(originOutputUUID, 0, 100)
  private val originTransaction     = Transaction(originTransactionUUID, originOutput :: Nil)
  val initialBlock: Array[Block]    = Array(Block(0L, "0", 0L, originTransaction))
  val initialChain: BlockChain      = State(_ => (initialBlock, ListBuffer(originOutput)))

  // Note: to keep it simple we don't do validation, an expensive mistake ;) This code is most likely not correct, just an example!
  def sendMoney(chain: BlockChain,
                amountToTransfer: BigDecimal,
                fromUser: Long,
                toUser: Long): BlockChain =
    // find which output to use for the transaction, we assume only a single output needed in all cases - real life would be more complex
    chain.flatMap { outputs =>
      outputs
        .find(_.owner === fromUser)
        .fold(chain) { userOutputToUse: Output =>
          val transferOutputs: List[Output] =
            if (amountToTransfer >= userOutputToUse.amount)
              userOutputToUse.copy(owner = toUser) :: Nil
            else
              Output(UUID.randomUUID(), toUser, amountToTransfer) :: Output(
                UUID.randomUUID(),
                fromUser,
                userOutputToUse.amount - amountToTransfer
              ) :: Nil

          val transaction      = Transaction(userOutputToUse.id, transferOutputs)
          val transactionAdded = addDataInNewBlock(chain, transaction)

          // we also update the list of balances with the new unused blocks
          transactionAdded.map(_ => (outputs - userOutputToUse) ++ transferOutputs)
        }
    }

  // the balance is stored in the A of the State in the blockchain, which contains unspent outputs
  def getBalance(chain: BlockChain, userId: Long): BigDecimal =
    chain.runA(initialBlock).value.filter(_.owner === userId).map(_.amount).sum


  val logger = org.log4s.getLogger

  def main(args: Array[String]): Unit = {

    println("We have a chain with some money")
    println(s"User 0> ${getBalance(initialChain, 0L)} utd")

    println("Let's send Money to user 1")
    val newState = sendMoney(initialChain, 5, 0, 1)
    println(s"New balances")
    println(s"User 0> ${getBalance(newState, 0)} utd")
    println(s"User 1> ${getBalance(newState, 1)} utd")

    println("Let's send more money around")
    val a         = sendMoney(newState, 25, 0, 2)
    val b         = sendMoney(a, 2, 2, 1)
    val newState2 = sendMoney(b, 1, 1, 0)
    println(s"New balances")
    println(s"User 0> ${getBalance(newState2, 0)} utd")
    println(s"User 1> ${getBalance(newState2, 1)} utd")
    println(s"User 2> ${getBalance(newState2, 2)} utd")

    println(s"And let's check the chain")
    val chain = newState2
      .runS(initialBlock)
      .value
      .map(block => (block.index, block.timestamp, block.blockData.toString))
      .mkString("\n")
    println(s">> All blocks:\n$chain")
  }

}
