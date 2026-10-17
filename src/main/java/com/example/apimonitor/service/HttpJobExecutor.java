
package com.example.apimonitor.service;

import com.example.apimonitor.dto.HttpExecutionResult;
import com.example.apimonitor.exception.HttpExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class HttpJobExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(HttpJobExecutor.class);

    private final ObjectMapper objectMapper;
    private final HttpSender httpSender;
    private final long readTimeoutMs;
    private final boolean allowLoopback;

    @Autowired
    public HttpJobExecutor(
            ObjectMapper objectMapper,
            @Value("${app.http.connect-timeout-ms:5000}")
            long connectTimeoutMs,
            @Value("${app.http.read-timeout-ms:10000}")
            long readTimeoutMs,
            @Value("${app.http.allow-loopback:false}")
            boolean allowLoopback) {

        this(
                objectMapper,
                createDefaultHttpSender(connectTimeoutMs),
                readTimeoutMs,
                allowLoopback
        );
    }

    public HttpJobExecutor(
            ObjectMapper objectMapper,
            HttpSender httpSender,
            long readTimeoutMs,
            boolean allowLoopback) {

        this.objectMapper = objectMapper;
        this.httpSender = httpSender;
        this.readTimeoutMs = readTimeoutMs;
        this.allowLoopback = allowLoopback;
    }

    private static HttpSender createDefaultHttpSender(
            long connectTimeoutMs) {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        return request -> {
            var response = client.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );

            return new HttpSender.SimpleHttpResponse(
                    response.statusCode(),
                    response.body()
            );
        };
    }

    public HttpExecutionResult execute(
            Map<String, Object> payload) {

        if (payload == null) {
            throw new IllegalArgumentException(
                    "Job payload is required"
            );
        }

        String url = Objects.toString(
                payload.get("url"), ""
        ).trim();

        validateUrl(url);

        String method = Objects.toString(
                payload.getOrDefault("method", "GET"), "GET"
        ).toUpperCase().trim();

        Map<String, String> headers =
                extractHeaders(payload.get("headers"));

        String body = extractBody(payload.get("body"));

        logSafeRequestDetails(method, url, headers);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMs));

        headers.forEach((key, value) -> {
            try {
                builder.header(key, value);
            } catch (IllegalArgumentException ex) {
                log.warn(
                        "Skipping restricted header '{}': {}",
                        key, ex.getMessage()
                );
            }
        });

        HttpRequest.BodyPublisher publisher =
                body != null && !body.isEmpty()
                        ? HttpRequest.BodyPublishers.ofString(body)
                        : HttpRequest.BodyPublishers.noBody();

        switch (method) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(publisher);
            case "PUT" -> builder.PUT(publisher);
            case "DELETE" -> builder.DELETE();
            case "PATCH" ->
                    builder.method("PATCH", publisher);
            default -> throw new IllegalArgumentException(
                    "Unsupported HTTP method: " + method
            );
        }

        long start = System.currentTimeMillis();
        HttpSender.SimpleHttpResponse response;

        try {
            response = httpSender.send(builder.build());

        } catch (HttpTimeoutException ex) {
            throw new HttpExecutionException(
                    "HTTP request timed out after "
                            + readTimeoutMs + "ms: "
                            + ex.getMessage()
            );

        } catch (IOException ex) {
            throw new HttpExecutionException(
                    "HTTP connection I/O failure: "
                            + ex.getMessage()
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new HttpExecutionException(
                    "HTTP request interrupted: "
                            + ex.getMessage()
            );
        }

        long durationMs =
                System.currentTimeMillis() - start;

        int statusCode = response.statusCode();

        log.info(
                "Outbound HTTP {} to '{}' returned status {} in {} ms",
                method, url, statusCode, durationMs
        );

        if (statusCode >= 200 && statusCode < 300) {
            return new HttpExecutionResult(
                    statusCode,
                    response.body(),
                    durationMs
            );
        }

        throw new HttpExecutionException(
                statusCode,
                "HTTP request failed with status code "
                        + statusCode
        );
    }

    public void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Target URL is required"
            );
        }

        URI uri;

        try {
            uri = URI.create(url);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Malformed URL: " + url, ex
            );
        }

        String scheme = uri.getScheme();

        if (scheme == null
                || (!scheme.equalsIgnoreCase("http")
                && !scheme.equalsIgnoreCase("https"))) {

            throw new IllegalArgumentException(
                    "Only HTTP and HTTPS are permitted"
            );
        }

        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid URL host: " + url
            );
        }

        if (!allowLoopback) {
            try {
                InetAddress[] addresses =
                        InetAddress.getAllByName(host);

                for (InetAddress address : addresses) {
                    if (isBlockedIp(address, host)) {
                        throw new SecurityException(
                                "SSRF protection blocked host: "
                                        + host
                        );
                    }
                }

            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException(
                        "Unable to resolve host: " + host,
                        ex
                );
            }
        }
    }

    private boolean isBlockedIp(
            InetAddress address, String host) {

        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()) {
            return true;
        }

        String ip = address.getHostAddress();

        return "169.254.169.254".equals(ip)
                || "100.100.100.200".equals(ip)
                || "metadata.google.internal"
                .equalsIgnoreCase(host);
    }

    private Map<String, String> extractHeaders(
            Object headersObject) {

        Map<String, String> headers = new HashMap<>();

        if (headersObject instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null && value != null) {
                    headers.put(
                            key.toString(),
                            value.toString()
                    );
                }
            });
        }

        return headers;
    }

    private String extractBody(Object bodyObject) {
        if (bodyObject == null) {
            return null;
        }

        if (bodyObject instanceof String str) {
            return str;
        }

        try {
            return objectMapper.writeValueAsString(
                    bodyObject
            );
        } catch (Exception ex) {
            return bodyObject.toString();
        }
    }

    private void logSafeRequestDetails(
            String method,
            String url,
            Map<String, String> headers) {

        Map<String, String> safeHeaders = new HashMap<>();

        headers.forEach((key, value) -> {
            String lower = key.toLowerCase();

            if (lower.contains("auth")
                    || lower.contains("key")
                    || lower.contains("token")
                    || lower.contains("secret")
                    || lower.contains("cookie")
                    || lower.contains("password")) {

                safeHeaders.put(key, "******");
            } else {
                safeHeaders.put(key, value);
            }
        });

        log.info(
                "Executing outbound HTTP {} to '{}' with safe headers {}",
                method, url, safeHeaders
        );
    }
}
