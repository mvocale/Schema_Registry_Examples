package io.confluent.developer.avro.fsi;


import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
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
import org.apache.log4j.*;
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

    private static final String TOPIC = "transactions-avro";

    /**
     * Tests the creation of a financial transaction with enumeration values
     * related to a preloaded schema.
     *
     * @throws IOException if there is an I/O error while loading properties
     */
    @Test
    public void testCreateTransactionWithTaxAmounts() throws IOException {

        // Initialize Log4j logging
        BasicConfigurator.configure();
        // Set the log level to INFO
        Logger.getRootLogger().setLevel(Level.INFO);

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
        propsProducer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
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
            Logger.getRootLogger().info("Topic created successfully");
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                Logger.getRootLogger().warn("Topic already exists");
            } else {
                Logger.getRootLogger().error("Error creating topic");
                throw new RuntimeException(e);
            }
        }

        final FinancialTransaction writeFinancialTransaction;
        try (KafkaProducer<String, FinancialTransaction> producer = new KafkaProducer<>(propsProducer)) {
            long transactionTime = Instant.now().toEpochMilli();
            writeFinancialTransaction = new FinancialTransaction(UUID.randomUUID().toString(), transactionTime, "deposit",
                    generateRandomCurrencyValues(), "USD", Arrays.asList(2.50d, 3.50d, 4.50d),
                    getRandomValueTransactionStatus());
            final ProducerRecord<String, FinancialTransaction> record = new ProducerRecord<>(TOPIC, writeFinancialTransaction.getTransactionId().toString(), writeFinancialTransaction);
            try {
                RecordMetadata metadata = producer.send(record).get();
                Logger.getRootLogger().info("Message sent to partition " + metadata.partition() + " offset " + metadata.offset());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            producer.flush();
        }

        Properties propsConsumer = new Properties();
        propsConsumer.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        propsConsumer.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        propsConsumer.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        propsConsumer.put(ConsumerConfig.GROUP_ID_CONFIG, "test-financial-transactions");
        propsConsumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        propsConsumer.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        propsConsumer.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


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

        assertEquals(Objects.requireNonNull(readFinancialTransaction).getTransactionId().toString(), writeFinancialTransaction.getTransactionId());
        assertEquals(readFinancialTransaction.getTimestamp(), writeFinancialTransaction.getTimestamp());
        assertEquals(readFinancialTransaction.getTransactionType().toString(), writeFinancialTransaction.getTransactionType());
        assertEquals(readFinancialTransaction.getAmount(), writeFinancialTransaction.getAmount());
        assertEquals(readFinancialTransaction.getCurrency().toString(), writeFinancialTransaction.getCurrency());
        assertEquals(Objects.requireNonNull(readFinancialTransaction).getTaxAmounts().size(), writeFinancialTransaction.getTaxAmounts().size());
        assertEquals(readFinancialTransaction.getTransactionStatus(), writeFinancialTransaction.getTransactionStatus());

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
     * Generates a random {@link TransactionStatus} value.
     *
     * @return A random {@link TransactionStatus} value.
     */
    private TransactionStatus getRandomValueTransactionStatus() {
        EnumSet<TransactionStatus> values = EnumSet.allOf(TransactionStatus.class);
        List<TransactionStatus> shuffledValues = new ArrayList<>(values);
        Collections.shuffle(shuffledValues);
        return values.iterator().next();
    }
}