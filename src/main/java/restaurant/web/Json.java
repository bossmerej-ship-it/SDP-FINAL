package restaurant.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import restaurant.api.ItemView;
import restaurant.api.MenuEntry;
import restaurant.api.OrderView;
import restaurant.pricing.PriceBreakdown;

/** Small output-only serializer for the application's known immutable view types. */
final class Json {
    private Json() { }

    static Map<String, Object> object(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }

    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String string) return quote(string);
        if (value instanceof Enum<?> enumeration) return quote(enumeration.name());
        if (value instanceof BigDecimal number) return number.toPlainString();
        if (value instanceof Integer || value instanceof Long || value instanceof Boolean) return value.toString();
        if (value instanceof MenuEntry menu) return encode(object("code", menu.code(), "description", menu.description(), "price", menu.price()));
        if (value instanceof PriceBreakdown price) return encode(object("subtotal", price.subtotal(), "discount", price.discount(), "total", price.total()));
        if (value instanceof ItemView item) return encode(object("description", item.description(), "quantity", item.quantity(),
                "unitPrice", item.unitPrice(), "lineTotal", item.lineTotal()));
        if (value instanceof OrderView order) return encode(object("id", order.id(), "customer", order.customer(),
                "serviceType", order.serviceType(), "tableNumber", order.tableNumber(), "comment", order.comment(),
                "items", order.items(), "price", order.price(), "status", order.status()));
        if (value instanceof Map<?, ?> map) {
            var output = new java.util.StringJoiner(",", "{", "}");
            for (var entry : map.entrySet()) output.add(quote((String) entry.getKey()) + ":" + encode(entry.getValue()));
            return output.toString();
        }
        if (value instanceof Iterable<?> list) {
            var output = new java.util.StringJoiner(",", "[", "]");
            for (Object item : list) output.add(encode(item));
            return output.toString();
        }
        throw new IllegalArgumentException("Unsupported JSON output type: " + value.getClass().getName());
    }

    private static String quote(String text) {
        var output = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            switch (value) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (value < 32 || value == '<' || value == '>' || value == '&' || value == '\u2028' || value == '\u2029') {
                        output.append("\\u");
                        String hex = Integer.toHexString(value);
                        output.append("0".repeat(4 - hex.length())).append(hex);
                    } else output.append(value);
                }
            }
        }
        return output.append('"').toString();
    }
}
