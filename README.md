# scalaChain #

Welcome to scalaChain!

This is a sample project to understand the basics of Blockchain. It simplifies some concepts so we can focus on the core components of the chain. Please note this is not a good example of programming, side-effects and mutability are everywhere :D  


## What is a blockchain

A blockchain is just a ledger. That's all. 

Technically, it is a distributed database that contains a growing list of records, called blocks. Thus, chain of blocks, or blockchain. 
These blocks use cryptographic techniques to ensure they are tamper-proof, which means that once a piece of data is added to the chain, that 
can't be modified without most of the network agreeing to the change.

Being a distributed database, it needs to tackle issues like network partitions and provide [Byzantine fault tolerance](https://en.wikipedia.org/wiki/Byzantine_fault_tolerance),
among others.  

## Components

This implementation has the following major components

### Network

A simplified representation of the blockchain network, it provides a bootstrap node and it allows us to find all nodes that belong to the network easily, It also
provides functionality to synchronise the network, triggering synchronization events from all the nodes in the network.

In a real implementation the network would be a peer-to-peer distributed network in which nodes would only know a subset of the network, their peers, along some *bootstrap* nodes
used to join the network.

### Node

Represents a node in the network. This node has certain capabilities:

* stores a local copy of the blockchain
* can add new blocks to the chain
* has a list of peers it can communicate with directly

All nodes belong to the network and provide the *computing backbone* of the system. Think of it as a distributed database that can store data in the form of a blockchain.
 
Remember that, as a simplified example, several of the operations the nodes execute are not fully equivalent to what a complete implementation would do. 
We only approximate them so a simplified step allows us to showcase the core idea. 

### Block

A block is an entry in the chain. In this implementation this has been simplified so a block contains a single piece of data (`BlockData`) associated to it, while
in 'real-life' implementation a block could contain several transactions, for example using [Merkle Trees](https://en.wikipedia.org/wiki/Merkle_tree) to store them. 

### Blockchain

A data structure comprised of a sequence of blocks. Each node will have its own copy, for integrity reasons. Think of it as a ledger in which each block describes
and entry of the ledger, and which is made *tamper proof* by having multiple copies (one per node) plus some cryptographic support.

### Wallet

A wallet is a type of account that can connect to the network. If we said the nodes in the network could be thought as a distributed database, a wallet would be a client connecting to that database to execute operations on it. It's not part of the node network, but it interacts with it. 

A wallet has a unique identifier and it uses a strong cryptographic key (a private key) for identification, but it is not stored in the chain itself. What it is stored in the chain is a reference to an operation that involved the wallet, for example in a bitcoin transaction you could see an *output* that says a certain amount of cash belongs to a certain wallet. That is the reason why all wallets warn you to not lose the private key you created, as if you do you will lose access to any resource associated with it. The chain has no other information.

A wallet also contains a local copy of the blockchain, for operational purposes. For example, to calculate your balance by following all your transactions stored in the chain. In effect this means the wallet *reads* every new block received and will update itself accordingly, for example updating the balance it displays. Our implementation simplifies this process by requesting a full copy of the blockchain from the network, when prompted, and providing some helper methods to store contracts locally with an up-to-date state.

A wallet interacts with the network by sending messages which can have effects like creating a new contract or triggering actions within a contract. The message is sent to a node, and it is the node the responsible of executing the action associated to the message. In networks like Ethereum this can trigger complex behaviour like running a contract within their virtual machine, in our implementation things are simplified for educational purposes.

### Contract

A smart contract is an executable program stored in the blockchain, along some values it may contain, and identified by a unique address. In this simplified version we have hardcoded many of the smart
contract parts to facilitate understanding. 

The fact they are stored in the chain means they are not modifiable, as the chain is immutable. At most you can create a new version of a contract, but the old one will stay
available, although may become unused. 

Contracts can receive `Messages` which will trigger actions. Messages are sent to a node, which will act accordingly. A `Create` message will add a new contract to the network,
while a contract specific message will have some other effect, as per the contract. Messages (including parameters) are stored in the chain. 


### Proof of Work

NOTE: please note that this project is not multi-threaded. All events happen linearly for ease of understanding, which means a proof-of-work implementation is not needed. This section is added just for educational purposes on how would that be implemented. For real implementations like Ethash check their [documentation](https://github.com/ethereum/wiki/wiki/Ethash). For a tutorial implementation check [Ethereum token tutorial](https://www.ethereum.org/token#proof-of-work)

[Proof of work](https://en.wikipedia.org/wiki/Proof-of-work_system) is a popular subject on cryptocurrencies (like Bitcoin) and all blockchain implementations. Let's demystify it.

What is a proof of work? It basically is hard (as in [computer hard](https://en.wikipedia.org/wiki/NP_(complexity))) problem nodes try to solve. Usual implementations are guessing the number that produces the challenge hash. The node tries random numbers until it finds a match. Then that node generates the next challenge. For this type of problems the solution is very easy to verify, so all nodes can confirm a certain node has found a valid solution.

Why is that useful? The proof of work is used as a way to ensure we don't write the same block to the chain multiple times. Only the 'winner' of the challenge can write down a block in the chain. In practice this means all the messages a node receives are stored in memory along a timestamp until:

* they solve the challenge and save the block
* they get a notification that another node has solved the challenge (with the timestamp, to discard messages already committed to the chain)

Why would nodes want to do that? This is a problem designed to be compute-intensive (thus the use of GPU by Bitcoin miners, for example) which means a hefty electricity bill associated. But the protocol requires this to happen to maintain trust between peers. So there is an incentive for nodes to 'mine' (that is, solve the challenge): the winner not only writes the block, they also get an economic reward (varies depending on the network). So the block they write will contain the messages to add ot the blockchain, plus a new message that increases their balance by some amount. 

And if there's a conflict? Technically two nodes could solve the problem at the same time and write the block. There are 2 options in this scenario:

* the write exactly the same block, with the same data and hash. No conflict
* due to network latency and messages they write different blocks. In this case the network has a protocol to resolve conflicts. For example it can always choose the longest chain by default, so one of the commits is discarded (and one of the workers will lose their reward).

How would that look in Scala? We can use the sample implementation for the [token tutorial](https://www.ethereum.org/token#proof-of-work)) o implement a challenge in Scala:

```scala
var currentChallenge = _                  // The coin starts with a challenge
var timeOfLastProof = _                   // Variable to keep track of when rewards were given
var difficulty = Math.pow(10, 32)         // Difficulty starts reasonably low

def proofOfWork(nonce: Int) = {
    val n = sha3(nonce, currentChallenge)    // Generate a random hash based on input
    require(n >= difficulty)                 // Check if it's under the difficulty

    val timeSinceLastProof = now - timeOfLastProof             // Calculate time since last reward was given
    require(timeSinceLastProof >=  5 seconds)                  // Rewards cannot be given too quickly
    balanceOf[msg.sender] += timeSinceLastProof / 60 seconds;  // The reward to the winner grows by the minute

    difficulty = difficulty * 10 minutes / timeSinceLastProof + 1  // Adjusts the difficulty
    timeOfLastProof = now                                          // Reset the counter
    currentChallenge = sha3(nonce, currentChallenge, block.blockhash(block.number - 1))  // Save a hash that will be used as the next proof
}
``` 

### Proof of Stake

[Proof of Stake](https://en.wikipedia.org/wiki/Proof-of-stake) is another popular concept in Blockchain,a s a replacement of `Proof of Work`. To see a discussion of the implementation in Ethereum read [this post](https://blog.ethereum.org/2015/12/28/understanding-serenity-part-2-casper/).

Why is this needed? Proof of work has fulfilled its duties well, but it has a few issues:

* the protocol limits how fast can transactions happen in the chain. The fact a new block can take 10m to be written in the chain means a transaction can take a long time to be confirmed, not useful when doing daily transactions (like buying in a shop)
* mining is very resource intensive. This includes electricity, which cause a noticeably impact on the ecosystem as well as causing high running costs for miners.


How does it work? Instead of fighting for a reward, the nodes are assigned in a pseudo-random way. The selected node writes the block to the chain. There is no reward for doing so, only the transaction fees associated ot the operations in the block. 

An alternative (Casper, in Ethereum) works by a system of betting in which nodes can join a special contract which opens the door to `betting` on which blocks they expect to be written to the chain. If they are right, they get a reward for the bet, if they are wrong they lose the bet. In a recursive way, blocks with most bets in favour are written to the chain. 

Sounds complex? It is. In all honestly I'm not 100% sure of the details. There's also suspicion on `Proof of Stake` as forgeries on the chains have been proved possible, but `Proof of Stake` seems the way forward in blockchain world.
 

## Contracts

The following contracts exist in this implementation. You can see them in action in the `Examples.scala` test file.

### Scala Coin

We have added a virtual coin on top of this blockchain: ScalaCoin 

A user can create a ScalaCoin contract, and from that moment onwards wallets can use the `uuid` of the contract along `uuid` of other wallets to transfer money between users.

The way this works is:

* When a message for a transfer is received, the node loads the contract from the chain and restores it to the latest status by reading and applying all operations from the chain
* The transaction is validated to avoid transfers of money a user doesn't own, etc
* If all is correct, the transaction is written to the chain. The next time the contract is loaded this new message will update balances accordingly


### DAO

TODO


-- we need to clean code ??


## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with
any pull requests, please state that the contribution is your original work and that you license
the work to the project under the project's open source license. Whether or not you state this
explicitly, by submitting any copyrighted material via pull request, email, or other means you
agree to license the material under the project's open source license and warrant that you have the
legal authority to do so.

## License ##

This code is open source software licensed under the
[Apache-2.0](http://www.apache.org/licenses/LICENSE-2.0) license.
