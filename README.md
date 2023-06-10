# mongodb-transaction-test
This project objective is to test mongodb transaction concurrency handling.

# Getting Started

This repository contains a docker compose file that starts a mongodb instance with a mongo-express pointing to it.

It also has a bash script [init-resources.sh](init-resources.sh) that makes this mongo instance a replicaSet and runs a js file on the mongo instance also.

The js file [mongo-init.js](mongo-init.js) creates a user and starts a movies dabase so we can run some transactions on it.

To run the tests the file [init-resources.sh](init-resources.sh) must have execution permission enabled.

Also add the following line '127.0.0.1  mongo' to ````/etc/host```` of your machine so the mongo replicaset is visible to the host.

Run the [init-resources.sh](init-resources.sh):
```bash
./init-resources.sh
```
After you can edit and run your own tests based on [MongoTransactionTestApplicationTests](src/test/java/com/github/prbpedro/mongotransactiontest/MongoTransactionTestApplicationTests.java) and observe how mongodb deal with conflicting transactions.

### Reference Documentation
For further reference, please consider the following sections:

* [Official MongoDB documentation on acquiring locks](https://www.mongodb.com/docs/manual/core/transactions-production-consideration/?_ga=2.90758106.1717373063.1686224778-845753589.1686223949#acquiring-locks)

* [Official MongoDB documentation on transactions](https://www.mongodb.com/docs/manual/core/transactions/)
