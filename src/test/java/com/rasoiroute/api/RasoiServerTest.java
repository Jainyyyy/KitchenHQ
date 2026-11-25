package com.rasoiroute.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.dispatch.OrderBook;
import com.rasoiroute.geo.LocalityDirectory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** These tests start the REAL server on a free port and talk to it over real HTTP, exactly as the browser does. */
class RasoiServerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

    private interface Check {
        void run(HttpClient client, String base) throws Exception;
    }

    private static void withServer(Check check) throws Exception {
        ApiService service = new ApiService(LocalityDirectory.loadDefault(), new OrderBook(1000, 0.01), CLOCK);
        HttpServer server = RasoiServer.start(0, service);
        try {
            check.run(HttpClient.newHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort());
        } finally {
            RasoiServer.stop(server);
        }
    }

    private static HttpResponse<String> send(HttpClient client, String method, String url, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).method(method, publisher).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void theStreetMapLibraryFilesAreServedFromOurOwnServer() throws Exception {
        withServer((client, base) -> {
            HttpResponse<String> script = send(client, "GET", base + "/leaflet.js", null);
            assertEquals(200, script.statusCode());
            assertTrue(script.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"));
            HttpResponse<String> css = send(client, "GET", base + "/leaflet.css", null);
            assertEquals(200, css.statusCode());
            assertTrue(css.headers().firstValue("Content-Type").orElse("").startsWith("text/css"));
        });
    }

    @Test
    void healthAnswersOverRealHttpAsJson() throws Exception {
        withServer((client, base) -> {
            HttpResponse<String> response = send(client, "GET", base + "/api/health", null);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
            assertEquals("ok", ((Map<?, ?>) Json.parse(response.body())).get("status"));
        });
    }

    @Test
    void anOrderCanBeSavedThenFoundThenARepeatIsRefused() throws Exception {
        String order = "{\"customerName\":\"Meera\",\"phone\":\"9876543210\",\"item\":\"Cake\",\"type\":\"BAKERY\","
                + "\"fulfilment\":\"DELIVERY\",\"locality\":\"Satellite\",\"dueAt\":\"2026-11-19T16:00\"}";
        withServer((client, base) -> {
            assertEquals(201, send(client, "POST", base + "/api/orders", order).statusCode());
            assertEquals(409, send(client, "POST", base + "/api/orders", order).statusCode());
            HttpResponse<String> list = send(client, "GET", base + "/api/orders", null);
            assertEquals(1.0, (Double) ((Map<?, ?>) Json.parse(list.body())).get("count"), 1e-12);
        });
    }

    @Test
    void queryValuesInTheAddressAreReadAndDecoded() throws Exception {
        withServer((client, base) -> {
            HttpResponse<String> response = send(client, "POST", base + "/api/demo/generate?count=50&days=2&seed=3", null);
            assertEquals(200, response.statusCode());
            assertEquals(50.0, (Double) ((Map<?, ?>) Json.parse(response.body())).get("generated"), 1e-12);
            assertEquals(400, send(client, "GET", base + "/api/plan?date=not%20a%20date", null).statusCode());
        });
    }

    @Test
    void badJsonGetsA400AndAnUnknownPathGetsA404() throws Exception {
        withServer((client, base) -> {
            assertEquals(400, send(client, "POST", base + "/api/orders", "{broken").statusCode());
            assertEquals(404, send(client, "GET", base + "/api/nothing", null).statusCode());
            assertEquals(405, send(client, "DELETE", base + "/api/health", null).statusCode());
        });
    }

    @Test
    void anOversizedRequestIsRefused() throws Exception {
        withServer((client, base) -> {
            String huge = "{\"item\":\"" + "x".repeat(RasoiServer.MAX_BODY_BYTES + 10) + "\"}";
            assertEquals(413, send(client, "POST", base + "/api/orders", huge).statusCode());
        });
    }

    @Test
    void theStockEndpointsWorkOverARealConnection() throws Exception {
        withServer((client, base) -> {
            HttpResponse<String> stock = send(client, "GET", base + "/api/stock", null);
            assertEquals(200, stock.statusCode());
            assertTrue(stock.body().contains("\"rows\""));

            HttpResponse<String> check = send(client, "POST", base + "/api/orders/check", "{\"family\":\"choc-cake\",\"quantity\":2}");
            assertEquals(200, check.statusCode());
            assertTrue(check.body().contains("\"possible\":true"));

            assertEquals(400, send(client, "POST", base + "/api/orders/check", "{\"family\":\"pizza\",\"quantity\":2}").statusCode());
        });
    }

    @Test
    void theThreeFrontendFilesAreServed() throws Exception {
        withServer((client, base) -> {
            HttpResponse<String> page = send(client, "GET", base + "/", null);
            assertEquals(200, page.statusCode());
            assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
            assertTrue(page.body().contains("KitchenHQ"));
            assertTrue(page.body().contains("<script"));

            HttpResponse<String> script = send(client, "GET", base + "/app.js", null);
            assertEquals(200, script.statusCode());
            assertTrue(script.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"));

            HttpResponse<String> style = send(client, "GET", base + "/style.css", null);
            assertEquals(200, style.statusCode());
            assertTrue(style.headers().firstValue("Content-Type").orElse("").startsWith("text/css"));
        });
    }

    @Test
    void noOtherFileCanBeAskedFor() throws Exception {
        withServer((client, base) -> {
            assertEquals(404, send(client, "GET", base + "/localities.csv", null).statusCode());
            assertEquals(404, send(client, "GET", base + "/pom.xml", null).statusCode());
            assertEquals(404, send(client, "GET", base + "/web/index.html", null).statusCode());
            assertEquals(404, send(client, "GET", base + "/%2e%2e/pom.xml", null).statusCode());
            assertEquals(405, send(client, "POST", base + "/", "x").statusCode());
            assertEquals(204, send(client, "GET", base + "/favicon.ico", null).statusCode());
        });
    }

    @Test
    void queryParserHandlesEmptyAndEncodedValues() throws IOException {
        assertEquals(Map.of(), RasoiServer.parseQuery(null));
        assertEquals(Map.of(), RasoiServer.parseQuery(""));
        assertEquals(Map.of("a", "1", "b", "x y", "c", ""), RasoiServer.parseQuery("a=1&b=x%20y&c"));
        assertEquals(Map.of("now", "2026-11-18T12:00"), RasoiServer.parseQuery("now=2026-11-18T12%3A00"));
    }
}
