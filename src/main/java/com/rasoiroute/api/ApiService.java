package com.rasoiroute.api;

import com.rasoiroute.dispatch.DispatchPlan;
import com.rasoiroute.dispatch.DispatchPlanner;
import com.rasoiroute.dispatch.OrderBook;
import com.rasoiroute.dispatch.OrderGenerator;
import com.rasoiroute.dispatch.SubmitResult;
import com.rasoiroute.geo.LocalityDirectory;
import com.rasoiroute.geo.Location;
import com.rasoiroute.model.Fulfilment;
import com.rasoiroute.model.Order;
import com.rasoiroute.model.OrderType;
import com.rasoiroute.stock.FamilyRecipe;
import com.rasoiroute.stock.Ingredient;
import com.rasoiroute.stock.StockBook;
import com.rasoiroute.stock.StockCatalog;
import com.rasoiroute.stock.StockLevels;
import com.rasoiroute.stock.StockPlanner;
import com.rasoiroute.urgency.ScoredOrder;
import com.rasoiroute.urgency.UrgencyRanker;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * All the rules of the web API, with NO web-server code in it: it takes a method, a path, the
 * query values and the body text, and returns an {@link ApiResponse}. Because there is no network
 * here, every rule can be tested quickly and exactly.
 *
 * Endpoints:
 *   GET    /api/health           is the server alive, and how many orders it holds
 *   GET    /api/localities       the kitchen and the places we can deliver to
 *   GET    /api/orders           saved orders with urgency numbers, most urgent first (?date= ?now=)
 *   POST   /api/orders           save one order (repeats are refused with 409)
 *   DELETE /api/orders           remove all orders
 *   POST   /api/demo/generate    replace all orders with pretend ones (?count= ?days= ?dupRate= ?seed=)
 *   GET    /api/plan             the plan for one day (?date= ?now= ?k=)
 *   GET    /api/stock            the kitchen shelf against the booked orders (?date= ?days= ?now=)
 *   POST   /api/stock/adjust     "I bought more" / "Used some" / a counted amount
 *   POST   /api/stock/reset      back to the starting amounts
 *   POST   /api/orders/check     can the kitchen make a new order of this product and size?
 *   POST   /api/orders/made      mark an order as made: its ingredients come off the shelf
 */
public final class ApiService {

    static final int MAX_DEMO_ORDERS = 5000;

    private final LocalityDirectory directory;
    private final OrderBook book;
    private final Clock clock;
    private final DispatchPlanner planner;
    private final OrderGenerator generator;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final StockBook stock;

    /** Which product family and how many units an order is for (only orders that gave this are tracked). */
    private record StockLink(String familyId, double quantity, boolean made) {
    }

    // order id -> its stock link. All access is inside synchronized (links).
    private final Map<String, StockLink> links = new LinkedHashMap<>();

    public ApiService(LocalityDirectory directory, OrderBook book, Clock clock) {
        this(directory, book, clock, new StockBook(StockCatalog.loadDefault()));
    }

    public ApiService(LocalityDirectory directory, OrderBook book, Clock clock, StockBook stock) {
        if (directory == null || book == null || clock == null || stock == null) {
            throw new IllegalArgumentException("directory, order book, clock and stock are required");
        }
        this.stock = stock;
        this.directory = directory;
        this.book = book;
        this.clock = clock;
        this.planner = new DispatchPlanner(directory);
        this.generator = new OrderGenerator(directory);
    }

    // =====================================================================
    // Entry point
    // =====================================================================

