package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.CatalogJsonService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class CatalogJsonServiceImpl implements CatalogJsonService {

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
}
