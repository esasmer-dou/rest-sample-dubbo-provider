package com.reactor.sample.dubbo.provider.service;

import com.reactor.sample.model.customer.SampleCustomer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerQueryJsonWriterTest {

    @Test void summaryHasTheTypedHttpShapeIncludingNullEscapesAndEmptyList() throws Exception {
        var writer = new CustomerQueryJsonWriter();
        assertEquals("{\"customers\":[]}", new String(writer.customerSummaries(List.of()), StandardCharsets.UTF_8));
        var row = new SampleCustomer(7, "C-7", "Çağrı\n\"\\", "standard", null, "active",
                Instant.EPOCH, Instant.EPOCH);
        String actual = new String(writer.customerSummaries(List.of(row)), StandardCharsets.UTF_8);
        assertEquals("{\"customers\":[{\"id\":7,\"customerNo\":\"C-7\",\"fullName\":\"Çağrı\\n\\\"\\\\\",\"segment\":\"standard\",\"email\":null,\"status\":\"active\",\"updatedAt\":\"1970-01-01T00:00:00Z\"}]}", actual);
    }

    @Test
    void writesStableUtf8DatabasePayloadWithoutIntermediateDtos() {
        SampleCustomer customer = new SampleCustomer(
                1,
                "C-1",
                "Çağrı Özkan",
                "pilot",
                "c@example.com",
                "active",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));

        String json = new String(
                new CustomerQueryJsonWriter().databaseCustomers(List.of(customer)),
                StandardCharsets.UTF_8);

        assertTrue(json.contains("\"fullName\":\"Çağrı Özkan\""));
        assertTrue(json.contains("\"customers\":[{"));
        assertTrue(json.contains("\"storage\":\"postgresql-jdbc-hikari\""));
    }
}
