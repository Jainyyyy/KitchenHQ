package com.rasoiroute.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void readsAnObjectWithEveryKindOfValue() {
        Map<String, Object> m = obj(Json.parse(
                "{\"name\": \"Meera\", \"count\": 3, \"price\": 12.5, \"paid\": true, \"gift\": false, \"note\": null}"));
        assertEquals("Meera", m.get("name"));
        assertEquals(3.0, (Double) m.get("count"), 1e-12);
        assertEquals(12.5, (Double) m.get("price"), 1e-12);
        assertEquals(Boolean.TRUE, m.get("paid"));
        assertEquals(Boolean.FALSE, m.get("gift"));
        assertTrue(m.containsKey("note"));
        assertNull(m.get("note"));
    }

    @Test
    void readsNestedListsAndObjects() {
        Map<String, Object> m = obj(Json.parse("{\"stops\": [{\"a\": 1}, {\"a\": 2}], \"empty\": [], \"none\": {}}"));
        List<?> stops = (List<?>) m.get("stops");
        assertEquals(2, stops.size());
        assertEquals(2.0, (Double) obj(stops.get(1)).get("a"), 1e-12);
        assertEquals(0, ((List<?>) m.get("empty")).size());
        assertEquals(0, obj(m.get("none")).size());
    }

    @Test
    void readsNumbersInAllTheirShapes() {
        assertEquals(-4.0, (Double) Json.parse("-4"), 1e-12);
        assertEquals(0.5, (Double) Json.parse("0.5"), 1e-12);
        assertEquals(1500.0, (Double) Json.parse("1.5e3"), 1e-12);
        assertEquals(0.025, (Double) Json.parse("2.5E-2"), 1e-12);
        assertEquals(0.0, (Double) Json.parse("0"), 1e-12);
    }

    @Test
    void readsEscapesInsideText() {
        assertEquals("say \"hi\"\nnext\ttab\\ / é", Json.parse("\"say \\\"hi\\\"\\nnext\\ttab\\\\ \\/ \\u00e9\""));
    }

    @Test
    void ignoresSpacesAndLineBreaksBetweenParts() {
        Map<String, Object> m = obj(Json.parse("  {\n \"a\" :\t[ 1 , 2 ]\r\n}  "));
        assertEquals(2, ((List<?>) m.get("a")).size());
    }

    @Test
    void rejectsBrokenJsonWithAClearMessage() {
        String[] broken = {
                "", "   ", "{", "[1, 2", "{\"a\": 1,}", "[1,]", "{a: 1}", "{'a': 1}", "{\"a\" 1}",
                "{\"a\": 1} extra", "\"never closed", "tru", "nul", "01", "-", "1.", "1e", "+5", "\"bad \\x escape\"",
                "\"bad \\u12 escape\"", "\"raw\nline break\"", "{\"a\": 1 \"b\": 2}", "[1 2]"};
        for (String text : broken) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Json.parse(text),
                    "should reject: " + text);
            assertTrue(e.getMessage().startsWith("Invalid JSON"), "message for [" + text + "] was " + e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> Json.parse(null));
    }

    @Test
    void rejectsAbsurdlyDeepNestingInsteadOfCrashing() {
        // 100,000 opening brackets: a naive reader would overflow the stack.
        String bomb = "[".repeat(100_000) + "]".repeat(100_000);
        assertThrows(IllegalArgumentException.class, () -> Json.parse(bomb));
    }

    @Test
    void rejectsNumbersTooBigToHold() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("1e999"));
    }

    @Test
    void writesEveryKindOfValue() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", "Meera");
        m.put("whole", 3);
        m.put("big", 5_000_000_000L);
        m.put("decimal", 12.5);
        m.put("yes", true);
        m.put("nothing", null);
        m.put("list", List.of(1, "two"));
        assertEquals("{\"text\":\"Meera\",\"whole\":3,\"big\":5000000000,\"decimal\":12.5,\"yes\":true,"
                + "\"nothing\":null,\"list\":[1,\"two\"]}", Json.write(m));
    }

    @Test
    void writesSpecialCharactersSafely() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\u0001\"", Json.write("a\"b\\c\nd\te\u0001"));
        assertEquals("\"é ₹\"", Json.write("é ₹"));
    }

    @Test
    void whatWeWriteWeCanReadBack() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("note", "line1\nline2 \"quoted\" \\ back");
        original.put("values", new ArrayList<Object>(List.of(1.5, -2.0, true, "x")));
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("k", null);
        original.put("inner", inner);

        assertEquals(original, Json.parse(Json.write(original)));
    }

    @Test
    void keepsTheOrderKeysWereAdded() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("z", 1);
        m.put("a", 2);
        m.put("m", 3);
        assertEquals("{\"z\":1,\"a\":2,\"m\":3}", Json.write(m));
    }

    @Test
    void refusesToWriteWhatJsonCannotHold() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
        Map<Object, Object> badKeys = new LinkedHashMap<>();
        badKeys.put(5, "x");
        assertThrows(IllegalArgumentException.class, () -> Json.write(badKeys));
    }
}
