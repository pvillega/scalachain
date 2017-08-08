# Understanding Blockchain basics

[BlockChain](https://en.wikipedia.org/wiki/Blockchain) is the current hype in the software community. Conferences, meetups,
you name it. In this post we are going to explain why the interest as well as the basics behind the blockchain theory. In 
the process we will create a 'single-node' chain using Scala.

<!-- more -->

# What is BlockChain

Blockchain is just a ledger. That's all. Technically, it is a database that contains a growing list of records, called blocks.
Thus, chain of blocks, or blockchain. These blocks use cryptographic techniques to ensure they are tamper-proof, 
which means that once a piece of data is added to the chain, that can't be modified. This is a very useful property, as we will see later on.  

In practice the implementations are more complicated as the database is distributed across multiple nodes. As a consequence
we need mechanism for fault tolerance and 
which means we need
mechanisms



Your app communicates to it to write down an entry saying something happened. That's it.

Based on

https://medium.com/@lhartikk/a-blockchain-in-200-lines-of-code-963cc1cc0e54

Do attrib! please!

Basic scala, create object with api for easy understanding of concepts

https://skillsmatter.com/skillscasts/10315-an-introduction-to-blockchain-technology

Relevance of blockchain: http://www3.weforum.org/docs/WEF_Realizing_Potential_Blockchain.pdf
http://www.coindesk.com/end-poverty-restore-trust-world-bank-dives-into-blockchain-with-lab-launch/

More on private blockchain: https://blog.acolyer.org/2017/07/05/blockbench-a-framework-for-analyzing-private-blockchains/

History https://medium.com/humanizing-the-singularity/a-brief-history-of-blockchain-647209f2180

Reference: https://skillsmatter.com/skillscasts/10315-an-introduction-to-blockchain-technology
Notes from https://skillsmatter.com/skillscasts/10315-an-introduction-to-blockchain-technology

Blockchain provides a trust layer without a central authority
 

Proof of work + longest chain for network p2p

Oyente verification of smart contracts

Warning: no proof of work https://en.wikipedia.org/wiki/Proof-of-work_system so not safe in public network
Means we can't see agreement/consensus between ndoes, this is just sample with 1 single node :)

Have nodes agree on transactions (changes to the chain)
The fourth major innovation, the current cutting edge of blockchain thinking, is called “proof of stake.” Current generation blockchains are secured by “proof of work,” in which the group with the largest total computing power makes the decisions. These groups are called “miners” and operate vast data centers to provide this security, in exchange for cryptocurrency payments. The new systems do away with these data centers, replacing them with complex financial instruments, for a similar or even higher degree of security. Proof-of-stake systems are expected to go live later this year.


Case example (api on top of blockchain api) 
	- Currency
	-    pay to user from user

What about mining? And doubloe spending?
Page 34/35 https://drive.google.com/file/d/0ByBe1QJVC-EJRUJqVWcyY2VNdlU/view
	- Add new block (with cash price for discovery) - proof of workin bitcoin, for example
	-   solve problem - make it a random.int === a certain value with limit on 100
	- 


Another use case: voting in shareholder meetings, electronically, as in https://www.cryptocoinsnews.com/nasdaq-estonias-e-residency-empower-shareholders-estonian-stock-exchange/
This ensures there is no tampering and the full history of the vote could be retraced.  
     



What about smart contracts?

http://solidity.readthedocs.io/en/develop/introduction-to-smart-contracts.html
	- 2 parts, code and data. 
	- Data stored in ledger
	- Code, activated by messages received (contract is a state machine)
 - code stored as a special account that has code
A transaction is a message that is sent from one account to another accoun -> transaction to the user triggers contract.



!!! NOTE: once written, add 'multiple netowrk nodes' and conflict resolution/proof of stake between them. Implement it!
- it can be a good talk for scalax bytes, then refine for the main talk.
