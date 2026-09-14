package de.extraitems;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;

/** Serves exactly one immutable ZIP; never exposes the filesystem. */
final class PackHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    PackHttpServer(String bind, int port, byte[] bytes, String sha1) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        executor = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), task -> {
            Thread t = new Thread(task, "ExtraItems-Pack-HTTP"); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getRawPath().equals("/extraitems.zip")) { exchange.sendResponseHeaders(404, -1); return; }
                String method = exchange.getRequestMethod();
                if (!method.equals("GET") && !method.equals("HEAD")) {
                    exchange.getResponseHeaders().set("Allow", "GET, HEAD"); exchange.sendResponseHeaders(405, -1); return;
                }
                var headers = exchange.getResponseHeaders();
                headers.set("Content-Type", "application/zip");
                headers.set("X-Content-Type-Options", "nosniff");
                headers.set("Cache-Control", "public, max-age=300");
                headers.set("ETag", "\"" + sha1 + "\"");
                headers.set("Content-Length", Integer.toString(bytes.length));
                if (method.equals("HEAD")) { exchange.sendResponseHeaders(200, -1); return; }
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
    }
    int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
