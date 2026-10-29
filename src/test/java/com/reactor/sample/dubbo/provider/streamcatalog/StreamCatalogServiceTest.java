package com.reactor.sample.dubbo.provider.streamcatalog;

import com.reactor.sample.dubbo.provider.service.StreamCatalogServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamCatalogServiceTest {

    private final StreamCatalogServiceImpl service = new StreamCatalogServiceImpl();

    @Test
    void acceptancePayloadsReuseTheSameImmutableRowsWithoutPerCallDtoConstruction() {
        assertSame(service.read(32), service.read(32));
        assertSame(service.read(512), service.read(512));
        assertEquals(service.read(512).subList(0, 32), service.read(32));
        assertThrows(UnsupportedOperationException.class, () -> service.read(32).clear());
        assertThrows(UnsupportedOperationException.class, () -> service.read(32).getFirst().details().tags().clear());
    }

    @Test
    void unicodeNullableNestedAndDifficultFiniteNumbersAreDeterministic() {
        var rows = service.read(32);
        assertEquals("\u00dcr\u00fcn \u0130pek 0", rows.getFirst().name());
        assertEquals("\u0130stanbul", rows.getFirst().details().city());
        assertEquals("\ud83d\ude80", rows.getFirst().details().tags().get(1));
        assertEquals("quote\" slash\\ line\n", rows.getFirst().details().tags().get(2));
        assertNull(rows.getFirst().details().note());
        assertNotNull(rows.get(1).details().note());
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(rows.get(1).amount()));
        assertEquals(Double.MIN_VALUE, rows.get(3).amount());
        assertEquals(Double.MAX_VALUE, rows.get(4).amount());
        assertTrue(rows.stream().allMatch(row -> Double.isFinite(row.amount())));
        assertEquals(rows, new StreamCatalogServiceImpl().read(32));
    }

    @Test
    void countHasAnExplicitBusinessBoundAndNeverSilentlyTruncates() {
        assertTrue(service.read(0).isEmpty());
        assertEquals(1, service.read(1).size());
        assertEquals(513, service.read(513).size());
        assertEquals(10_000, service.read(10_000).size());
        assertEquals(10_000L, service.read(10_000).getLast().id());
        assertThrows(IllegalArgumentException.class, () -> service.read(-1));
        assertThrows(IllegalArgumentException.class, () -> service.read(10_001));
    }
}
