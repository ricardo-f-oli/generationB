package com.generationb.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CreatorDB API v3 over a real socket, for tests.
 *
 * <p>The live API has no sandbox — every call there spends credits — so the response bodies are
 * built from the shapes in CreatorDB's OpenAPI document. A path with no scripted answer replies
 * the way CreatorDB does for a creator it has not indexed: 404 {@code NOT_FOUND}, which is free.
 */
public class CreatorDbStub implements AutoCloseable {

    public record Call(String path, String body, String apiKey) {
    }

    private final HttpServer server;
    private final Map<String, String> answers = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final List<Call> calls = new ArrayList<>();
    private volatile double creditsAvailable = 9000;

    public CreatorDbStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** {@code data} for a path such as {@code /instagram/profile}. */
    public CreatorDbStub answer(String path, String dataJson) {
        answers.put(path, dataJson);
        statuses.remove(path);
        return this;
    }

    /** An error status for a path: 404 NOT_FOUND, 402 INSUFFICIENT_CREDITS, 401, 500. */
    public CreatorDbStub fail(String path, int status) {
        statuses.put(path, status);
        return this;
    }

    public CreatorDbStub withCredits(double credits) {
        this.creditsAvailable = credits;
        return this;
    }

    public void reset() {
        answers.clear();
        statuses.clear();
        creditsAvailable = 9000;
        synchronized (calls) {
            calls.clear();
        }
    }

    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    public long count(String path) {
        return calls().stream().filter(c -> c.path().equals(path)).count();
    }

    public List<String> bodies(String path) {
        return calls().stream().filter(c -> c.path().equals(path)).map(Call::body).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        synchronized (calls) {
            calls.add(new Call(path, body, exchange.getRequestHeaders().getFirst("api-key")));
        }

        String data = answers.get(path);
        int status = statuses.getOrDefault(path, data == null ? 404 : 200);
        String response = switch (status) {
            case 200 -> "{\"success\":true,\"data\":" + data + ",\"creditsUsed\":1,\"creditsAvailable\":"
                    + creditsAvailable + ",\"errorCode\":\"\",\"errorDescription\":\"\",\"traceId\":\"stub\"}";
            case 402 -> "{\"success\":false,\"error\":\"PaymentRequiredError\",\"errorCode\":\"INSUFFICIENT_CREDITS\","
                    + "\"message\":\"No credits\",\"details\":{\"creditsAvailable\":0,\"creditsUsed\":0},\"traceId\":\"stub\"}";
            case 401 -> "{\"success\":false,\"error\":\"UnauthorizedError\",\"errorCode\":\"UNAUTHORIZED\","
                    + "\"message\":\"Invalid API key\",\"traceId\":\"stub\"}";
            case 404 -> "{\"success\":false,\"error\":\"NotFoundError\",\"errorCode\":\"NOT_FOUND\","
                    + "\"message\":\"Not found\",\"traceId\":\"stub\"}";
            default -> "{\"success\":false,\"error\":\"InternalError\",\"errorCode\":\"INTERNAL_ERROR\","
                    + "\"message\":\"Boom\",\"traceId\":\"stub\"}";
        };
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
