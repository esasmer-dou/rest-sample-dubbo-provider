package com.reactor.sample.dubbo.provider.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerCommandJsonWriterTest {

    @Test
    void escapesUtf8AndControlCharactersWithoutChangingResponseShape() {
        String json = new String(
                new CustomerCommandJsonWriter().error("geçersiz_istek", "İsim \"zorunlu\"\nalan"),
                StandardCharsets.UTF_8);

        assertTrue(json.contains("\"source\":\"rest-sample-dubbo-provider\""));
        assertTrue(json.contains("\"code\":\"geçersiz_istek\""));
        assertTrue(json.contains("İsim \\\"zorunlu\\\"\\nalan"));
    }
}
