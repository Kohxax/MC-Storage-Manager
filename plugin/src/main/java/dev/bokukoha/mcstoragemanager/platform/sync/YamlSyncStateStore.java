package dev.bokukoha.mcstoragemanager.platform.sync;

import dev.bokukoha.mcstoragemanager.core.sync.ContainerSnapshotData;
import dev.bokukoha.mcstoragemanager.core.sync.ItemAmountData;
import dev.bokukoha.mcstoragemanager.core.sync.SyncBatchData;
import dev.bokukoha.mcstoragemanager.core.sync.SyncStateData;
import dev.bokukoha.mcstoragemanager.core.sync.SyncStateStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Atomically persists dirty snapshots and unacknowledged batches without including credentials. */
public final class YamlSyncStateStore implements SyncStateStore {
    private final File file;
    private final Logger logger;

    public YamlSyncStateStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public Optional<SyncStateData> load() {
        if (!file.isFile()) {
            return Optional.empty();
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            long nextRevision = yaml.getLong("next-revision");
            return Optional.of(new SyncStateData(nextRevision,
                    readSnapshots(yaml.getMapList("dirty")), readBatches(yaml.getMapList("pending"))));
        } catch (RuntimeException exception) {
            logger.warning("Could not restore pending sync state; starting with an empty queue: " + exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(SyncStateData state) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-revision", state.nextRevision());
        yaml.set("dirty", state.dirtyContainers().stream().map(YamlSyncStateStore::writeSnapshot).toList());
        yaml.set("pending", state.pendingBatches().stream().map(YamlSyncStateStore::writeBatch).toList());
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist pending sync state", exception);
        }
    }

    private static List<ContainerSnapshotData> readSnapshots(List<Map<?, ?>> maps) {
        List<ContainerSnapshotData> snapshots = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            snapshots.add(readSnapshot(map));
        }
        return List.copyOf(snapshots);
    }

    private static List<SyncBatchData> readBatches(List<Map<?, ?>> maps) {
        List<SyncBatchData> batches = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> containers = (List<Map<?, ?>>) required(map, "containers");
            batches.add(new SyncBatchData(
                    UUID.fromString(text(map, "id")), number(map, "revision"), readSnapshots(containers),
                    Math.toIntExact(number(map, "failure-count")), Instant.parse(text(map, "next-attempt-at"))));
        }
        return List.copyOf(batches);
    }

    private static ContainerSnapshotData readSnapshot(Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> items = (List<Map<?, ?>>) required(map, "items");
        List<ItemAmountData> itemData = new ArrayList<>();
        for (Map<?, ?> item : items) {
            @SuppressWarnings("unchecked")
            Map<String, String> variant = item.containsKey("variant")
                    ? copyVariant((Map<?, ?>) item.get("variant")) : Map.of();
            itemData.add(new ItemAmountData(text(item, "item-key"), number(item, "amount"), variant));
        }
        return new ContainerSnapshotData(UUID.fromString(text(map, "region-id")),
                Math.toIntExact(number(map, "x")), Math.toIntExact(number(map, "y")), Math.toIntExact(number(map, "z")),
                text(map, "container-type"), itemData, Instant.parse(text(map, "observed-at")), map.get("deleted") instanceof Boolean deleted && deleted);
    }

    private static Map<String, Object> writeBatch(SyncBatchData batch) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", batch.id().toString());
        map.put("revision", batch.revision());
        map.put("failure-count", batch.failureCount());
        map.put("next-attempt-at", batch.nextAttemptAt().toString());
        map.put("containers", batch.containers().stream().map(YamlSyncStateStore::writeSnapshot).toList());
        return map;
    }

    private static Map<String, Object> writeSnapshot(ContainerSnapshotData snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("region-id", snapshot.regionId().toString());
        map.put("x", snapshot.x());
        map.put("y", snapshot.y());
        map.put("z", snapshot.z());
        map.put("container-type", snapshot.containerType());
        map.put("observed-at", snapshot.observedAt().toString());
        if (snapshot.deleted()) {
            map.put("deleted", true);
        }
        map.put("items", snapshot.items().stream().map(item -> {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("item-key", item.itemKey());
            itemMap.put("amount", item.amount());
            if (!item.variant().isEmpty()) {
                itemMap.put("variant", item.variant());
            }
            return itemMap;
        }).toList());
        return map;
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = required(map, key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing text " + key);
        }
        return text;
    }

    private static long number(Map<?, ?> map, String key) {
        Object value = required(map, key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("missing number " + key);
        }
        return number.longValue();
    }

    private static Object required(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    private static Map<String, String> copyVariant(Map<?, ?> raw) {
        Map<String, String> variant = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException("variant must have text keys and values");
            }
            variant.put(key, value);
        }
        return Map.copyOf(variant);
    }
}
