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

import com.aracon.scalachain.contracts.ScalaCoinContract
import com.aracon.scalachain.network.{ Network, Node, Wallet }
import org.scalatest.Assertion

import scala.util.Random

class Examples extends SpecNetworkPackageHelper {

  "with a single node" - {
    "Two wallets can transfer money to each other" in singleNodeFixture(2) { fixture =>
      testTransfersBetween2Wallets(fixture)
    }

    "Four wallets can transfer money to each other" in singleNodeFixture(4) { fixture =>
      testTransfersBetween4Nodes(fixture)
    }
  }

  "with multiple nodes" - {
    "Two wallets can transfer money to each other" in multipleNodeFixture(2) { fixture =>
      testTransfersBetween2Wallets(fixture)
    }

    "Four wallets can transfer money to each other" in multipleNodeFixture(4) { fixture =>
      testTransfersBetween4Nodes(fixture)
    }
  }

  private case class Fixture(wallets: List[Wallet], network: Network)

  private def testTransfersBetween2Wallets(fixture: Fixture) = {
    import fixture._

    val wallet1 = wallets.head
    val wallet2 = wallets(1)

    val scalaCoinContract = new ScalaCoinContract(UUID.randomUUID(), wallet1.walletId, 1000)
    wallet1.createContract(scalaCoinContract) should be(Right(()))

    wallet1.transferMoney(scalaCoinContract.contractUniqueId, wallet2.walletId, 100) should be(
      Right(())
    )
    wallet2.transferMoney(scalaCoinContract.contractUniqueId, wallet1.walletId, 20) should be(
      Right(())
    )

    wallet1.getBalanceForContract(scalaCoinContract.contractUniqueId) should be(920)
    wallet2.getBalanceForContract(scalaCoinContract.contractUniqueId) should be(80)
  }

  private def testTransfersBetween4Nodes(fixture: Fixture) = {
    import fixture._

    val wallet1 = wallets.head
    val wallet2 = wallets(1)
    val wallet3 = wallets(2)
    val wallet4 = wallets(3)

    val scalaCoinContract = new ScalaCoinContract(UUID.randomUUID(), wallet1.walletId, 1000)
    wallet1.createContract(scalaCoinContract) should be(Right(()))

    wallet1.transferMoney(scalaCoinContract.contractUniqueId, wallet2.walletId, 100) should be(
      Right(())
    )
    wallet1.transferMoney(scalaCoinContract.contractUniqueId, wallet3.walletId, 100) should be(
      Right(())
    )
    wallet1.transferMoney(scalaCoinContract.contractUniqueId, wallet4.walletId, 100) should be(
      Right(())
    )
    wallet2.transferMoney(scalaCoinContract.contractUniqueId, wallet1.walletId, 20) should be(
      Right(())
    )
    wallet2.transferMoney(scalaCoinContract.contractUniqueId, wallet3.walletId, 20) should be(
      Right(())
    )
    wallet2.transferMoney(scalaCoinContract.contractUniqueId, wallet4.walletId, 20) should be(
      Right(())
    )
    wallet3.transferMoney(scalaCoinContract.contractUniqueId, wallet1.walletId, 10) should be(
      Right(())
    )
    wallet3.transferMoney(scalaCoinContract.contractUniqueId, wallet2.walletId, 10) should be(
      Right(())
    )
    wallet3.transferMoney(scalaCoinContract.contractUniqueId, wallet3.walletId, 10) should be(
      Right(())
    )
    wallet3.transferMoney(scalaCoinContract.contractUniqueId, wallet4.walletId, 10) should be(
      Right(())
    )
    wallet4.requestLatestChain()

    wallet1.getBalanceForContract(scalaCoinContract.contractUniqueId) should be(730)
    wallet2.getBalanceForContract(scalaCoinContract.contractUniqueId) should be(50)
    wallet3.getBalanceForContract(scalaCoinContract.contractUniqueId) should be(90)
    wallet4.getBalanceForContract(scalaCoinContract.contractUniqueId) should be(130)
  }

  private def singleNodeFixture(nWallets: Int)(f: Fixture => Assertion): Assertion = {
    val network = new Network()

    f(Fixture(createWallets(nWallets, network), network))
  }

  private def multipleNodeFixture(nWallets: Int)(f: Fixture => Assertion): Assertion = {
    val network = new Network()
    (1 to Random.nextInt(100)).foreach { _ =>
      val node  = new Node(UUID.randomUUID())
      val peers = Random.shuffle(network.getAllNodes).take(5).map(_.nodeId).toList
      node.connectToNetwork(peers, network)
    }

    f(Fixture(createWallets(nWallets, network), network))
  }

  private def createWallets(n: Int, network: Network): List[Wallet] = {
    val wallets = (0 to n)
      .map(_ => new Wallet(UUID.randomUUID(), network))
      .toList

    wallets.foreach(_.requestLatestChain())
    wallets
  }
}
