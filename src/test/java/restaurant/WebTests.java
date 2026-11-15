package restaurant;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import restaurant.web.WebServer;

/** Real HTTP integration tests. No third-party test or JSON library is required. */
public final class WebTests {
    private static int passed;
    private static final String VALID = "customer=Aida&serviceType=TAKEAWAY&tableNumber=&comment="
            + "&discount=student&meal=burger&quantity=1&cheese=1&sauce=1";

    public static void main(String[] args) throws Exception {
        Path fixtureParent = Path.of("out").toAbsolutePath();
        Files.createDirectories(fixtureParent);
        Path root = Files.createTempDirectory(fixtureParent, "restaurant-web-tests-");
        Files.createDirectories(root.resolve("assets"));
        Files.writeString(root.resolve("index.html"), "<h1>Menu fixture</h1>");
        Files.writeString(root.resolve("orders.html"), "<h1>Orders fixture</h1>");
        Files.writeString(root.resolve("kitchen.html"), "<h1>Kitchen fixture</h1>");
        Files.writeString(root.resolve("assets/app.css"), "body { color: black; }");
        Files.writeString(root.resolve("assets/app.js"), "console.log('fixture');");
        Files.write(root.resolve("assets/meal.png"), new byte[] {(byte) 137, 80, 78, 71});
        try (WebServer server = new WebServer(root, 0)) {
            server.start();
            Client first = new Client(server.port());
            Client second = new Client(server.port());
            check("Static pages, MIME types, traversal and methods", () -> {
                contains(first.get("/").body(), "Menu fixture");
                contains(first.get("/orders").body(), "Orders fixture");
                contains(first.get("/kitchen").body(), "Kitchen fixture");
                contentType(first.get("/assets/app.css"), "text/css");
                contentType(first.get("/assets/app.js"), "javascript");
                contentType(first.get("/assets/meal.png"), "image/png");
                status(404, first.get("/assets/"));
                status(404, first.get("/assets/missing.css"));
                status(404, first.get("/assets/../index.html"));
                status(404, first.get("/assets/%2e%2e/index.html"));
                status(404, first.get("/assets/%5c..%5cindex.html"));
                status(405, first.send("DELETE", "/", "", null, null, null));
                status(405, first.send("POST", "/api/menu", "", null, null, null));
                var ordersMethod = first.send("DELETE", "/api/orders", "", null, null, null);
                status(405, ordersMethod);
                equal("GET, POST", ordersMethod.headers().firstValue("Allow").orElse(""));
            });
            check("Menu uses domain meal and decorator prices", () -> {
                var response = first.get("/api/menu");
                status(200, response);
                contains(response.body(), "\"code\":\"burger\"");
                contains(response.body(), "\"price\":2500.00");
                contains(response.body(), "\"price\":3500.00");
                contains(response.body(), "\"price\":300.00");
                contains(response.body(), "\"price\":200.00");
                contains(response.body(), "\"studentDiscountPercent\":10");
            });
            check("Sessions issue a protected cookie and distinct CSRF tokens", () -> {
                var a = first.openSession();
                var b = second.openSession();
                contains(a.headers().firstValue("Set-Cookie").orElseThrow(), "HttpOnly");
                contains(a.headers().firstValue("Set-Cookie").orElseThrow(), "SameSite=Strict");
                contains(a.headers().firstValue("Set-Cookie").orElseThrow(), "Path=/");
                truth(!first.csrf.equals(second.csrf), "Sessions must use distinct random tokens");
                equal(first.csrf, field(first.get("/api/session").body(), "csrfToken"));
                status(200, b);
            });
            check("Quote is real 3000 to 2700 pricing without saving an order", () -> {
                var quote = first.post("/api/orders/quote", VALID);
                status(200, quote);
                contains(quote.body(), "\"subtotal\":3000.00");
                contains(quote.body(), "\"discount\":300.00");
                contains(quote.body(), "\"total\":2700.00");
                equal("[]", first.get("/api/kitchen").body());
            });
            check("CSRF, foreign Origin and unsupported body formats are rejected", () -> {
                status(403, first.send("POST", "/api/orders", VALID, null, null, null));
                status(403, first.send("POST", "/api/orders", VALID, second.csrf, null, null));
                status(403, first.send("POST", "/api/orders", VALID, first.csrf, "https://example.com", null));
                status(403, first.send("POST", "/api/orders", VALID, first.csrf, "null", null));
                status(415, first.send("POST", "/api/orders", "{}", first.csrf, null, "application/json"));
                Client noSession = new Client(server.port());
                status(403, noSession.send("POST", "/api/orders", VALID, first.csrf, null, null));
                equal("[]", first.get("/api/kitchen").body());
            });
            check("Invalid and oversized input never saves an order", () -> {
                List<String> invalid = List.of(
                        VALID.replace("meal=burger", "meal=unknown"),
                        VALID.replace("quantity=1", "quantity=0"),
                        VALID.replace("quantity=1", "quantity=100"),
                        VALID.replace("cheese=1", "cheese=21"),
                        VALID.replace("sauce=1", "sauce=-1"),
                        VALID.replace("sauce=1", "sauce=1.5"),
                        VALID.replace("customer=Aida", "customer="),
                        VALID.replace("customer=Aida", "customer=" + "a".repeat(101)),
                        VALID.replace("comment=", "comment=" + "a".repeat(501)),
                        VALID.replace("serviceType=TAKEAWAY", "serviceType=DINE_IN"),
                        VALID.replace("tableNumber=", "tableNumber=5"),
                        VALID.replace("serviceType=TAKEAWAY", "serviceType=DINE_IN").replace("tableNumber=", "tableNumber=10000"),
                        VALID.replace("discount=student", "discount=free"),
                        VALID + "&customer=Other",
                        VALID + "&meal=pizza",
                        VALID + "&topping=unknown",
                        VALID + "&comment=%XX",
                        "customer=Aida&serviceType=TAKEAWAY&discount=regular",
                        VALID + "&meal=burger&quantity=1&cheese=0&sauce=0".repeat(30));
                for (String body : invalid) status(400, first.post("/api/orders", body));
                status(413, first.post("/api/orders", "customer=" + "x".repeat(33000)));
                equal("[]", first.get("/api/kitchen").body());
            });
            final String[] orderId = {null};
            check("Creation returns a real order and sessions isolate customer reads", () -> {
                var created = first.send("POST", "/api/orders", VALID, first.csrf, first.base, null);
                status(201, created);
                orderId[0] = field(created.body(), "id");
                contains(created.body(), "\"status\":\"NEW\"");
                contains(created.body(), "\"unitPrice\":3000.00");
                contains(created.body(), "\"total\":2700.00");
                contains(first.get("/api/orders").body(), orderId[0]);
                equal("[]", second.get("/api/orders").body());
                status(404, second.get("/api/orders/" + orderId[0]));
                status(200, first.get("/api/orders/" + orderId[0]));
                contains(second.get("/api/kitchen").body(), orderId[0]);
            });
            check("Kitchen shares NEW, PREPARING and READY domain transitions", () -> {
                String path = "/api/orders/" + orderId[0] + "/status";
                status(409, second.post(path, "status=READY"));
                status(200, second.post(path, "status=PREPARING"));
                String snapshot = second.get("/api/kitchen").body();
                status(200, second.post(path, "status=PREPARING"));
                equal(snapshot, second.get("/api/kitchen").body());
                contains(first.get("/api/orders/" + orderId[0]).body(), "\"status\":\"PREPARING\"");
                status(200, second.post(path, "status=READY"));
                contains(first.get("/api/orders/" + orderId[0]).body(), "\"status\":\"READY\"");
                status(409, second.post(path, "status=NEW"));
                status(400, second.post(path, "status=garbage"));
                status(404, second.post("/api/orders/missing/status", "status=PREPARING"));
            });
            check("JSON safely escapes malicious customer strings and controls", () -> {
                String name = "Русский <script>\"\\\n\t\u0001";
                String body = VALID.replace("customer=Aida", "customer=" + URLEncoder.encode(name, StandardCharsets.UTF_8));
                var created = first.post("/api/orders", body);
                status(201, created);
                contains(created.body(), "Русский");
                contains(created.body(), "\\\"");
                contains(created.body(), "\\\\");
                contains(created.body(), "\\n");
                contains(created.body(), "\\t");
                contains(created.body(), "\\u0001");
                truth(created.body().chars().noneMatch(c -> c < 32), "JSON cannot contain literal control characters");
            });
            check("Dine-in metadata and repeated cart lines reach the real domain", () -> {
                String body = VALID.replace("serviceType=TAKEAWAY", "serviceType=DINE_IN")
                        .replace("tableNumber=", "tableNumber=7")
                        + "&meal=pizza&quantity=2&cheese=0&sauce=0";
                var response = second.post("/api/orders", body);
                status(201, response);
                contains(response.body(), "\"serviceType\":\"DINE_IN\"");
                contains(response.body(), "\"tableNumber\":7");
                contains(response.body(), "\"subtotal\":10000.00");
                contains(response.body(), "\"total\":9000.00");
            });
            check("Idempotency keys prevent racing retries and reject changed bodies", () -> {
                int before = countOrders(first.get("/api/kitchen").body());
                var pool = Executors.newFixedThreadPool(4);
                try {
                    List<Callable<HttpResponse<String>>> calls = new ArrayList<>();
                    for (int i = 0; i < 8; i++) calls.add(() -> first.idempotent("retry-1", VALID));
                    String id = null;
                    for (var result : pool.invokeAll(calls)) {
                        var response = result.get();
                        status(201, response);
                        String resultId = field(response.body(), "id");
                        if (id == null) id = resultId;
                        equal(id, resultId);
                    }
                    equal(before + 1, countOrders(first.get("/api/kitchen").body()));
                    status(409, first.idempotent("retry-1", VALID.replace("Aida", "Other")));
                    status(409, first.idempotent("retry-1", VALID.replace("customer=Aida", "customer=")));
                    equal(before + 1, countOrders(first.get("/api/kitchen").body()));
                    status(201, second.idempotent("retry-1", VALID));
                    equal(before + 2, countOrders(first.get("/api/kitchen").body()));
                } finally {
                    pool.shutdownNow();
                }
            });
        } finally {
            // Only remove this test's own temporary fixture files.
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("RESULT: " + passed + " HTTP tests passed");
    }

    private static int countOrders(String json) { return json.split("\\\"id\\\":", -1).length - 1; }
    private static String field(String json, String name) {
        var match = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        if (!match.find()) throw new AssertionError("Missing " + name + " in " + json);
        return match.group(1);
    }
    private static void check(String name, CheckedRunnable action) throws Exception {
        action.run(); passed++; System.out.println("PASS: " + name);
    }
    private static void status(int expected, HttpResponse<String> actual) {
        if (actual.statusCode() != expected) throw new AssertionError("Expected HTTP " + expected + ", got " + actual.statusCode() + ": " + actual.body());
    }
    private static void contentType(HttpResponse<String> response, String type) {
        status(200, response); contains(response.headers().firstValue("Content-Type").orElse(""), type);
    }
    private static void contains(String actual, String expected) {
        truth(actual.contains(expected), "Expected " + actual + " to contain " + expected);
    }
    private static void equal(Object expected, Object actual) {
        truth(expected.equals(actual), "Expected " + expected + ", got " + actual);
    }
    private static void truth(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }

    private static final class Client {
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        private final String base;
        private String cookie;
        private String csrf;
        Client(int port) { base = "http://127.0.0.1:" + port; }
        HttpResponse<String> openSession() throws Exception {
            var response = get("/api/session");
            status(200, response);
            cookie = response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            csrf = field(response.body(), "csrfToken");
            return response;
        }
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, "", null, null, null); }
        HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body, csrf, null, null); }
        HttpResponse<String> idempotent(String key, String body) throws Exception {
            return http.send(request("POST", "/api/orders", body, csrf, null, null)
                    .header("Idempotency-Key", key).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        HttpResponse<String> send(String method, String path, String body, String token, String origin, String type) throws Exception {
            return http.send(request(method, path, body, token, origin, type).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        private HttpRequest.Builder request(String method, String path, String body, String token, String origin, String type) {
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
            if (cookie != null) request.header("Cookie", cookie);
            if (token != null) request.header("X-CSRF-Token", token);
            if (origin != null) request.header("Origin", origin);
            if (method.equals("POST")) request.header("Content-Type", type == null ? "application/x-www-form-urlencoded;charset=UTF-8" : type);
            return request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
    }
}
