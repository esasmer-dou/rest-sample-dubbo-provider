package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.db.SampleCustomer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public final class NestedCatalogServiceImpl implements NestedCatalogService, AutoCloseable {

    private final PostgresCustomerRepository customerRepository;

    public NestedCatalogServiceImpl(PostgresCustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public void warmupDatabase() {
        customerRepository.findCustomers();
    }

    @Override
    public byte[] getNestedCatalogJson() {
        String json = """
                {
                  "source": "rest-sample-dubbo-provider",
                  "generatedAt": "%s",
                  "catalog": {
                    "id": "catalog-demo",
                    "name": "Low Latency Product Catalog",
                    "owner": {
                      "team": "platform",
                      "region": "tr-west-1"
                    },
                    "categories": [
                      {
                        "code": "compute",
                        "priority": 1,
                        "items": [
                          {
                            "sku": "cpu-lowrss-01",
                            "name": "Low RSS Node",
                            "price": {
                              "amount": 89.50,
                              "currency": "USD"
                            },
                            "attributes": {
                              "cores": "16",
                              "memory": "32Gi",
                              "profile": "low-rss"
                            }
                          },
                          {
                            "sku": "cpu-balanced-01",
                            "name": "Balanced Dubbo Node",
                            "price": {
                              "amount": 119.00,
                              "currency": "USD"
                            },
                            "attributes": {
                              "cores": "24",
                              "memory": "48Gi",
                              "profile": "balanced-dubbo"
                            }
                          }
                        ]
                      },
                      {
                        "code": "storage",
                        "priority": 2,
                        "items": [
                          {
                            "sku": "nvme-hot-01",
                            "name": "Hot NVMe Volume",
                            "price": {
                              "amount": 19.25,
                              "currency": "USD"
                            },
                            "attributes": {
                              "iops": "120000",
                              "tier": "hot"
                            }
                          }
                        ]
                      }
                    ]
                  }
                }
                """.formatted(Instant.now());
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] getDatabaseCustomersJson() {
        List<SampleCustomer> customers = customerRepository.findCustomers();
        StringBuilder json = new StringBuilder(512 + customers.size() * 160);
        json.append("{\n");
        json.append("  \"source\": \"rest-sample-dubbo-provider\",\n");
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
