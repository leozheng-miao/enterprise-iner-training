package com.leo.enterpriseinertraining.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-for-hs256-must-be-at-least-32-bytes-please");
        props.setExpireMillis(60_000L);
        jwtUtils = new JwtUtils(props);
    }

    @Test
    void generate_and_parse_round_trip() {
        String token = jwtUtils.generate(42L, "alice", "USER");
        assertNotNull(token);

        JwtUtils.JwtPayload p = jwtUtils.parse(token);
        assertEquals(42L, p.userId());
        assertEquals("alice", p.username());
        assertEquals("USER", p.role());
    }

    @Test
    void parse_invalid_token_throws() {
        assertThrows(RuntimeException.class, () -> jwtUtils.parse("not-a-jwt"));
    }

    @Test
    void parse_expired_token_throws() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-for-hs256-must-be-at-least-32-bytes-please");
        props.setExpireMillis(1L);
        JwtUtils shortLived = new JwtUtils(props);
        String token = shortLived.generate(1L, "u", "USER");
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        assertThrows(RuntimeException.class, () -> shortLived.parse(token));
    }
}
