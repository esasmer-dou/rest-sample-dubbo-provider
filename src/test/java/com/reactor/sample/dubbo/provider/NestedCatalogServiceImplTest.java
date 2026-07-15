package com.reactor.sample.dubbo.provider;

import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedCatalogServiceImplTest {

    @Test
    void nestedCatalogJsonIsUtf8AndReadyForRawForwarding() {
        NestedCatalogServiceImpl service = new NestedCatalogServiceImpl();
        byte[] bytes = service.getNestedCatalogJson();
        String json = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"catalog\""));
        assertTrue(json.contains("\"id\": \"catalog-demo\""));
        assertTrue(json.contains("\"categories\""));
        assertEquals("Low Latency Product Catalog", service.getCatalogTitle());
        assertEquals(3, service.countCatalogItems());
        assertEquals("catalog-demo", service.getCatalogInfo().id());
        assertEquals(2, service.listFeaturedItems(2).size());
        assertEquals("micro-dubbo", service.getCatalogAttributes().get("profile"));
    }
}
