package dev.bokukoha.mcstoragemanager.core.sync;

import java.util.Map;

/** Persistence DTO for {@link ItemAmount}. */
public record ItemAmountData(String itemKey, long amount, Map<String, String> variant) {
    public ItemAmount toItemAmount() {
        return new ItemAmount(itemKey, amount, variant);
    }

    public static ItemAmountData from(ItemAmount item) {
        return new ItemAmountData(item.itemKey(), item.amount(), item.variant());
    }
}
