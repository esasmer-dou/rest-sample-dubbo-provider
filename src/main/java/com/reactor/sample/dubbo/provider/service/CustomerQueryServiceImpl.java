package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.db.SampleCustomer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public final class CustomerQueryServiceImpl implements CustomerQueryService, AutoCloseable {

    private final PostgresCustomerRepository customerRepository;

    public CustomerQueryServiceImpl(PostgresCustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public void warmupDatabase() {
        customerRepository.findCustomers();
    }

    @Override
    public byte[] getDatabaseCustomersJson() {
        List<SampleCustomer> customers = customerRepository.findCustomers();
        StringBuilder json = new StringBuilder(512 + customers.size() * 160);
        json.append("{\n");
        json.append("  \"source\": \"rest-sample-dubbo-provider\",\n");
        json.append("  \"service\": \"CustomerQueryService\",\n");
        json.append("  \"storage\": \"postgresql-activejdbc-hikari\",\n");
        json.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"customers\": [\n");
        for (int i = 0; i < customers.size(); i++) {
            SampleCustomer customer = customers.get(i);
            json.append("    {\n");
            json.append("      \"id\": ").append(customer.id()).append(",\n");
            json.append("      \"customerNo\": \"").append(escapeJson(customer.customerNo())).append("\",\n");
            json.append("      \"fullName\": \"").append(escapeJson(customer.fullName())).append("\",\n");
            json.append("      \"segment\": \"").append(escapeJson(customer.segment())).append("\",\n");
            json.append("      \"createdAt\": \"").append(customer.createdAt()).append("\"\n");
            json.append("    }");
            if (i + 1 < customers.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        customerRepository.close();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
