package io.github.chalsense.core.site;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebOriginTest {
    @Test
    void canonicalizesCaseAndDefaultPorts() {
        assertEquals("https://example.com", WebOrigin.parse("HTTPS://Example.COM:443").value());
        assertEquals("http://example.com", WebOrigin.parse("http://example.com:80").value());
        assertEquals("https://example.com:8443", WebOrigin.parse("https://example.com:8443").value());
    }

    @Test
    void recognizesCanonicalLoopbackOrigins() {
        assertTrue(WebOrigin.parse("http://localhost:3000").isLoopback());
        assertTrue(WebOrigin.parse("http://127.0.0.1").isLoopback());
        assertTrue(WebOrigin.parse("http://[::1]").isLoopback());
        assertFalse(WebOrigin.parse("https://example.com").isLoopback());
    }

    @Test
    void rejectsComponentsOutsideAnOrigin() {
        assertThrows(IllegalArgumentException.class, () -> WebOrigin.parse("https://example.com/"));
        assertThrows(IllegalArgumentException.class, () -> WebOrigin.parse("https://user@example.com"));
        assertThrows(IllegalArgumentException.class, () -> WebOrigin.parse("https://example.com?a=1"));
        assertThrows(IllegalArgumentException.class, () -> WebOrigin.parse("https://example.com#part"));
    }

    @Test
    void rejectsNonCanonicalHosts() {
        assertThrows(IllegalArgumentException.class, () -> WebOrigin.parse("https://example.com."));
        assertThrows(IllegalArgumentException.class, () -> WebOrigin.parse("https://127.000.0.1"));
        assertThrows(IllegalArgumentException.class, () -> WebOrigin.parse("https://例子.测试"));
    }
}
