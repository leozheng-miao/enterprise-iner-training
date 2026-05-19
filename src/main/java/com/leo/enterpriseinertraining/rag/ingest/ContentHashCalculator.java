package com.leo.enterpriseinertraining.rag.ingest;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.security.MessageDigest;

public final class ContentHashCalculator {

    private ContentHashCalculator() {}

    public static String sha256(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 失败: " + e.getMessage(), e);
        }
    }
}
