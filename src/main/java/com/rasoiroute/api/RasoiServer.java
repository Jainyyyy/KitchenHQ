package com.rasoiroute.api;

import com.rasoiroute.dispatch.OrderBook;
import com.rasoiroute.geo.LocalityDirectory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The web server. It does only the plumbing: read the request, hand it to {@link ApiService},
 * write the answer. It also serves the three frontend files (page, script, style).
 *
 * It listens on this computer only (127.0.0.1), so nobody else on the network can reach it.
 */
public final class RasoiServer {

    /** Largest request body accepted (64 KB). Stops someone from sending an enormous request. */
    static final int MAX_BODY_BYTES = 64 * 1024;

    /** The ONLY files the server will hand out. Anything else is "not found", so no one can ask for other files. */
    private static final Map<String, String> STATIC_FILES = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/app.js", "app.js",
            "/style.css", "style.css",
            "/leaflet.js", "leaflet.js",
            "/leaflet.css", "leaflet.css");

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "index.html", "text/html; charset=utf-8",
            "app.js", "text/javascript; charset=utf-8",
            "style.css", "text/css; charset=utf-8",
            "leaflet.js", "text/javascript; charset=utf-8",
            "leaflet.css", "text/css; charset=utf-8");

    private RasoiServer() {
        // static helper only
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("The port must be a number, for example: 8080");
                return;
            }
        }
        ApiService service = new ApiService(LocalityDirectory.loadDefault(), new OrderBook(10_000, 0.01), Clock.systemDefaultZone());
        start(port, service);
        System.out.println("KitchenHQ is running. Open http://localhost:" + port + " in your browser.");
        System.out.println("Press Ctrl+C (or the red stop button) to stop it.");
    }

    /** Starts the server. Pass port 0 to let the system choose a free port (used by tests). */
    public static HttpServer start(int port, ApiService service) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", exchange -> {
            try {
                handle(exchange, service);
            } catch (RuntimeException | IOException e) {
                trySend(exchange, 500, errorBody("Something went wrong on the server"));
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return server;
    }

    public static void stop(HttpServer server) {
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }

    // =====================================================================

    private static void handle(HttpExchange exchange, ApiService service) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (path.startsWith("/api/")) {
            byte[] bodyBytes;
            try (InputStream in = exchange.getRequestBody()) {
                bodyBytes = in.readNBytes(MAX_BODY_BYTES + 1);
            }
            if (bodyBytes.length > MAX_BODY_BYTES) {
                send(exchange, 413, errorBody("The request is too large"));
                return;
            }
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            ApiResponse response = service.handle(method, path, parseQuery(exchange.getRequestURI().getRawQuery()), body);
            send(exchange, response.status(), response.body());
            return;
        }

        if (path.equals("/favicon.ico")) {
            exchange.sendResponseHeaders(204, -1);    // no tab icon: answer "nothing here" quietly
            return;
        }

        String file = STATIC_FILES.get(path);
        // Vite emits content-hashed assets under /assets. Only allow a flat, safe filename;
        // never let a request traverse outside the packaged web resources.
        if (file == null && path.matches("/assets/[A-Za-z0-9._-]+")) {
            file = path.substring(1);
        }
        if (file == null && path.equals("/favicon.svg")) {
            file = "favicon.svg";
        }
        if (file == null) {
            send(exchange, 404, errorBody("Not found: " + path));
            return;
        }
        if (!method.equals("GET")) {
            send(exchange, 405, errorBody("Method " + method + " is not allowed here"));
            return;
        }
        try (InputStream in = RasoiServer.class.getResourceAsStream("/web/" + file)) {
            if (in == null) {
                send(exchange, 404, errorBody("The page asset " + file + " is missing from the project"));
                return;
            }
            byte[] bytes = in.readAllBytes();
            String contentType = file.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : file.endsWith(".css") ? "text/css; charset=utf-8"
                    : file.endsWith(".svg") ? "image/svg+xml"
                    : file.endsWith(".woff2") ? "font/woff2"
                    : file.endsWith(".png") ? "image/png"
                    : file.endsWith(".jpg") || file.endsWith(".jpeg") ? "image/jpeg"
                    : "application/octet-stream";
            if (file.equals("index.html")) contentType = "text/html; charset=utf-8";
            if (CONTENT_TYPES.containsKey(file)) contentType = CONTENT_TYPES.get(file);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", file.startsWith("assets/") ? "public, max-age=31536000, immutable" : "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    /** "a=1&b=x%20y" -> {a: "1", b: "x y"} */
    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return map;
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Last-resort answer; if even this fails (the connection is gone) there is nothing more to do. */
    private static void trySend(HttpExchange exchange, int status, Object body) {
        try {
            send(exchange, status, body);
        } catch (IOException | RuntimeException ignored) {
            // nothing more we can do
        }
    }
}
