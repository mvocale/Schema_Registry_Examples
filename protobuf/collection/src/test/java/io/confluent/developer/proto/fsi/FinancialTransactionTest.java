package io.confluent.developer.proto.fsi;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import io.confluent.developer.proto.fsi.FinancialTransactionProto.FinancialTransaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FinancialTransactionTest {

    @Test
    public void testCreateTransaction() {
        Instant time = Instant.now();
        long transactionTime = Timestamp.newBuilder().setSeconds(time.getEpochSecond())
                .setNanos(time.getNano()).build().getSeconds();

        // Create a FinancialTransaction message
        FinancialTransaction transaction = FinancialTransaction.newBuilder()
                .setTransactionId("12345")
                .setTimestamp(transactionTime)
                .setTransactionType("deposit")
                .setAmount(100.00)
                .setCurrency("USD")
                .addAllTaxAmounts(Arrays.asList(2.50, 3.50, 4.50))
                .build();

        // Verify that the message is properly constructed
        assertEquals(transaction.getTransactionId(), "12345");
        assertEquals(transaction.getTimestamp(), transactionTime);
        assertEquals(transaction.getTransactionType(), "deposit");
        assertEquals(transaction.getAmount(), 100.00);
        assertEquals(transaction.getCurrency(), "USD");
        assertEquals(transaction.getTaxAmountsCount(), 3);

        // Serialize and deserialize the message
        byte[] serializedMessage = transaction.toByteArray();
        FinancialTransaction deserializedTransaction;
        try {
            deserializedTransaction = FinancialTransaction.parseFrom(serializedMessage);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Exception during transaction deserialization", e);
        }

        // Verify that the deserialized message is equal to the original message
        assertArrayEquals(serializedMessage, deserializedTransaction.toByteArray());
        assertEquals(deserializedTransaction.getTransactionId(), transaction.getTransactionId());
        assertEquals(deserializedTransaction.getTimestamp(), transaction.getTimestamp());
        assertEquals(deserializedTransaction.getTransactionType(), transaction.getTransactionType());
        assertEquals(deserializedTransaction.getAmount(), transaction.getAmount());
        assertEquals(deserializedTransaction.getCurrency(), transaction.getCurrency());
        assertEquals(deserializedTransaction.getTaxAmountsCount(), transaction.getTaxAmountsCount());
    }
}