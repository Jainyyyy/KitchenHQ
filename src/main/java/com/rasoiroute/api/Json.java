package com.rasoiroute.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer, written for this project so it needs no outside library.
 *
 * JSON is the text format the browser and the Java server use to talk to each other, e.g.
 *   {"customerName": "Meera", "item": "Cake", "tags": ["a", "b"], "paid": true, "weight": 1.5}
 *
 * Reading ({@link #parse}) turns that text into plain Java objects:
 *   object -> Map<String,Object>   array -> List<Object>   text -> String
 *   number -> Double               true/false -> Boolean   null -> null
 *
 * Writing ({@link #write}) does the reverse.
 */
public final class Json {

    /** How deeply objects/arrays may be nested. Stops a hostile request from crashing the server. */
    private static final int MAX_DEPTH = 64;

    private Json() {
        // static helper only
    }

    // =====================================================================
    // Reading
    // =====================================================================

    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Invalid JSON: there is nothing to read");
        }
        Parser parser = new Parser(text);
        parser.skipSpaces();
        Object value = parser.readValue(0);
        parser.skipSpaces();
        if (!parser.atEnd()) {
            throw parser.error("unexpected extra text after the value");
        }
        return value;
    }

    /** Reads text one character at a time, remembering where it is ("pos"). */
    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipSpaces() {
            while (!atEnd()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON: " + message + " (at position " + pos + ")");
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nested too deeply");
            }
            if (atEnd()) {
                throw error("the text ended too early");
            }
            char c = s.charAt(pos);
            if (c == '{') {
                return readObject(depth);
            }
            if (c == '[') {
                return readArray(depth);
            }
            if (c == '"') {
                return readString();
            }
            if (c == 't') {
                expectWord("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expectWord("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expectWord("null");
                return null;
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return readNumber();
            }
            throw error("unexpected character '" + c + "'");
        }

        void expectWord(String word) {
            if (!s.startsWith(word, pos)) {
                throw error("expected " + word);
            }
            pos += word.length();
        }

        Map<String, Object> readObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;                                   // skip {
            skipSpaces();
            if (!atEnd() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipSpaces();
                if (atEnd() || s.charAt(pos) != '"') {
                    throw error("object keys must be written in double quotes");
                }
                String key = readString();
                skipSpaces();
                if (atEnd() || s.charAt(pos) != ':') {
                    throw error("expected : after the key");
                }
                pos++;
                skipSpaces();
                map.put(key, readValue(depth + 1));
                skipSpaces();
                if (atEnd()) {
                    throw error("the object was never closed");
                }
                char c = s.charAt(pos++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    pos--;
                    throw error("expected , or } after a value");
                }
            }
        }

        List<Object> readArray(int depth) {
            List<Object> list = new ArrayList<>();
            pos++;                                   // skip [
            skipSpaces();
            if (!atEnd() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipSpaces();
                list.add(readValue(depth + 1));
                skipSpaces();
                if (atEnd()) {
                    throw error("the list was never closed");
                }
                char c = s.charAt(pos++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    pos--;
                    throw error("expected , or ] after a value");
                }
            }
        }

        String readString() {
            pos++;                                   // skip the opening quote
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("text in quotes was never closed");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("text ended in the middle of an escape");
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"', '\\', '/' -> out.append(e);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (pos + 4 > s.length()) {
                                throw error("a \\u escape needs 4 hex digits");
                            }
                            int code = 0;
                            for (int i = 0; i < 4; i++) {
                                int digit = Character.digit(s.charAt(pos + i), 16);
                                if (digit < 0) {
                                    throw error("a \\u escape needs 4 hex digits");
                                }
                                code = code * 16 + digit;
                            }
                            pos += 4;
                            out.append((char) code);
                        }
                        default -> {
                            pos--;
                            throw error("unknown escape \\" + e);
                        }
                    }
                } else if (c < 0x20) {
                    pos--;
                    throw error("a raw line break or control character is not allowed inside text");
                } else {
                    out.append(c);
                }
            }
        }

        Double readNumber() {
            int start = pos;
            if (s.charAt(pos) == '-') {
                pos++;
            }
            if (atEnd() || !isDigit(s.charAt(pos))) {
                throw error("a number needs digits");
            }
            if (s.charAt(pos) == '0') {
                pos++;
                if (!atEnd() && isDigit(s.charAt(pos))) {
                    throw error("a number cannot start with a leading zero");
                }
            } else {
                while (!atEnd() && isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            if (!atEnd() && s.charAt(pos) == '.') {
                pos++;
                if (atEnd() || !isDigit(s.charAt(pos))) {
                    throw error("digits are needed after the decimal point");
                }
                while (!atEnd() && isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            if (!atEnd() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                pos++;
                if (!atEnd() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                if (atEnd() || !isDigit(s.charAt(pos))) {
                    throw error("digits are needed in the exponent");
                }
                while (!atEnd() && isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            double value = Double.parseDouble(s.substring(start, pos));
            if (!Double.isFinite(value)) {
                throw error("the number is too large");
            }
            return value;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }

    // =====================================================================
    // Writing
    // =====================================================================

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Cannot write JSON nested more than " + MAX_DEPTH + " levels");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(out, text);
        } else if (value instanceof Boolean flag) {
            out.append(flag.booleanValue());
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException("JSON cannot hold NaN or infinity");
            }
            out.append(d);
        } else if (value instanceof Enum<?> constant) {
            writeString(out, constant.name());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object keys must be text");
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, key);
                out.append(':');
                writeValue(out, entry.getValue(), depth + 1);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item, depth + 1);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Cannot write " + value.getClass().getSimpleName() + " as JSON");
        }
    }

    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
