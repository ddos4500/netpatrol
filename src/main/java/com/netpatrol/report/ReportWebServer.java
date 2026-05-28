package com.netpatrol.report;

import com.netpatrol.store.HistoryStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ReportWebServer {
    private final HistoryStore historyStore;
    private HttpServer server;
    private int port;

    public ReportWebServer(HistoryStore historyStore) {
        this.historyStore = historyStore;
    }

    public void start(int preferredPort) throws IOException {
        this.port = preferredPort;
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", preferredPort), 0);
        server.createContext("/", new PageHandler());
        server.createContext("/latest", new JsonHandler());
        server.createContext("/api/latest", new JsonHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    public String url() {
        return "http://127.0.0.1:" + port + "/";
    }

    public int getPort() {
        return port;
    }

    private class PageHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>NetPatrol JSON Report</title>"
                    + "<style>body{margin:0;background:#0f172a;color:#e2e8f0;font:14px Consolas,monospace;}pre{white-space:pre-wrap;margin:0;padding:20px;}</style>"
                    + "</head><body><pre id=\"json\">loading...</pre><script>"
                    + "async function load(){const r=await fetch('/latest?ts='+Date.now());document.getElementById('json').textContent=await r.text();}"
                    + "load();setInterval(load,5000);</script></body></html>";
            send(exchange, "text/html; charset=utf-8", html);
        }
    }

    private class JsonHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            send(exchange, "application/json; charset=utf-8", historyStore.latestJson());
        }
    }

    private void send(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
    }
}
