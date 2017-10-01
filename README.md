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



-- The below are notes to be implemented and documented --

-- we need proof of work now as that is the way to guarantee a node stores data into the chain <- !!! important
-- we need a DAO-like contract to run in the network


//TODO: have the feeling that given the loops in method calls all this would be nicer as a state machine that calls methods in order as required...
// Improve code?


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
