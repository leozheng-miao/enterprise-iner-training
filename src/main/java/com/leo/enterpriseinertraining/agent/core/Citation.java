package com.leo.enterpriseinertraining.agent.core;

public record Citation(
        Long docId,
        String docTitle,
        String source,
        String sectionTitle,
        Integer pageStart,
        Integer pageEnd
) {}
