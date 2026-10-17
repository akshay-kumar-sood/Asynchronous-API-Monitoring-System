package com.example.apimonitor.service;

import java.io.IOException;
import java.net.http.HttpRequest;

/**
 * Functional interface abstracting HTTP request transmission for testability and decoupling.
 */
@FunctionalInterface
public interface HttpSender {

    SimpleHttpResponse send(HttpRequest request) throws IOException, InterruptedException;

    record SimpleHttpResponse(int statusCode, String body) {}
}