    public ApiResponse handle(String method, String path, Map<String, String> query, String body) {
        try {
            return route(method, path, query == null ? Map.of() : query, body);
        } catch (ApiException e) {
            return error(e.status(), e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        } catch (RuntimeException e) {
            // A bug on our side. The person gets a plain message, not the technical details.
            return error(500, "Something went wrong on the server");
        }
    }

    private ApiResponse route(String method, String path, Map<String, String> query, String body) {
        switch (path) {
            case "/api/health":
                requireMethod(method, "GET");
                return health();
            case "/api/localities":
                requireMethod(method, "GET");
                return localities();
            case "/api/orders":
                requireMethod(method, "GET", "POST", "DELETE");
                if (method.equals("GET")) {
                    return listOrders(query);
                }
                if (method.equals("POST")) {
                    return createOrder(body);
                }
                return clearOrders();
            case "/api/demo/generate":
                requireMethod(method, "POST");
                return generateDemo(query);
            case "/api/plan":
                requireMethod(method, "GET");
                return plan(query);
            case "/api/stock":
                requireMethod(method, "GET");
                return stockReport(query);
            case "/api/stock/adjust":
                requireMethod(method, "POST");
                return adjustStock(body);
            case "/api/stock/reset":
                requireMethod(method, "POST");
                return resetStock();
            case "/api/orders/check":
                requireMethod(method, "POST");
                return checkOrder(body, query);
            case "/api/orders/made":
                requireMethod(method, "POST");
                return markMade(body);
            default:
                throw new ApiException(404, "Not found: " + path);
        }
    }

    private static void requireMethod(String actual, String... allowed) {
        if (!Arrays.asList(allowed).contains(actual)) {
            throw new ApiException(405, "Method " + actual + " is not allowed here (use " + String.join(" or ", allowed) + ")");
        }
    }

    private static ApiResponse ok(Object body) {
        return new ApiResponse(200, body);
    }

    private static ApiResponse error(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return new ApiResponse(status, m);
    }

    // =====================================================================
    // Endpoints
    // =====================================================================

    private ApiResponse health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok");
        m.put("orders", book.size());
        return ok(m);
    }

    private ApiResponse localities() {
        List<Location> places = new ArrayList<>();
        for (String name : directory.names()) {
            Location place = directory.find(name);
            if (!place.name().equalsIgnoreCase(DispatchPlanner.KITCHEN)) {
                places.add(place);
            }
        }
        places.sort(Comparator.comparing(Location::name));

        List<Object> list = new ArrayList<>();
        for (Location place : places) {
            list.add(locationMap(place));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kitchen", locationMap(directory.find(DispatchPlanner.KITCHEN)));
        m.put("localities", list);
        return ok(m);
    }

    private static Map<String, Object> locationMap(Location place) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", place.name());
        m.put("latitude", place.latitude());
        m.put("longitude", place.longitude());
        return m;
    }

    private ApiResponse listOrders(Map<String, String> query) {
        LocalDateTime now = parseNow(query);
        List<Order> orders = book.orders();
        if (query.containsKey("date")) {
            LocalDate date = parseDate(query.get("date"), "date");
            List<Order> kept = new ArrayList<>();
            for (Order o : orders) {
                if (o.dueAt().toLocalDate().equals(date)) {
                    kept.add(o);
                }
            }
            orders = kept;
        }
        List<ScoredOrder> scored = UrgencyRanker.scoreAll(orders, now);
        scored.sort(UrgencyRanker.MOST_URGENT_FIRST);

        List<Object> list = new ArrayList<>();
        for (ScoredOrder s : scored) {
            Map<String, Object> item = ApiMapper.scored(s);
            StockLink link;
            synchronized (links) {
                link = links.get(s.order().id());
            }
            if (link != null) {
                FamilyRecipe family = stock.catalog().family(link.familyId());
                item.put("family", family.label());
                item.put("familyId", family.id());
                item.put("quantity", link.quantity());
                item.put("familyUnit", family.unit());
                item.put("made", link.made());
            }
            list.add(item);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", list.size());
        m.put("orders", list);
        m.put("bloomHits", book.bloomHits());
        m.put("repeatsBlocked", book.confirmedDuplicates());
        m.put("falseAlarms", book.falseAlarms());
        return ok(m);
    }

    private ApiResponse createOrder(String body) {
        Object parsed = Json.parse(body);                 // IllegalArgumentException -> 400
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new ApiException(400, "The request body must be a JSON object, like {\"item\": \"Cake\"}");
        }
        Order order = toOrder(raw);
        StockLink link = toStockLink(raw);
        SubmitResult result = book.submit(order);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accepted", result.accepted());
        m.put("message", result.message());
        if (result.accepted()) {
            if (link != null) {
                synchronized (links) {
                    links.put(order.id(), link);
                }
            }
            m.put("order", ApiMapper.order(order));
            return new ApiResponse(201, m);
        }
        m.put("duplicateOfId", result.duplicateOfId());
        return new ApiResponse(409, m);
    }

