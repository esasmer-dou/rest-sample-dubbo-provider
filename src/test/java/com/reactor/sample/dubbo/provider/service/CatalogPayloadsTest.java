package com.reactor.sample.dubbo.provider.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CatalogPayloadsTest {
    @Test
    void preservesEveryByteOfTheDynamicPayloadWithoutFormatter() {
        for (String time : new String[]{"2026-10-18T10:00:00Z", "2026-10-18T10:00:00.123456789Z"}) {
            Instant instant = Instant.parse(time);
            byte[] expected = CatalogPayloads.NESTED_CATALOG.formatted(instant).getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(expected, CatalogPayloads.nestedCatalogJson(instant));
            assertNotSame(CatalogPayloads.nestedCatalogJson(instant), CatalogPayloads.nestedCatalogJson(instant));
        }
    }
}
