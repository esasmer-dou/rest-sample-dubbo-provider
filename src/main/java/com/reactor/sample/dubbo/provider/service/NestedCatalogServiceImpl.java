package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.rust.dubbo.sample.dto.CatalogItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class NestedCatalogServiceImpl implements NestedCatalogService {

    private static final List<CatalogItem> ITEMS = List.of(
            new CatalogItem("cpu-lowrss-01", "Low RSS Node", "compute", 89.50, "USD", true),
            new CatalogItem("cpu-balanced-01", "Balanced Dubbo Node", "compute", 119.00, "USD", true),
            new CatalogItem("nvme-hot-01", "Hot NVMe Volume", "storage", 19.25, "USD", true)
    );

    @Override
    public byte[] getNestedCatalogJson() {
        return CatalogPayloads.nestedCatalogJson();
    }

    @Override
    public String getCatalogTitle() {
        return "Low Latency Product Catalog";
    }

    @Override
    public int countCatalogItems() {
        return ITEMS.size();
    }

    @Override
    public CatalogInfo getCatalogInfo() {
        return new CatalogInfo(
                "catalog-demo",
                getCatalogTitle(),
                "platform",
                "tr-west-1",
                ITEMS.size(),
                Instant.now().toString()
        );
    }

    @Override
    public List<CatalogItem> listFeaturedItems(int limit) {
        int boundedLimit = Math.max(0, Math.min(limit, ITEMS.size()));
        return ITEMS.subList(0, boundedLimit);
    }

    @Override
    public Map<String, String> getCatalogAttributes() {
        return Map.of(
                "source", "rest-sample-dubbo-provider",
                "shape", "Map<String,String>",
                "ownerTeam", "platform",
                "region", "tr-west-1",
                "profile", "micro-dubbo"
        );
    }
}
