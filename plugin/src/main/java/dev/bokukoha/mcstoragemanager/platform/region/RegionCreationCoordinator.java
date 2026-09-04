package dev.bokukoha.mcstoragemanager.platform.region;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import dev.bokukoha.mcstoragemanager.core.region.Cuboid;
import dev.bokukoha.mcstoragemanager.core.region.RegisteredRegion;
import dev.bokukoha.mcstoragemanager.core.region.RegionOverlapException;
import dev.bokukoha.mcstoragemanager.core.region.RegionRegistry;
import dev.bokukoha.mcstoragemanager.core.region.RegionStore;
import dev.bokukoha.mcstoragemanager.core.region.WorldIdentity;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Coordinates preflight validation, sliced scanning, local persistence, and index publication. */
public final class RegionCreationCoordinator {
    private final JavaPlugin plugin;
    private final RegionRegistry registry;
    private final RegionStore store;
    private final RegionRegistrationLimits limits;
    private final Consumer<RegisteredRegion> onRegistered;
    private final Map<UUID, Integer> pendingByOwner = new HashMap<>();

    public RegionCreationCoordinator(
            JavaPlugin plugin, RegionRegistry registry, RegionStore store, RegionRegistrationLimits limits) {
        this(plugin, registry, store, limits, ignored -> { });
    }

    public RegionCreationCoordinator(
            JavaPlugin plugin, RegionRegistry registry, RegionStore store, RegionRegistrationLimits limits,
            Consumer<RegisteredRegion> onRegistered) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.limits = limits;
        this.onRegistered = onRegistered;
    }

    public void create(Player player, String name, WorldIdentity identity, Cuboid cuboid) {
        String validationFailure = validate(player.getUniqueId(), name, cuboid, player.getWorld());
        if (validationFailure != null) {
            player.sendMessage(validationFailure);
            return;
        }
        RegisteredRegion proposed = new RegisteredRegion(
                UUID.randomUUID(), name, player.getUniqueId(), identity, cuboid, List.of(), Instant.now());
        try {
            if (!registry.findConflicts(proposed).isEmpty()) {
                player.sendMessage("選択範囲は既存のストレージ範囲と重複しているため登録できません。");
                return;
            }
        } catch (RuntimeException exception) {
            player.sendMessage("重複検証に失敗しました。登録を破棄しました。");
            return;
        }

        player.sendMessage("ストレージ範囲を初回スキャンしています。完了まで登録は確定しません。");
        pendingByOwner.merge(player.getUniqueId(), 1, Integer::sum);
        new InitialRegionScanner(player.getWorld(), cuboid, limits.blocksPerTick(), limits.maxContainersPerRegion(),
                containers -> {
                    try {
                        commit(player, proposed, containers);
                    } finally {
                        clearPending(player.getUniqueId());
                    }
                }, message -> {
                    clearPending(player.getUniqueId());
                    player.sendMessage(message);
                }).start(plugin);
    }

    private String validate(UUID ownerId, String name, Cuboid cuboid, World world) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            return "範囲名は英数字、_、- を使った1〜32文字にしてください。";
        }
        if (cuboid.lengthX() > limits.maxEdgeLength() || cuboid.lengthY() > limits.maxEdgeLength()
                || cuboid.lengthZ() > limits.maxEdgeLength()) {
            return "辺長が上限 " + limits.maxEdgeLength() + " ブロックを超えています。";
        }
        if (cuboid.volume() > limits.maxVolume()) {
            return "体積が上限 " + limits.maxVolume() + " ブロックを超えています。";
        }
        if (registry.findByOwner(ownerId).size() + pendingByOwner.getOrDefault(ownerId, 0)
                >= limits.maxRegionsPerPlayer()) {
            return "所有できるストレージ範囲の上限 " + limits.maxRegionsPerPlayer() + " 件に達しています。";
        }
        for (int chunkX = cuboid.minimumChunkX(); chunkX <= cuboid.maximumChunkX(); chunkX++) {
            for (int chunkZ = cuboid.minimumChunkZ(); chunkZ <= cuboid.maximumChunkZ(); chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return "選択範囲に未ロードのチャンクが含まれるため登録できません。";
                }
            }
        }
        return null;
    }

    private void commit(Player player, RegisteredRegion proposed, Set<BlockPosition> containers) {
        RegisteredRegion completed = new RegisteredRegion(
                proposed.id(), proposed.name(), proposed.ownerId(), proposed.world(), proposed.cuboid(),
                containers.stream().sorted((first, second) -> {
                    int x = Integer.compare(first.x(), second.x());
                    if (x != 0) return x;
                    int y = Integer.compare(first.y(), second.y());
                    return y != 0 ? y : Integer.compare(first.z(), second.z());
                }).toList(), proposed.createdAt());
        try {
            registry.register(completed);
            try {
                store.save(completed.toData());
            } catch (RuntimeException persistenceFailure) {
                registry.unregister(completed.id());
                throw persistenceFailure;
            }
            onRegistered.accept(completed);
            player.sendMessage("ストレージ範囲 '" + completed.name() + "' を登録しました。"
                    + " 体積=" + completed.cuboid().volume()
                    + ", コンテナ=" + completed.containers().size());
        } catch (RegionOverlapException overlap) {
            player.sendMessage("スキャン中に既存範囲と重複したため登録を破棄しました。");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not commit region " + completed.id() + ": " + exception.getMessage());
            player.sendMessage("登録の保存に失敗したため登録を破棄しました。");
        }
    }

    private void clearPending(UUID ownerId) {
        pendingByOwner.computeIfPresent(ownerId, (ignored, count) -> count <= 1 ? null : count - 1);
    }
}
