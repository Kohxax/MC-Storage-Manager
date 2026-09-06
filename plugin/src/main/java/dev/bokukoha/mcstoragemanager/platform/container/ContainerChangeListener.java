package dev.bokukoha.mcstoragemanager.platform.container;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import dev.bokukoha.mcstoragemanager.core.region.RegionRegistry;
import dev.bokukoha.mcstoragemanager.core.region.WorldIdentity;
import dev.bokukoha.mcstoragemanager.core.sync.ContainerSyncService;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/** Captures Bukkit state only on the main thread and queues immutable snapshots for async delivery. */
public final class ContainerChangeListener implements Listener {
    private final JavaPlugin plugin;
    private final RegionRegistry regions;
    private final ContainerSyncService syncService;
    private final Runnable triggerSend;

    public ContainerChangeListener(JavaPlugin plugin, RegionRegistry regions, ContainerSyncService syncService) {
        this(plugin, regions, syncService, () -> { });
    }

    /** Called after a snapshot is durably queued to wake the asynchronous sender. */
    public ContainerChangeListener(JavaPlugin plugin, RegionRegistry regions, ContainerSyncService syncService,
                                   Runnable triggerSend) {
        this.plugin = plugin;
        this.regions = regions;
        this.syncService = syncService;
        this.triggerSend = Objects.requireNonNull(triggerSend, "triggerSend");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (holder == null) {
            return;
        }
        try {
            BlockPosition position = TrackedContainer.normalizedPosition(holder);
            queueSnapshot(event.getPlayer().getWorld(), position, TrackedContainer.containerTypeForHolder(holder), inventory);
        } catch (IllegalArgumentException ignored) {
            // A non-block inventory was closed.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!TrackedContainer.isTracked(block.getState())) {
            return;
        }
        // The placed block's inventory is only stable after the placement event completes.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            BlockState current = block.getState();
            if (!TrackedContainer.isTracked(current)) return;
            // A newly placed chest can join a prior single chest. Remove every non-canonical
            // local identifier before recording the combined inventory.
            deleteNonCanonicalComponentIds(block.getWorld(), TrackedContainer.componentPositions(block, current),
                    TrackedContainer.normalizedPosition(block, current), TrackedContainer.containerType(current));
            queueSnapshotFromBlock(block);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockState state = block.getState();
        if (!TrackedContainer.isTracked(state)) {
            return;
        }
        BlockPosition position = TrackedContainer.normalizedPosition(block, state);
        Set<BlockPosition> originalComponent = TrackedContainer.componentPositions(block, state);
        String containerType = TrackedContainer.containerType(state);
        findRegion(block.getWorld(), position).ifPresent(region ->
                markDirty(InventorySnapshotter.deleted(region.id(), position, containerType)));
        // A double chest becomes a single chest after this event. Capture its surviving half on
        // the next tick so the remote side receives both the old-ID deletion and new snapshot.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (BlockPosition member : originalComponent) {
                if (member.equals(new BlockPosition(block.getX(), block.getY(), block.getZ()))) continue;
                Block remaining = block.getWorld().getBlockAt(member.x(), member.y(), member.z());
                queueSnapshotFromBlock(remaining);
            }
        });
    }

    private void queueSnapshotFromBlock(Block block) {
        BlockState state = block.getState();
        if (!TrackedContainer.isTracked(state) || !(state instanceof InventoryHolder holder)) {
            return;
        }
        queueSnapshot(block.getWorld(), TrackedContainer.normalizedPosition(block, state),
                TrackedContainer.containerType(state), holder.getInventory());
    }

    private void queueSnapshot(World world, BlockPosition position, String containerType, Inventory inventory) {
        findRegion(world, position).ifPresent(region ->
                markDirty(InventorySnapshotter.snapshot(region.id(), position, containerType, inventory)));
    }

    private void deleteNonCanonicalComponentIds(World world, Set<BlockPosition> positions, BlockPosition canonical,
                                                 String containerType) {
        for (BlockPosition position : positions) {
            if (!position.equals(canonical)) {
                findRegion(world, position).ifPresent(region ->
                        markDirty(InventorySnapshotter.deleted(region.id(), position, containerType)));
            }
        }
    }

    private void markDirty(dev.bokukoha.mcstoragemanager.core.sync.ContainerSnapshot snapshot) {
        syncService.markDirty(snapshot);
        triggerSend.run();
    }

    private Optional<dev.bokukoha.mcstoragemanager.core.region.RegisteredRegion> findRegion(
            World world, BlockPosition position) {
        return regions.findContaining(new WorldIdentity(world.getUID(), world.getName(), world.getKey().toString()), position);
    }
}
