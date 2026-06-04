package com.reactor.sample.dubbo.provider;

import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRuntimeConfigurationTest {

    @Test
    void providerKeepsDbBulkheadAlignedWithHikariPool() throws IOException {
        Properties properties = loadProperties();

        int hikariMaxPool = intValue(properties, "sample.db.maximum-pool-size");
        int customerServiceLimit = intValue(properties, "dubbo.provider.service.CustomerQueryService.max-concurrent");
        int customerMethodLimit = intValue(properties, "dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent");
        int customerCommandLimit = intValue(properties, "dubbo.provider.service.CustomerCommandService.max-concurrent");
        int createCommandLimit = intValue(properties, "dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent");

        assertTrue(customerServiceLimit <= hikariMaxPool,
                "DB-backed service concurrency should not exceed Hikari max pool size");
        assertTrue(customerMethodLimit <= customerServiceLimit,
                "Method override should be equal to or smaller than service concurrency");
        assertTrue(customerCommandLimit <= hikariMaxPool,
                "DB write command concurrency should not exceed Hikari max pool size");
        assertTrue(createCommandLimit <= customerCommandLimit,
                "Write method override should be equal to or smaller than command service concurrency");
        assertEquals("0", properties.getProperty("sample.db.minimum-idle"));
        assertEquals("3000", properties.getProperty("sample.db.connection-timeout-ms"));
    }

    @Test
    void providerKeepsNettyAndDubboLowRssTuning() throws IOException {
        Properties properties = loadProperties();

        assertEquals("1", properties.getProperty("io.netty.allocator.numDirectArenas"));
        assertEquals("1", properties.getProperty("io.netty.allocator.numHeapArenas"));
        assertEquals("0", properties.getProperty("io.netty.recycler.maxCapacityPerThread"));
        assertEquals("true", properties.getProperty("io.netty.noPreferDirect"));
        assertEquals("false", properties.getProperty("dubbo.application.qos.enable"));
        assertEquals("false", properties.getProperty("dubbo.metrics.enable"));
        assertEquals("false", properties.getProperty("dubbo.tracing.enabled"));
    }

    @Test
    void nestedCatalogJsonIsUtf8AndReadyForRawForwarding() {
        byte[] bytes = new NestedCatalogServiceImpl().getNestedCatalogJson();
        String json = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"catalog\""));
        assertTrue(json.contains("\"id\": \"catalog-demo\""));
        assertTrue(json.contains("\"categories\""));
    }

    private static Properties loadProperties() throws IOException {
        try (InputStream input = ProviderRuntimeConfigurationTest.class
                .getClassLoader()
                .getResourceAsStream("rest-sample-dubbo-provider.properties")) {
            assertNotNull(input, "rest-sample-dubbo-provider.properties must be available on the test classpath");
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        }
    }

    private static int intValue(Properties properties, String key) {
        return Integer.parseInt(properties.getProperty(key));
    }
}