    private ApiResponse clearOrders() {
        book.clear();
        synchronized (links) {
            links.clear();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cleared", true);
        return ok(m);
    }

    private ApiResponse generateDemo(Map<String, String> query) {
        int count = intParam(query, "count", 100, 1, MAX_DEMO_ORDERS);
        int days = intParam(query, "days", 7, 1, 30);
        double duplicateRate = doubleParam(query, "dupRate", 0.1, 0.0, 1.0);
        long seed = longParam(query, "seed", 42L);

        book.clear();
        synchronized (links) {
            links.clear();
        }
        List<Order> generated = generator.generate(count, LocalDate.now(clock), days, duplicateRate, new Random(seed));
        int accepted = 0;
        int repeats = 0;
        int other = 0;
        for (Order order : generated) {
            SubmitResult result = book.submit(order);
            if (result.accepted()) {
                accepted++;
            } else if (result.duplicateOfId() != null) {
                repeats++;
            } else {
                other++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generated", generated.size());
        m.put("accepted", accepted);
        m.put("repeatsBlocked", repeats);
        m.put("otherRefused", other);
        m.put("bloomHits", book.bloomHits());
        m.put("falseAlarms", book.falseAlarms());
        return ok(m);
    }

    private ApiResponse plan(Map<String, String> query) {
        LocalDate day = query.containsKey("date") ? parseDate(query.get("date"), "date") : LocalDate.now(clock);
        LocalDateTime now = parseNow(query);
        int k = intParam(query, "k", 5, 0, 100);
        DispatchPlan plan = planner.plan(book.orders(), day, now, k, new Random());
        return ok(ApiMapper.plan(plan));
    }

    // =====================================================================
    // Stock
    // =====================================================================

    /** The orders that still have to be made (not marked made) and are due before the end of the window. */
    private List<StockPlanner.Need> openNeeds(LocalDateTime now, LocalDateTime windowEnd) {
        List<Order> open = new ArrayList<>();
        Map<String, StockLink> copy;
        synchronized (links) {
            copy = new LinkedHashMap<>(links);
        }
        for (Order o : book.orders()) {
            StockLink link = copy.get(o.id());
            if (link != null && !link.made() && o.dueAt().isBefore(windowEnd)) {
                open.add(o);
            }
        }
        List<StockPlanner.Need> needs = new ArrayList<>();
        for (ScoredOrder s : UrgencyRanker.scoreAll(open, now)) {
            StockLink link = copy.get(s.order().id());
            needs.add(new StockPlanner.Need(s.order().id(), s.score(), s.order().dueAt(), link.familyId(), link.quantity()));
        }
        return needs;
    }

    private ApiResponse stockReport(Map<String, String> query) {
        LocalDateTime now = parseNow(query);
        LocalDate from = query.containsKey("date") ? parseDate(query.get("date"), "date") : now.toLocalDate();
        int days = intParam(query, "days", 7, 1, 30);
        List<StockPlanner.Need> needs = openNeeds(now, from.plusDays(days).atStartOfDay());
        StockCatalog catalog = stock.catalog();
        StockPlanner.Report report = StockPlanner.report(catalog, stock.snapshot(), needs);

        Map<String, Order> byId = new LinkedHashMap<>();
        for (Order o : book.orders()) {
            byId.put(o.id(), o);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from.toString());
        m.put("days", days);
        m.put("linkedOrders", needs.size());

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("ok", report.okCount());
        counts.put("low", report.lowCount());
        counts.put("short", report.shortCount());
        counts.put("out", report.outCount());
        m.put("counts", counts);

        List<Object> rows = new ArrayList<>();
        for (StockPlanner.Row r : report.rows()) {
            Map<String, Object> row = ingredientMap(r.ingredient());
            row.put("onHand", ApiMapper.round3(r.onHand()));
            row.put("committed", ApiMapper.round3(r.committed()));
            row.put("available", ApiMapper.round3(r.available()));
            row.put("lowLevel", r.ingredient().lowLevel());
            row.put("level", r.level().name());
            rows.add(row);
        }
        m.put("rows", rows);

        List<Object> shopping = new ArrayList<>();
        for (StockLevels.ShoppingLine line : report.shopping()) {
            Map<String, Object> s = ingredientMap(line.ingredient());
            s.put("onHand", ApiMapper.round3(line.onHand()));
            s.put("committed", ApiMapper.round3(line.committed()));
            s.put("toBuy", ApiMapper.round3(line.toBuy()));
            s.put("packs", line.packs());
            s.put("buyAmount", ApiMapper.round3(line.buyAmount()));
            s.put("reason", line.reason());
            shopping.add(s);
        }
        m.put("shopping", shopping);

        List<Object> atRisk = new ArrayList<>();
        for (StockPlanner.AtRisk risk : report.atRisk()) {
            Map<String, Object> a = new LinkedHashMap<>();
            Order o = byId.get(risk.orderId());
            a.put("orderId", risk.orderId());
            a.put("item", o == null ? "" : o.item());
            a.put("customerName", o == null ? "" : o.customerName());
            a.put("dueAt", o == null ? null : o.dueAt().toString());
            List<Object> missing = new ArrayList<>();
            for (Map.Entry<String, Double> e : risk.missing().entrySet()) {
                Ingredient ing = catalog.ingredient(e.getKey());
                Map<String, Object> line = ingredientMap(ing);
                line.put("amount", ApiMapper.round3(e.getValue()));
                missing.add(line);
            }
            a.put("missing", missing);
            atRisk.add(a);
        }
        m.put("atRisk", atRisk);
        m.put("urgentFirstServed", report.urgentFirstServed());
        m.put("bookingOrderServed", report.bookingOrderServed());

        List<Object> families = new ArrayList<>();
        for (FamilyRecipe f : catalog.families().values()) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("id", f.id());
            fm.put("label", f.label());
            fm.put("type", f.type().name());
            fm.put("unit", f.unit());
            fm.put("minQty", f.minQty());
            fm.put("maxQty", f.maxQty());
            families.add(fm);
        }
        m.put("families", families);
        return ok(m);
    }

    private static Map<String, Object> ingredientMap(Ingredient ing) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ing.id());
        m.put("name", ing.name());
        m.put("unit", ing.unit());
        m.put("group", ing.group());
        m.put("packSize", ing.packSize());
        return m;
    }

