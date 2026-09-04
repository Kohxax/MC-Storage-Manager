package dev.bokukoha.mcstoragemanager.platform.sync;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader for the trusted, typed plugin API responses. */
final class SimpleJson {
    private final String source;
    private int index;

    private SimpleJson(String source) { this.source = source; }

    static Object parse(String source) {
        SimpleJson parser = new SimpleJson(source);
        Object value = parser.value();
        parser.white();
        if (parser.index != source.length()) throw new IllegalArgumentException("trailing JSON content");
        return value;
    }

    private Object value() {
        white();
        if (index >= source.length()) throw new IllegalArgumentException("unexpected JSON end");
        return switch (source.charAt(index)) {
            case '{' -> object(); case '[' -> array(); case '\"' -> string(); case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE); case 'n' -> literal("null", null); default -> number();
        };
    }
    private Map<String, Object> object() {
        index++; Map<String, Object> result = new LinkedHashMap<>(); white();
        if (take('}')) return Map.copyOf(result);
        do { white(); String key = string(); white(); need(':'); result.put(key, value()); white(); } while (take(','));
        need('}'); return Map.copyOf(result);
    }
    private List<Object> array() {
        index++; List<Object> result = new ArrayList<>(); white();
        if (take(']')) return List.copyOf(result);
        do { result.add(value()); white(); } while (take(',')); need(']'); return List.copyOf(result);
    }
    private String string() {
        need('\"'); StringBuilder value = new StringBuilder();
        while (index < source.length() && source.charAt(index) != '\"') { char c = source.charAt(index++); if (c != '\\') value.append(c); else {
            if (index >= source.length()) throw new IllegalArgumentException("bad escape"); char e = source.charAt(index++);
            switch (e) { case '\"' -> value.append('\"'); case '\\' -> value.append('\\'); case '/' -> value.append('/'); case 'b' -> value.append('\b'); case 'f' -> value.append('\f'); case 'n' -> value.append('\n'); case 'r' -> value.append('\r'); case 't' -> value.append('\t'); case 'u' -> { if (index + 4 > source.length()) throw new IllegalArgumentException("bad unicode escape"); value.append((char) Integer.parseInt(source.substring(index, index + 4), 16)); index += 4; } default -> throw new IllegalArgumentException("bad escape"); }
        }} need('\"'); return value.toString();
    }
    private Object literal(String text, Object value) { if (!source.startsWith(text, index)) throw new IllegalArgumentException("invalid literal"); index += text.length(); return value; }
    private Number number() { int start = index; while (index < source.length() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++; try { return new BigDecimal(source.substring(start, index)); } catch (NumberFormatException exception) { throw new IllegalArgumentException("invalid number", exception); } }
    private void white() { while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++; }
    private boolean take(char expected) { if (index < source.length() && source.charAt(index) == expected) { index++; return true; } return false; }
    private void need(char expected) { if (!take(expected)) throw new IllegalArgumentException("expected " + expected); }
}
