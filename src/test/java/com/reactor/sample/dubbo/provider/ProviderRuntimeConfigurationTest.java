package com.reactor.sample.dubbo.provider;

import com.alibaba.com.caucho.hessian.io.Hessian2Input;
import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.rust.dubbo.sample.dto.CatalogItem;
import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
        int customerBySegmentLimit = intValue(properties, "dubbo.provider.service.CustomerQueryService.method.findCustomersBySegment.max-concurrent");
        int customerCommandLimit = intValue(properties, "dubbo.provider.service.CustomerCommandService.max-concurrent");
        int createCommandLimit = intValue(properties, "dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent");
        int createTypedCommandLimit = intValue(properties, "dubbo.provider.service.CustomerCommandService.method.createCustomerTyped.max-concurrent");

        assertTrue(customerServiceLimit <= hikariMaxPool,
                "DB-backed service concurrency should not exceed Hikari max pool size");
        assertTrue(customerMethodLimit <= customerServiceLimit,
                "Method override should be equal to or smaller than service concurrency");
        assertTrue(customerBySegmentLimit <= customerServiceLimit,
                "Typed list query method override should be equal to or smaller than service concurrency");
        assertTrue(customerCommandLimit <= hikariMaxPool,
                "DB write command concurrency should not exceed Hikari max pool size");
        assertTrue(createCommandLimit <= customerCommandLimit,
                "Write method override should be equal to or smaller than command service concurrency");
        assertTrue(createTypedCommandLimit <= customerCommandLimit,
                "Typed write method override should be equal to or smaller than command service concurrency");
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

    @Test
    void hessianCanCarryRecordListAndMapShapesUsedByTheSample() throws IOException {
        CatalogInfo info = new CatalogInfo(
                "catalog-demo",
                "Low Latency Product Catalog",
                "platform",
                "tr-west-1",
                2,
                "2026-06-08T00:00:00Z"
        );
        List<CatalogItem> items = List.of(
                new CatalogItem("sku-1", "First", "compute", 10.5, "USD", true),
                new CatalogItem("sku-2", "Second", "storage", 2.0, "USD", false)
        );
        Map<String, String> attributes = Map.of("shape", "Map<String,String>", "profile", "micro-dubbo");

        assertEquals(info, roundTrip(info, CatalogInfo.class));
        assertEquals(items, roundTrip(items, List.class));
        assertEquals(attributes, roundTrip(attributes, Map.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value, Class<?> type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bytes);
        output.writeObject(value);
        output.flush();

        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()));
        return (T) input.readObject(type);
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
