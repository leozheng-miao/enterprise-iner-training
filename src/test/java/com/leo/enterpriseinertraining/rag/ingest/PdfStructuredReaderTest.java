package com.leo.enterpriseinertraining.rag.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 真实 PDF 读取留给 Task 8 集成验证。
 * 这里只确保 Bean 能实例化。
 */
class PdfStructuredReaderTest {
    @Test
    void can_instantiate() {
        assertNotNull(new PdfStructuredReader());
    }
}
