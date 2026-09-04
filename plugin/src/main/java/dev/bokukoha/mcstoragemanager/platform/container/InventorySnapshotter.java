package dev.bokukoha.mcstoragemanager.platform.container;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import dev.bokukoha.mcstoragemanager.core.sync.ContainerId;
import dev.bokukoha.mcstoragemanager.core.sync.ContainerSnapshot;
import dev.bokukoha.mcstoragemanager.core.sync.ItemAmount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Converts a Bukkit inventory on the main thread into a server-independent full snapshot. */
public final class InventorySnapshotter {
    private InventorySnapshotter() {
    }

    public static ContainerSnapshot snapshot(UUID regionId, BlockPosition position, String containerType, Inventory inventory) {
        Map<ItemIdentity, Long> totals = new LinkedHashMap<>();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            ItemIdentity identity = new ItemIdentity(item.getType().getKey().toString(), Map.of());
            totals.merge(identity, (long) item.getAmount(), Math::addExact);
        }
        List<ItemAmount> items = new ArrayList<>(totals.size());
        totals.forEach((identity, amount) -> items.add(new ItemAmount(identity.itemKey(), amount, identity.variant())));
        return new ContainerSnapshot(new ContainerId(regionId, position), containerType, items, Instant.now());
    }

    public static ContainerSnapshot deleted(UUID regionId, BlockPosition position, String containerType) {
        return new ContainerSnapshot(new ContainerId(regionId, position), containerType, List.of(), Instant.now(), true);
    }

    private record ItemIdentity(String itemKey, Map<String, String> variant) {
    }
}
