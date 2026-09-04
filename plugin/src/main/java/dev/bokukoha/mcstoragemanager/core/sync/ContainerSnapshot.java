package dev.bokukoha.mcstoragemanager.core.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Complete, replacement-style contents of one container at a point in time. */
public record ContainerSnapshot(
        ContainerId containerId, String containerType, List<ItemAmount> items, Instant observedAt, boolean deleted) {
    private static final Pattern CONTAINER_TYPE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ContainerSnapshot {
        Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(containerType, "containerType");
        if (!CONTAINER_TYPE.matcher(containerType).matches()) {
            throw new IllegalArgumentException("containerType must be a namespaced key");
        }
        Objects.requireNonNull(observedAt, "observedAt");
        items = canonicalize(items);
        if (deleted && !items.isEmpty()) {
            throw new IllegalArgumentException("a deletion snapshot must not contain items");
        }
    }

    public ContainerSnapshot(ContainerId containerId, String containerType, List<ItemAmount> items, Instant observedAt) {
        this(containerId, containerType, items, observedAt, false);
    }

    private static List<ItemAmount> canonicalize(List<ItemAmount> items) {
        Objects.requireNonNull(items, "items");
        List<ItemAmount> copy = new ArrayList<>(items);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("items must not contain null");
        }
        copy.sort(Comparator.comparing(ItemAmount::itemKey).thenComparing(item -> item.variant().toString()));
        for (int index = 1; index < copy.size(); index++) {
            ItemAmount previous = copy.get(index - 1);
            ItemAmount current = copy.get(index);
            if (previous.itemKey().equals(current.itemKey()) && previous.variant().equals(current.variant())) {
                throw new IllegalArgumentException("items must not contain duplicate item variants");
            }
        }
        return List.copyOf(copy);
    }
}
