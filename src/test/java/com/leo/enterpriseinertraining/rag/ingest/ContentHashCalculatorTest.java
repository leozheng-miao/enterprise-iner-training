package com.leo.enterpriseinertraining.rag.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.*;

class ContentHashCalculatorTest {

    @Test
    void sha256_same_content_yields_same_hash() {
        Resource a = new ByteArrayResource("hello".getBytes());
        Resource b = new ByteArrayResource("hello".getBytes());
        assertEquals(ContentHashCalculator.sha256(a), ContentHashCalculator.sha256(b));
    }

    @Test
    void sha256_different_content_differs() {
        Resource a = new ByteArrayResource("hello".getBytes());
        Resource b = new ByteArrayResource("world".getBytes());
        assertNotEquals(ContentHashCalculator.sha256(a), ContentHashCalculator.sha256(b));
    }

    @Test
    void sha256_length_is_64() {
        Resource a = new ByteArrayResource("hello".getBytes());
        assertEquals(64, ContentHashCalculator.sha256(a).length());
    }
}
