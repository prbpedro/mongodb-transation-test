package com.github.prbpedro.mongotransactiontest;

import static com.mongodb.client.model.Filters.eq;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.TransactionBody;
import com.mongodb.client.model.Updates;

@SpringBootTest
class MongoTransactionTestApplicationTests {

    Logger log = Logger.getLogger(this.getClass().getName());

    String uri = "mongodb://root:password@mongo:27017/movies?authSource=admin&replicaSet=dbrs&retryWrites=false&retryReads=false";

    TransactionOptions txnOptions = TransactionOptions.builder()
            .readPreference(ReadPreference.primary())
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .build();

    MongoClient mongoClient;

    AtomicReference<Exception> ex1 = new AtomicReference<>();

    Thread transactedReplaceOne = new Thread(() -> {
        final MongoDatabase database = mongoClient.getDatabase("test");
        final MongoCollection<Document> collection = database.getCollection("movies");

        try (ClientSession session = mongoClient.startSession()) {
            session.withTransaction(
                    new TransactionBody<Document>() {
                        public Document execute() {
                            try {
                                log.info("[TX1] start");

                                Thread.sleep(300);

                                Document doc = collection.findOneAndUpdate(
                                        session,
                                        eq("title", "Tag"),
                                        Updates.set("lockId", UUID.randomUUID().toString()));

                                doc.put("newKey3", "FIRST");
                                Thread.sleep(5000);

                                log.info("[TX1] sleep after findOneAndUpdate");

                                collection.replaceOne(session, eq("title", "Tag"), doc);

                                log.info("[TX1] sleep after replaceOne");
                                Thread.sleep(10000);
                                log.info("[TX1] end");

                                return doc;

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                ex1.set(e);
                            }

                            return null;
                        }
                    },
                    txnOptions);
        }
    });

    @Test
    void shouldHandleConflictingTransactionsWaitingPostFindOneAndUpdate() throws InterruptedException {

        AtomicReference<Exception> ex2 = new AtomicReference<>();

        try (MongoClient mongoClientTry = MongoClients.create(uri)) {

            mongoClient = mongoClientTry;
            Thread t2 = new Thread(() -> {
                final MongoDatabase database = mongoClient.getDatabase("test");
                final MongoCollection<Document> collection = database.getCollection("movies");

                try (ClientSession session = mongoClient.startSession()) {

                    log.info("[TX2] start");
                    session.withTransaction(
                            new TransactionBody<Document>() {
                                public Document execute() {
                                    try {
                                        log.info("[TX2] sleep after start ");
                                        Thread.sleep(1000);

                                        Document doc = collection.find(
                                                eq("title", "Tag"))
                                                .first();

                                        doc.put("newKey3", "WAITED");

                                        log.info("[TX2] find");
                                        collection.replaceOne(session, eq("title", "Tag"), doc);
                                        log.info("[TX2] replaceOne");
                                        log.info("[TX2] end");

                                        return doc;

                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                        throw new RuntimeException(e);
                                    }
                                }
                            },
                            txnOptions);
                }
            });

            transactedReplaceOne.start();
            t2.start();

            transactedReplaceOne.join();
            t2.join();

            Assertions.assertNull(ex1.get());
            Assertions.assertNull(ex2.get());
        }
    }

    @Test
    void shouldHandleConflictingTransactions() throws InterruptedException {

        AtomicReference<Exception> ex2 = new AtomicReference<>();

        try (MongoClient mongoClientTry = MongoClients.create(uri)) {

            mongoClient = mongoClientTry;

            Thread t2 = new Thread(() -> {
                final MongoDatabase database = mongoClient.getDatabase("test");
                final MongoCollection<Document> collection = database.getCollection("movies");

                try (ClientSession session = mongoClient.startSession()) {
                    Thread.sleep(100);

                    log.info("[TX2] start");
                    session.withTransaction(
                            new TransactionBody<Document>() {
                                public Document execute() {
                                    try {
                                        Thread.sleep(7000);
                                        log.info("[TX2] sleep after start ");

                                        Document doc = collection.findOneAndUpdate(
                                                session,
                                                eq("title", "Tag"),
                                                Updates.set("lockId", UUID.randomUUID().toString()));

                                        doc.put("newKey3", "WAITED");

                                        log.info("[TX2] findOneAndUpdate");
                                        collection.replaceOne(session, eq("title", "Tag"), doc);
                                        log.info("[TX2] replaceOne");
                                        log.info("[TX2] end");

                                        return doc;

                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                        throw new RuntimeException(e);
                                    }
                                }
                            },
                            txnOptions);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            });

            transactedReplaceOne.start();
            t2.start();

            transactedReplaceOne.join();
            t2.join();

            Assertions.assertNull(ex1.get());
            Assertions.assertNull(ex2.get());

        }
    }

    @Test
    void shouldThrowErrorForForcedOperationWhileTransacting() throws InterruptedException {

        AtomicReference<Exception> ex2 = new AtomicReference<>();

        try (MongoClient mongoClientTry = MongoClients.create(uri)) {
            mongoClient = mongoClientTry;

            Thread t2 = new Thread(() -> {
                final MongoDatabase database = mongoClient.getDatabase("test");
                final MongoCollection<Document> collection = database.getCollection("movies");

                try {
                    Thread.sleep(4000);

                    log.info("[TX2] start");

                    Document doc = collection.find(
                            eq("title", "Tag")).first();

                    doc.put("newKey3", "WAITED");

                    log.info("[TX2] find");
                    var result = collection.replaceOne(eq("title", "Tag"), doc);

                    if (!result.wasAcknowledged()) {
                        throw new RuntimeException("Not Acknowledged ");
                    }

                    log.info("[TX2] replaceOne");
                    log.info("[TX2] end");

                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            });

            transactedReplaceOne.start();
            t2.start();

            transactedReplaceOne.join();
            t2.join();

            Assertions.assertNull(ex1.get());
            Assertions.assertNull(ex2.get());

        }
    }

}
