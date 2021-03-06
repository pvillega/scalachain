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

package com.aracon.scalachain.error

sealed trait BlockchainError

sealed trait NetworkError extends BlockchainError

case object NoBootstrapPeers extends NetworkError

case object InvalidLocalChainError extends BlockchainError

sealed trait BlockError extends BlockchainError

case object WrongIndex        extends BlockError
case object WrongPreviousHash extends BlockError
case object WrongBlockHash    extends BlockError

sealed trait MessageError extends BlockchainError

case object NoNodesAvailable                                extends MessageError
case object UnknownMessage                                  extends MessageError
case object InvalidContractReference                        extends MessageError
case object ContractReferenceAlreadyExists                  extends MessageError
case class ContractRejectedMessage(err: SmartContractError) extends MessageError

sealed trait SmartContractError extends BlockchainError

sealed trait ScalaCoinError extends SmartContractError

case object InvalidAmountError          extends ScalaCoinError
case object InsuficientFundsSenderError extends ScalaCoinError
case object OverflowError               extends ScalaCoinError
