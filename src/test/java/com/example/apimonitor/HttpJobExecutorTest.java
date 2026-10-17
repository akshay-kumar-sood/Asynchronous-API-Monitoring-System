package com.example.apimonitor;

import com.example.apimonitor.dto.HttpExecutionResult;
import com.example.apimonitor.exception.HttpExecutionException;
import com.example.apimonitor.service.HttpJobExecutor;
import com.example.apimonitor.service.HttpSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpJobExecutorTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should execute outbound HTTP GET request successfully and return 200 result")
    void testExecuteHttpGetSuccess() {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedUri = new AtomicReference<>();

        HttpSender mockSender = request -> {
            capturedMethod.set(request.method());
            capturedUri.set(request.uri().toString());
            return new HttpSender.SimpleHttpResponse(200, "{\"status\":\"healthy\"}");
        };

        HttpJobExecutor executor = new HttpJobExecutor(objectMapper, mockSender, 5000, true);

        Map<String, Object> payload = Map.of(
                "url", "http://127.0.0.1:8080/health",
                "method", "GET"
        );

        HttpExecutionResult result = executor.execute(payload);

        assertNotNull(result);
        assertEquals(200, result.getStatusCode());
        assertEquals("{\"status\":\"healthy\"}", result.getResponseBody());
        assertEquals("GET", capturedMethod.get());
        assertEquals("http://127.0.0.1:8080/health", capturedUri.get());
    }

    @Test
    @DisplayName("Should execute outbound HTTP POST request with headers and JSON body")
    void testExecuteHttpPostWithBodyAndHeaders() {
        AtomicReference<String> capturedHeader = new AtomicReference<>();

        HttpSender mockSender = request -> {
            capturedHeader.set(request.headers().firstValue("X-Trace-Id").orElse(null));
            return new HttpSender.SimpleHttpResponse(201, "{\"id\":\"order-123\"}");
        };

        HttpJobExecutor executor = new HttpJobExecutor(objectMapper, mockSender, 5000, true);

        Map<String, Object> payload = Map.of(
                "url", "http://127.0.0.1:8080/api/orders",
                "method", "POST",
                "headers", Map.of("Content-Type", "application/json", "X-Trace-Id", "trace-999"),
                "body", Map.of("amount", 250, "currency", "USD")
        );

        HttpExecutionResult result = executor.execute(payload);

        assertEquals(201, result.getStatusCode());
        assertEquals("{\"id\":\"order-123\"}", result.getResponseBody());
        assertEquals("trace-999", capturedHeader.get());
    }

    @Test
    @DisplayName("Should throw HttpExecutionException when server returns 500 Internal Server Error")
    void testExecuteHttpServerError500() {
        HttpSender mockSender = request -> new HttpSender.SimpleHttpResponse(500, "Internal Server Error");
        HttpJobExecutor executor = new HttpJobExecutor(objectMapper, mockSender, 5000, true);

        Map<String, Object> payload = Map.of(
                "url", "http://127.0.0.1:8080/api/failing",
                "method", "GET"
        );

        HttpExecutionException ex = assertThrows(HttpExecutionException.class, () -> executor.execute(payload));
        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    @DisplayName("Should throw HttpExecutionException when HTTP request times out")
    void testExecuteHttpTimeout() {
        HttpSender mockSender = request -> {
            throw new HttpTimeoutException("Read timed out");
        };
        HttpJobExecutor executor = new HttpJobExecutor(objectMapper, mockSender, 5000, true);

        Map<String, Object> payload = Map.of(
                "url", "http://127.0.0.1:8080/api/slow",
                "method", "GET"
        );

        HttpExecutionException ex = assertThrows(HttpExecutionException.class, () -> executor.execute(payload));
        assertNull(ex.getStatusCode());
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    @DisplayName("Should reject unsupported schemes like ftp or file")
    void testRejectUnsupportedScheme() {
        HttpSender mockSender = request -> new HttpSender.SimpleHttpResponse(200, "ok");
        HttpJobExecutor strictExecutor = new HttpJobExecutor(objectMapper, mockSender, 5000, false);

        Map<String, Object> ftpPayload = Map.of("url", "ftp://ftp.example.com/file.txt");
        assertThrows(IllegalArgumentException.class, () -> strictExecutor.execute(ftpPayload));

        Map<String, Object> filePayload = Map.of("url", "file:///etc/passwd");
        assertThrows(IllegalArgumentException.class, () -> strictExecutor.execute(filePayload));
    }

    @Test
    @DisplayName("Should reject loopback and private IP addresses when SSRF protection is enabled")
    void testSsrfProtectionBlocksLoopbackAndPrivateIps() {
        HttpSender mockSender = request -> new HttpSender.SimpleHttpResponse(200, "ok");
        HttpJobExecutor strictExecutor = new HttpJobExecutor(objectMapper, mockSender, 5000, false);

        // 127.0.0.1 loopback
        Map<String, Object> loopbackPayload = Map.of("url", "http://127.0.0.1:8080/admin");
        SecurityException loopbackEx = assertThrows(SecurityException.class, () -> strictExecutor.execute(loopbackPayload));
        assertTrue(loopbackEx.getMessage().contains("SSRF Protection"));

        // Cloud metadata IP: 169.254.169.254
        Map<String, Object> metadataPayload = Map.of("url", "http://169.254.169.254/latest/meta-data/");
        SecurityException metaEx = assertThrows(SecurityException.class, () -> strictExecutor.execute(metadataPayload));
        assertTrue(metaEx.getMessage().contains("SSRF Protection"));
    }
}
