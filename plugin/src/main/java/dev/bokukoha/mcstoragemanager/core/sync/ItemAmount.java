package dev.bokukoha.mcstoragemanager.core.sync;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** A quantity of one Minecraft item, expressed without Bukkit's ItemStack type. */
public record ItemAmount(String itemKey, long amount, Map<String, String> variant) {
    private static final Pattern ITEM_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ItemAmount {
        Objects.requireNonNull(itemKey, "itemKey");
        if (!ITEM_KEY.matcher(itemKey).matches()) {
            throw new IllegalArgumentException("itemKey must be a namespaced key");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        variant = immutableTextMap(variant, "variant");
    }

    private static Map<String, String> immutableTextMap(Map<String, String> values, String field) {
        Objects.requireNonNull(values, field);
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), field + " key");
            String value = Objects.requireNonNull(entry.getValue(), field + " value");
            if (key.isBlank()) {
                throw new IllegalArgumentException(field + " keys must not be blank");
            }
            copy.put(key, value);
        }
        return Map.copyOf(copy);
    }
}
