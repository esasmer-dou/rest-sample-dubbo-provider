package com.reactor.sample.dubbo.provider.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class CatalogPayloads {

    static final String NESTED_CATALOG = """
            {
              "source": "rest-sample-dubbo-provider",
              "generatedAt": "%s",
              "catalog": {
                "id": "catalog-demo",
                "name": "Low Latency Product Catalog",
                "owner": {"team": "platform", "region": "tr-west-1"},
                "categories": [
                  {
                    "code": "compute",
                    "priority": 1,
                    "items": [
                      {
                        "sku": "cpu-lowrss-01",
                        "name": "Low RSS Node",
                        "price": {"amount": 89.50, "currency": "USD"},
                        "attributes": {"cores": "16", "memory": "32Gi", "profile": "low-rss"}
                      },
                      {
                        "sku": "cpu-balanced-01",
                        "name": "Balanced Dubbo Node",
                        "price": {"amount": 119.00, "currency": "USD"},
                        "attributes": {"cores": "24", "memory": "48Gi", "profile": "balanced-dubbo"}
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
                        "price": {"amount": 19.25, "currency": "USD"},
                        "attributes": {"iops": "120000", "tier": "hot"}
                      }
                    ]
                  }
                ]
              }
            }
            """;
    private static final int TIMESTAMP = NESTED_CATALOG.indexOf("%s");
    private static final byte[] PREFIX = NESTED_CATALOG.substring(0, TIMESTAMP).getBytes(StandardCharsets.UTF_8);
    private static final byte[] SUFFIX = NESTED_CATALOG.substring(TIMESTAMP + 2).getBytes(StandardCharsets.UTF_8);

    private CatalogPayloads() {}

    static byte[] nestedCatalogJson() {
        return nestedCatalogJson(Instant.now());
    }

    static byte[] nestedCatalogJson(Instant generatedAt) {
        byte[] timestamp = generatedAt.toString().getBytes(StandardCharsets.UTF_8);
        byte[] response = new byte[PREFIX.length + timestamp.length + SUFFIX.length];
        System.arraycopy(PREFIX, 0, response, 0, PREFIX.length);
        System.arraycopy(timestamp, 0, response, PREFIX.length, timestamp.length);
        System.arraycopy(SUFFIX, 0, response, PREFIX.length + timestamp.length, SUFFIX.length);
        return response;
    }
}
