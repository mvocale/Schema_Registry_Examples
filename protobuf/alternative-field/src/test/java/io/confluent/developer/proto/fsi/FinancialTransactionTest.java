package io.confluent.developer.proto.fsi;

import io.confluent.developer.proto.fsi.alternative_field.DepositTransaction;
import io.confluent.developer.proto.fsi.alternative_field.FinancialTransactionOuterClass.FinancialTransaction;
import io.confluent.developer.proto.fsi.alternative_field.TransferTransaction;
import io.confluent.developer.proto.fsi.alternative_field.WithdrawalTransaction;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static java.nio.file.Files.newInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FinancialTransactionTest {

    private static final String TOPIC = "transactions-protobuf";

    private static final String[] TRANSACTION_TYPES = {"deposit", "withdrawal", "transfer"};

    @Test
    public void testCreateTransaction() throws IOException {

        // Initialize Log4j logging
        BasicConfigurator.configure();
        // Set the log level to INFO
        Logger.getRootLogger().setLevel(Level.DEBUG);

        Properties schemaRegistryProperties = new Properties();
        schemaRegistryProperties.load(
                newInputStream(Path.of(System.getProperty("user.dir") + "/../../build-environment/src/main/resources/schema_registry_values.properties")));

        Properties kafkaClusterProperties = new Properties();
        kafkaClusterProperties.load(
                newInputStream(Path.of(System.getProperty("user.dir") + "/../../build-environment/src/main/resources/kafka_cluster_values.properties")));

        String producerSaslJaasConfig = "org.apache.kafka.common.security.plain.PlainLoginModule required username='%s' password='%s';".formatted(kafkaClusterProperties.getProperty("api_key"), kafkaClusterProperties.getProperty("api_secret"));
        String bootstrapServers = kafkaClusterProperties.getProperty("endpoint");

        Properties propsProducer = new Properties();
        propsProducer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        propsProducer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
        // Important properties needed to manage the schema reference in protobuf: in this way, given a reference name,
        // the producer replace slashes with dots, and remove the .proto suffix to obtain the subject name.
        // The schema reference used for Customer object will be customer-transaction as specified in Maven pom.xml during register schema phase.
        propsProducer.put("reference.subject.name.strategy", "io.confluent.kafka.serializers.subject.QualifiedReferenceSubjectNameStrategy");

        propsProducer.put(ProducerConfig.ACKS_CONFIG, "all");
        propsProducer.put(ProducerConfig.RETRIES_CONFIG, 0);

        // Confluent Schema Registry for Java
        propsProducer.put("basic.auth.credentials.source", "USER_INFO");
        propsProducer.put("schema.registry.basic.auth.user.info", schemaRegistryProperties.get("api_key") + ":" + schemaRegistryProperties.get("api_secret"));
        propsProducer.put("schema.registry.url", schemaRegistryProperties.get("endpoint_url"));
        propsProducer.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

        // Basic Confluent Cloud Connectivity
        propsProducer.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propsProducer.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        propsProducer.put(SaslConfigs.SASL_JAAS_CONFIG, producerSaslJaasConfig);
        propsProducer.put("security.protocol", "SASL_SSL");

        try (AdminClient adminClient = AdminClient.create(propsProducer)) {
            NewTopic newTopic = new NewTopic(TOPIC, 3, (short) 3);
            adminClient.createTopics(Collections.singleton(newTopic)).all().get();
            System.out.println("Topic created successfully");
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                System.out.println("Topic already exists");
            } else {
                System.out.println("Error creating topic");
                throw new RuntimeException(e);
            }
        }

        final FinancialTransaction writeFinancialTransaction;
        String transactionType = TRANSACTION_TYPES[new Random().nextInt(TRANSACTION_TYPES.length)];

        try (KafkaProducer<String, FinancialTransaction> producer = new KafkaProducer<>(propsProducer)) {
            writeFinancialTransaction = generateTransactionDetails(transactionType);
            final ProducerRecord<String, FinancialTransaction> record = new ProducerRecord<>(TOPIC, writeFinancialTransaction.getTransactionId(), writeFinancialTransaction);
            try {
                Logger.getRootLogger().info("Record produced: " + record);
                RecordMetadata metadata = producer.send(record).get();
                Logger.getRootLogger().info("Message sent to partition " + metadata.partition() + " offset " + metadata.offset());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            producer.flush();
        }

        Properties propsConsumer = new Properties();
        propsConsumer.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        propsConsumer.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class);
        propsConsumer.put(ConsumerConfig.GROUP_ID_CONFIG, "test-financial-transactions");
        propsConsumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        propsConsumer.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        propsConsumer.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        propsConsumer.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, FinancialTransaction.class);


        // Confluent Schema Registry for Java
        propsConsumer.put("basic.auth.credentials.source", "USER_INFO");
        propsConsumer.put("schema.registry.basic.auth.user.info", schemaRegistryProperties.get("api_key") + ":" + schemaRegistryProperties.get("api_secret"));
        propsConsumer.put("schema.registry.url", schemaRegistryProperties.get("endpoint_url"));
        propsConsumer.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

        // Basic Confluent Cloud Connectivity
        propsConsumer.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propsConsumer.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        propsConsumer.put(SaslConfigs.SASL_JAAS_CONFIG, producerSaslJaasConfig);
        propsConsumer.put("security.protocol", "SASL_SSL");

        // Create the topic needed for the test
        FinancialTransaction readFinancialTransaction = null;
        try (KafkaConsumer<String, FinancialTransaction> consumer = new KafkaConsumer<>(propsConsumer)) {
            consumer.subscribe(Collections.singletonList(TOPIC));
            final ConsumerRecords<String, FinancialTransaction> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, FinancialTransaction> record : records) {
                readFinancialTransaction = record.value();
                Logger.getRootLogger().info("Message read " + readFinancialTransaction);
            }
        }

        assertEquals(Objects.requireNonNull(readFinancialTransaction).getTransactionId(), writeFinancialTransaction.getTransactionId());
        assertEquals(readFinancialTransaction.getTimestamp(), writeFinancialTransaction.getTimestamp());
        assertEquals(readFinancialTransaction.getCurrency(), writeFinancialTransaction.getCurrency());
        // Add assertions for transaction_details
        switch (readFinancialTransaction.getTransactionDetailsCase()) {
            case DEPOSIT:
                assertEquals(writeFinancialTransaction.getDeposit().getDepositAmount(), readFinancialTransaction.getDeposit().getDepositAmount());
                break;
            case WITHDRAWAL:
                assertEquals(writeFinancialTransaction.getWithdrawal().getWithdrawalAmount(), readFinancialTransaction.getWithdrawal().getWithdrawalAmount());
                break;
            case TRANSFER:
                assertEquals(writeFinancialTransaction.getTransfer().getTransferAmount(), readFinancialTransaction.getTransfer().getTransferAmount());
                break;
        }
    }

    /**
     * Generates a random currency value between 0 and 100 USD.
     *
     * @return the random currency value rounded to the nearest integer
     */
    private double generateRandomCurrencyValues() {
        Currency currency = Currency.getInstance("USD");
        Random random = new Random();
        double randomValue = 1 + (100 - 1) * random.nextDouble();
        return Math.rint(randomValue * currency.getDefaultFractionDigits());
    }

    /**
     * Generates a `FinancialTransaction` object based on the specified transaction type.
     *
     * @param transactionType The type of transaction to generate ("deposit", "withdrawal", or "transfer").
     * @return A `FinancialTransaction` object with the specified details.
     * @throws IllegalArgumentException if an invalid transaction type is provided.
     */
    private FinancialTransaction generateTransactionDetails(String transactionType) {
        FinancialTransaction writeFinancialTransaction;
        switch (transactionType) {
            case "deposit" -> {
                DepositTransaction.Deposit depositDetails = DepositTransaction.Deposit.newBuilder()
                        .setDepositAmount(generateRandomCurrencyValues())
                        .setDescription("checking")
                        .build();
                long transactionTime = Instant.now().toEpochMilli();
                writeFinancialTransaction = FinancialTransaction.newBuilder()
                        .setTransactionId(UUID.randomUUID().toString())
                        .setTimestamp(transactionTime)
                        .setDeposit(depositDetails)
                        .setCurrency("USD")
                        .build();
            }
            case "withdrawal" -> {
                WithdrawalTransaction.Withdrawal withdrawalDetails = WithdrawalTransaction.Withdrawal.newBuilder()
                        .setWithdrawalAmount(generateRandomCurrencyValues())
                        .setReason("savings")
                        .build();
                long transactionTime = Instant.now().toEpochMilli();
                writeFinancialTransaction = FinancialTransaction.newBuilder()
                        .setTransactionId(UUID.randomUUID().toString())
                        .setTimestamp(transactionTime)
                        .setWithdrawal(withdrawalDetails)
                        .setCurrency("USD")
                        .build();
            }
            case "transfer" -> {
                TransferTransaction.Transfer transferTransaction = TransferTransaction.Transfer.newBuilder()
                        .setTransferAmount(generateRandomCurrencyValues())
                        .build();
                long transactionTime = Instant.now().toEpochMilli();
                writeFinancialTransaction = FinancialTransaction.newBuilder()
                        .setTransactionId(UUID.randomUUID().toString())
                        .setTimestamp(transactionTime)
                        .setTransfer(transferTransaction)
                        .setCurrency("USD")
                        .build();
            }
            default ->
                    throw new IllegalArgumentException("Invalid transaction type: " + transactionType);
        }
        return writeFinancialTransaction;
    }
}