    private ApiResponse adjustStock(String body) {
        Map<?, ?> raw = jsonObject(body);
        String id = requiredText(raw, "ingredientId");
        boolean hasChange = raw.get("change") != null;
        boolean hasSet = raw.get("set") != null;
        if (hasChange == hasSet) {
            throw new ApiException(400, "Give either change (to add or take away) or set (a counted amount), not both");
        }
        double newAmount = hasChange ? stock.adjust(id, number(raw, "change")) : stock.set(id, number(raw, "set"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ingredientId", stock.catalog().ingredient(id).id());
        m.put("onHand", ApiMapper.round3(newAmount));
        return ok(m);
    }

    private ApiResponse resetStock() {
        stock.reset();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reset", true);
        return ok(m);
    }

    private ApiResponse checkOrder(String body, Map<String, String> query) {
        Map<?, ?> raw = jsonObject(body);
        String family = requiredText(raw, "family");
        double quantity = number(raw, "quantity");
        LocalDateTime now = parseNow(query);
        StockCatalog catalog = stock.catalog();
        FamilyRecipe recipe = catalog.family(family);
        // Everything not yet made counts against the shelf, whenever it is due.
        List<StockPlanner.Need> needs = openNeeds(now, LocalDateTime.MAX);
        StockPlanner.Check check = StockPlanner.check(catalog, stock.snapshot(), needs, family, quantity);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("family", recipe.label());
        m.put("quantity", quantity);
        m.put("unit", recipe.unit());
        m.put("possible", check.possible());
        List<Object> lines = new ArrayList<>();
        List<String> shortOn = new ArrayList<>();
        for (StockPlanner.CheckLine l : check.lines()) {
            Map<String, Object> line = ingredientMap(l.ingredient());
            line.put("use", ApiMapper.round3(l.use()));
            line.put("availableBefore", ApiMapper.round3(l.availableBefore()));
            line.put("availableAfter", ApiMapper.round3(l.availableAfter()));
            lines.add(line);
            if (l.availableAfter() < -1e-9) {
                shortOn.add(l.ingredient().name() + " by " + readable(-l.availableAfter(), l.ingredient().unit()));
            }
        }
        m.put("lines", lines);
        m.put("unitsStillPossible", check.unitsStillPossible());
        m.put("limitedBy", check.limitedBy());
        m.put("message", check.possible()
                ? "Yes, the kitchen has enough for this order."
                : "Not enough in stock. Short on " + String.join(", ", shortOn) + ".");
        return ok(m);
    }

    private ApiResponse markMade(String body) {
        Map<?, ?> raw = jsonObject(body);
        String id = requiredText(raw, "id");
        StockLink link;
        synchronized (links) {
            link = links.get(id);
            if (link == null) {
                throw new ApiException(400, "Order " + id + " has no product and quantity, so there is nothing to take out of stock");
            }
            if (link.made()) {
                throw new ApiException(409, "Order " + id + " is already marked as made");
            }
            links.put(id, new StockLink(link.familyId(), link.quantity(), true));
        }
        Map<String, Double> used = stock.catalog().family(link.familyId()).requirementFor(link.quantity());
        stock.use(used);
        List<Object> list = new ArrayList<>();
        for (Map.Entry<String, Double> e : used.entrySet()) {
            Map<String, Object> line = ingredientMap(stock.catalog().ingredient(e.getKey()));
            line.put("amount", ApiMapper.round3(e.getValue()));
            list.add(line);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("made", true);
        m.put("used", list);
        return ok(m);
    }

    /** Reads the optional "family" and "quantity" of an order. Both or neither must be given. */
    private StockLink toStockLink(Map<?, ?> raw) {
        String family = optionalText(raw, "family");
        Object quantity = raw.get("quantity");
        if (family == null && quantity == null) {
            return null;
        }
        if (family == null || quantity == null) {
            throw new ApiException(400, "family and quantity go together: give both or neither");
        }
        double q = number(raw, "quantity");
        if (q > 10_000) {
            throw new ApiException(400, "quantity is too large");
        }
        stock.catalog().family(family).requirementFor(q);     // rejects an unknown product or a quantity of 0 or less
        return new StockLink(stock.catalog().family(family).id(), q, false);
    }

    /** 1000 g reads as "1 kg" and 1000 ml as "1 L", the way people say it in the kitchen. */
    private static String readable(double amount, String unit) {
        double value = amount;
        String shown = unit;
        if (unit.equals("g") && amount >= 1000) {
            value = amount / 1000.0;
            shown = "kg";
        } else if (unit.equals("ml") && amount >= 1000) {
            value = amount / 1000.0;
            shown = "L";
        }
        double rounded = Math.round(value * 100.0) / 100.0;
        String text = rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
        return text + " " + shown;
    }

    private static Map<?, ?> jsonObject(String body) {
        Object parsed = Json.parse(body);
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new ApiException(400, "The request body must be a JSON object");
        }
        return raw;
    }

    private static double number(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (!(value instanceof Number n)) {
            throw new ApiException(400, key + " must be a number");
        }
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new ApiException(400, key + " must be a number");
        }
        return d;
    }

    // =====================================================================
    // Reading an order from the request
    // =====================================================================

    private Order toOrder(Map<?, ?> raw) {
        LocalDateTime now = LocalDateTime.now(clock);

        String id = optionalText(raw, "id");
        if (id == null) {
            id = String.format("W-%05d", nextId.getAndIncrement());
        }
        String customerName = requiredText(raw, "customerName");
        String phone = requiredText(raw, "phone");
        String item = requiredText(raw, "item");
        OrderType type = parseEnum(OrderType.class, requiredText(raw, "type"), "type");
        Fulfilment fulfilment = parseEnum(Fulfilment.class, requiredText(raw, "fulfilment"), "fulfilment");
        LocalDateTime dueAt = parseDateTime(requiredText(raw, "dueAt"), "dueAt");
        String bookedText = optionalText(raw, "bookedAt");
        LocalDateTime bookedAt = bookedText == null ? now : parseDateTime(bookedText, "bookedAt");

        String locality = null;
        if (fulfilment == Fulfilment.DELIVERY) {
            locality = optionalText(raw, "locality");
            if (locality == null) {
                throw new ApiException(400, "locality is required for a delivery");
            }
            if (!directory.contains(locality)) {
                throw new ApiException(400, "Unknown locality: " + locality + " (choose one from the list of places)");
            }
        }
        // The Order itself re-checks everything (phone digits, due not before booked ...).
        return new Order(id, customerName, phone, item, type, fulfilment, bookedAt, dueAt, locality);
    }

    private static String optionalText(Map<?, ?> raw, String key) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ApiException(400, key + " must be text");
        }
        return text.isBlank() ? null : text.trim();
    }

    private static String requiredText(Map<?, ?> raw, String key) {
        String text = optionalText(raw, key);
        if (text == null) {
            throw new ApiException(400, key + " is required");
        }
        return text;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String text, String field) {
        String wanted = text.trim().toUpperCase().replace(' ', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(wanted)) {
                return constant;
            }
        }
        throw new ApiException(400, field + " must be one of " + Arrays.toString(type.getEnumConstants()));
    }

    private static LocalDateTime parseDateTime(String text, String field) {
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new ApiException(400, field + " must look like 2026-11-19T16:00");
        }
    }

    // =====================================================================
    // Reading values from the address (?date=..&k=..)
    // =====================================================================

    private LocalDateTime parseNow(Map<String, String> query) {
        String text = query.get("now");
        return text == null || text.isBlank() ? LocalDateTime.now(clock) : parseDateTime(text.trim(), "now");
    }

    private static LocalDate parseDate(String text, String field) {
        try {
            return LocalDate.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, field + " must look like 2026-11-19");
        }
    }

    private static int intParam(Map<String, String> query, String name, int fallback, int min, int max) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, name + " must be a whole number");
        }
        if (value < min || value > max) {
            throw new ApiException(400, name + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static long longParam(Map<String, String> query, String name, long fallback) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, name + " must be a whole number");
        }
    }

    private static double doubleParam(Map<String, String> query, String name, double fallback, double min, double max) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, name + " must be a number");
        }
        if (!(value >= min && value <= max)) {        // written this way so NaN is rejected too
            throw new ApiException(400, name + " must be between " + min + " and " + max);
        }
        return value;
    }
}
