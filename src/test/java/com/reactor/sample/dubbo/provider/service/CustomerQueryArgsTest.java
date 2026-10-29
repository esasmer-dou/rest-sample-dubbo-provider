package com.reactor.sample.dubbo.provider.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CustomerQueryArgsTest {
    @Test void compactQueryPreservesUnicodeWhitespaceAndEmptyStrings() {
        for (String segment : new String[]{"standard", " İpek Şahin 🌍 ", "", "a\"b\\c"}) {
            assertEquals(segment, CustomerQueryServiceImpl.decodeSegment(segment.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test void rejectsMalformedAndOversizedSegmentsWithoutTruncating() {
        assertThrows(IllegalArgumentException.class, () -> CustomerQueryServiceImpl.decodeSegment(null));
        assertThrows(IllegalArgumentException.class, () -> CustomerQueryServiceImpl.decodeSegment(new byte[4097]));
        assertThrows(IllegalArgumentException.class,
                () -> CustomerQueryServiceImpl.decodeSegment(new byte[]{(byte) 0xc3, 0x28}));
        assertEquals(4096, CustomerQueryServiceImpl.decodeSegment(new byte[4096]).length());
    }
}
