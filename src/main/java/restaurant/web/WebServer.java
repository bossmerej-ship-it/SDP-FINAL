package restaurant.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import restaurant.RestaurantFacade;
import restaurant.api.ItemRequest;
import restaurant.api.MenuEntry;
import restaurant.api.OrderRequest;
import restaurant.api.OrderView;
import restaurant.meal.Burger;
import restaurant.meal.Cheese;
import restaurant.meal.Meal;
import restaurant.meal.Sauce;
import restaurant.order.OrderStatus;
import restaurant.order.ServiceType;

/** Loopback-only demo server. RestaurantFacade remains the single business entry point. */
public final class WebServer implements AutoCloseable {
    private static final int MAX_BODY = 32 * 1024;
    private static final String COOKIE = "restaurant_session";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ORDER_FIELDS = Set.of("customer", "serviceType", "tableNumber", "comment",
            "discount", "meal", "quantity", "cheese", "sauce");
    private final RestaurantFacade restaurant = new RestaurantFacade();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Path webRoot;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public WebServer(Path webRoot, int port) throws IOException {
        this.webRoot = java.util.Objects.requireNonNull(webRoot).toAbsolutePath().normalize();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    @Override public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        WebServer application = new WebServer(Path.of("web"), port);
        Runtime.getRuntime().addShutdownHook(new Thread(application::close));
        application.start();
        System.out.println("Restaurant: http://127.0.0.1:" + application.port());
        System.out.println("Press Ctrl+C to stop. Orders are held in memory until the server stops.");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Referrer-Policy", "same-origin");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; "
                    + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/api") || path.startsWith("/api/")) {
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    api(exchange, path);
                } else staticFile(exchange, path);
            } catch (ApiError error) {
                json(exchange, error.status, Json.object("error", error.getMessage()));
            } catch (IllegalArgumentException error) {
                json(exchange, 400, Json.object("error", "Проверьте параметры запроса."));
            } catch (RuntimeException error) {
                System.getLogger(WebServer.class.getName()).log(System.Logger.Level.ERROR, "HTTP request failed", error);
                json(exchange, 500, Json.object("error", "Не удалось выполнить запрос. Попробуйте ещё раз."));
            } catch (IOException error) {
                System.getLogger(WebServer.class.getName()).log(System.Logger.Level.ERROR, "HTTP I/O failed", error);
                throw error;
            }
        }
    }

    private void api(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/session")) {
            method(exchange, "GET");
            Session session = session(exchange);
            if (session == null) {
                String id = randomToken();
                session = new Session();
                sessions.put(id, session);
                exchange.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + id + "; HttpOnly; SameSite=Strict; Path=/");
            }
            json(exchange, 200, Json.object("csrfToken", session.csrf));
        } else if (path.equals("/api/menu")) {
            method(exchange, "GET");
            Meal base = new Burger();
            List<MenuEntry> toppings = List.of(
                    new MenuEntry("cheese", "Сыр", new Cheese(base).getPrice().subtract(base.getPrice())),
                    new MenuEntry("sauce", "Соус", new Sauce(base).getPrice().subtract(base.getPrice())));
            json(exchange, 200, Json.object("meals", restaurant.getMenu(), "toppings", toppings, "studentDiscountPercent", 10));
        } else if (path.equals("/api/kitchen")) {
            method(exchange, "GET");
            json(exchange, 200, restaurant.listOrders());
        } else if (path.equals("/api/orders/quote")) {
            method(exchange, "POST");
            authorize(exchange);
            json(exchange, 200, restaurant.quote(orderRequest(form(exchange))));
        } else if (path.equals("/api/orders")) {
            method(exchange, "GET", "POST");
            if (exchange.getRequestMethod().equals("GET")) {
                Session session = session(exchange);
                json(exchange, 200, session == null ? List.of() : restaurant.listOrders().stream()
                        .filter(order -> session.orders.contains(order.id())).toList());
            } else {
                Session session = authorize(exchange);
                String body = readBody(exchange);
                String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                if (key != null && !key.matches("[A-Za-z0-9._:-]{1,128}")) throw bad("Некорректный ключ повторной отправки.");
                synchronized (session) {
                    Replay replay = key == null ? null : session.replays.get(key);
                    if (replay != null) {
                        if (!replay.body.equals(body)) throw new ApiError(409, "Этот ключ отправки уже использован для другого заказа.");
                        json(exchange, 201, replay.order);
                        return;
                    }
                    OrderRequest request = orderRequest(parseForm(body));
                    OrderView order = restaurant.placeOrder(request);
                    session.orders.add(order.id());
                    if (key != null) {
                        session.replays.put(key, new Replay(body, order));
                        if (session.replays.size() > 100) session.replays.remove(session.replays.keySet().iterator().next());
                    }
                    json(exchange, 201, order);
                }
            }
        } else if (path.startsWith("/api/orders/")) {
            String[] parts = path.substring("/api/orders/".length()).split("/", -1);
            if (parts.length == 1 && !parts[0].isEmpty()) {
                method(exchange, "GET");
                Session session = session(exchange);
                if (session == null || !session.orders.contains(parts[0])) throw missing();
                json(exchange, 200, findOrder(parts[0]));
            } else if (parts.length == 2 && !parts[0].isEmpty() && parts[1].equals("status")) {
                method(exchange, "POST");
                authorize(exchange);
                Map<String, List<String>> form = form(exchange);
                if (!form.keySet().equals(Set.of("status"))) throw bad("Укажите новый статус заказа.");
                OrderStatus next;
                try { next = OrderStatus.valueOf(scalar(form, "status", true)); }
                catch (IllegalArgumentException error) { throw bad("Неизвестный статус заказа."); }
                findOrder(parts[0]);
                try { json(exchange, 200, restaurant.updateStatus(parts[0], next)); }
                catch (IllegalArgumentException error) { throw new ApiError(409, "Этот переход статуса недоступен. Обновите список заказов."); }
            } else throw missing();
        } else throw missing();
    }

    private OrderView findOrder(String id) {
        try { return restaurant.getOrder(id); }
        catch (IllegalArgumentException error) { throw missing(); }
    }

    private Session authorize(HttpExchange exchange) {
        Session session = session(exchange);
        String token = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
        if (session == null || token == null || !MessageDigest.isEqual(session.csrf.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiError(403, "Сессия устарела. Обновите страницу и повторите действие.");
        }
        List<String> origins = exchange.getRequestHeaders().get("Origin");
        if (origins != null && (origins.size() != 1 || !sameOrigin(exchange, origins.get(0)))) {
            throw new ApiError(403, "Запрос с другого сайта запрещён.");
        }
        return session;
    }

    private boolean sameOrigin(HttpExchange exchange, String value) {
        try {
            URI origin = URI.create(value);
            String host = origin.getHost();
            int originPort = origin.getPort() == -1 ? 80 : origin.getPort();
            return "http".equals(origin.getScheme()) && ("127.0.0.1".equals(host) || "localhost".equals(host))
                    && originPort == port() && origin.getRawUserInfo() == null
                    && (origin.getRawPath() == null || origin.getRawPath().isEmpty())
                    && origin.getRawQuery() == null && origin.getRawFragment() == null
                    && origin.getRawAuthority().equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Host"));
        } catch (IllegalArgumentException error) { return false; }
    }

    private Session session(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookieHeader : cookies) {
                for (String part : cookieHeader.split(";")) {
                    String cookie = part.strip();
                    if (cookie.startsWith(COOKIE + "=")) return sessions.get(cookie.substring(COOKIE.length() + 1));
                }
            }
        }
        return null;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Map<String, List<String>> form(HttpExchange exchange) throws IOException { return parseForm(readBody(exchange)); }

    private String readBody(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].strip().equalsIgnoreCase("application/x-www-form-urlencoded")) {
            throw new ApiError(415, "Используйте форму application/x-www-form-urlencoded.");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) throw new ApiError(413, "Заказ слишком большой. Максимальный размер запроса — 32 КБ.");
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException error) { throw bad("Используйте кодировку UTF-8."); }
    }

    private static Map<String, List<String>> parseForm(String body) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (body.isEmpty()) return values;
        String[] fields = body.split("&", -1);
        if (fields.length > 130) throw bad("В заказе слишком много полей.");
        for (String field : fields) {
            String[] pair = field.split("=", 2);
            try {
                String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            } catch (IllegalArgumentException error) { throw bad("Некорректная кодировка полей формы."); }
        }
        return values;
    }

    private OrderRequest orderRequest(Map<String, List<String>> form) {
        if (!ORDER_FIELDS.containsAll(form.keySet())) throw bad("В форме есть неизвестные поля.");
        String customer = scalar(form, "customer", true);
        if (customer.isBlank() || customer.length() > 100) throw bad("Укажите имя длиной от 1 до 100 символов.");
        String comment = scalar(form, "comment", false);
        if (comment.length() > 500) throw bad("Комментарий не должен превышать 500 символов.");
        ServiceType service;
        try { service = ServiceType.valueOf(scalar(form, "serviceType", true)); }
        catch (IllegalArgumentException error) { throw bad("Выберите обслуживание в зале или навынос."); }
        String tableValue = scalar(form, "tableNumber", false);
        Integer table = null;
        if (service == ServiceType.DINE_IN) table = integer(tableValue, 1, 9999, "Укажите номер стола от 1 до 9999.");
        else if (!tableValue.isBlank()) throw bad("Для заказа навынос номер стола не нужен.");
        String discount = scalar(form, "discount", true);
        if (!discount.equals("regular") && !discount.equals("student")) throw bad("Выберите обычную или студенческую цену.");
        List<String> meals = form.getOrDefault("meal", List.of());
        if (meals.isEmpty() || meals.size() > 30) throw bad("Добавьте от 1 до 30 позиций в заказ.");
        for (String field : List.of("quantity", "cheese", "sauce")) {
            if (form.getOrDefault(field, List.of()).size() != meals.size()) throw bad("У каждой позиции должны быть количество и добавки.");
        }
        Set<String> mealCodes = restaurant.getMenu().stream().map(MenuEntry::code).collect(java.util.stream.Collectors.toSet());
        List<ItemRequest> items = new ArrayList<>();
        for (int i = 0; i < meals.size(); i++) {
            String meal = meals.get(i);
            if (!mealCodes.contains(meal)) throw bad("Неизвестное блюдо. Обновите меню.");
            int quantity = integer(form.get("quantity").get(i), 1, 99, "Количество блюда должно быть от 1 до 99.");
            List<String> toppings = new ArrayList<>();
            for (String topping : List.of("cheese", "sauce")) {
                int count = integer(form.get(topping).get(i), 0, 20, "Количество каждой добавки должно быть от 0 до 20.");
                for (int j = 0; j < count; j++) toppings.add(topping);
            }
            items.add(new ItemRequest(meal, toppings, quantity));
        }
        return new OrderRequest(customer, service, table, comment, discount, items);
    }

    private static int integer(String value, int min, int max, String message) {
        if (!value.matches("[0-9]{1,9}")) throw bad(message);
        int result = Integer.parseInt(value);
        if (result < min || result > max) throw bad(message);
        return result;
    }

    private static String scalar(Map<String, List<String>> form, String name, boolean required) {
        List<String> values = form.get(name);
        if (values == null && !required) return "";
        if (values == null || values.size() != 1) throw bad("Проверьте поле «" + name + "»: оно должно быть указано один раз.");
        return values.get(0);
    }

    private void staticFile(HttpExchange exchange, String path) throws IOException {
        method(exchange, "GET");
        String relative = switch (path) {
            case "/" -> "index.html";
            case "/orders" -> "orders.html";
            case "/kitchen" -> "kitchen.html";
            default -> path.startsWith("/assets/") ? path.substring(1) : null;
        };
        if (relative == null || relative.indexOf('\\') >= 0 || relative.chars().anyMatch(c -> c < 32)
                || List.of(relative.split("/", -1)).stream().anyMatch(part -> part.equals(".") || part.equals(".."))) throw missing();
        try {
            Path file = webRoot.resolve(relative).normalize();
            if (!file.startsWith(webRoot) || !safeFile(file)) throw missing();
            exchange.getResponseHeaders().set("Content-Type", mime(file));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, Files.size(file));
            Files.copy(file, exchange.getResponseBody());
        } catch (InvalidPathException error) { throw missing(); }
    }

    private boolean safeFile(Path file) {
        Path current = webRoot;
        try {
            // Reject links and Windows reparse points under the trusted web root.
            // Reading each component also works in restricted Windows workspaces,
            // where canonicalizing all filesystem ancestors can be denied.
            for (Path part : webRoot.relativize(file)) {
                current = current.resolve(part);
                BasicFileAttributes attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther()) return false;
            }
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException error) { return false; }
    }

    private static String mime(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return switch (dot < 0 ? "" : name.substring(dot + 1)) {
            case "html" -> "text/html; charset=UTF-8";
            case "css" -> "text/css; charset=UTF-8";
            case "js" -> "text/javascript; charset=UTF-8";
            case "json" -> "application/json; charset=UTF-8";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "svg" -> "image/svg+xml";
            case "webp" -> "image/webp";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
    }

    private static void method(HttpExchange exchange, String... allowed) {
        if (!List.of(allowed).contains(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", String.join(", ", allowed));
            throw new ApiError(405, "Этот HTTP-метод не поддерживается.");
        }
    }

    private static void json(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.encode(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(status, -1);
        else {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static ApiError bad(String message) { return new ApiError(400, message); }
    private static ApiError missing() { return new ApiError(404, "Страница или заказ не найдены."); }
    private static final class ApiError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;
        ApiError(int status, String message) { super(message); this.status = status; }
    }
    private static final class Session {
        private final String csrf = randomToken();
        private final Set<String> orders = ConcurrentHashMap.newKeySet();
        private final LinkedHashMap<String, Replay> replays = new LinkedHashMap<>();
    }
    private record Replay(String body, OrderView order) { }
}
