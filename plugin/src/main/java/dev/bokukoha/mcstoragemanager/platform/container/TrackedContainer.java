package dev.bokukoha.mcstoragemanager.platform.container;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Bukkit-only identification and canonical positioning for containers tracked by this plugin. */
public final class TrackedContainer {
    private TrackedContainer() {
    }

    /** Chest includes trapped chest; furnaces, hoppers, droppers, and dispensers are excluded. */
    public static boolean isTracked(BlockState state) {
        return state instanceof Chest || state instanceof Barrel || state instanceof ShulkerBox;
    }

    public static BlockPosition normalizedPosition(Block block, BlockState state) {
        if (state instanceof Chest chest) {
            Inventory inventory = chest.getInventory();
            if (inventory instanceof DoubleChestInventory doubleChestInventory
                    && doubleChestInventory.getHolder() instanceof DoubleChest doubleChest) {
                BlockPosition left = positionOf(doubleChest.getLeftSide());
                BlockPosition right = positionOf(doubleChest.getRightSide());
                if (left != null && right != null) {
                    return compare(left, right) <= 0 ? left : right;
                }
            }
        }
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPosition normalizedPosition(InventoryHolder holder) {
        if (holder instanceof DoubleChest doubleChest) {
            BlockPosition left = positionOf(doubleChest.getLeftSide());
            BlockPosition right = positionOf(doubleChest.getRightSide());
            if (left != null && right != null) {
                return compare(left, right) <= 0 ? left : right;
            }
        }
        BlockPosition position = positionOf(holder);
        if (position == null) {
            throw new IllegalArgumentException("Inventory holder has no block position");
        }
        return position;
    }

    /** Returns every physical block currently forming this container (one or two for chests). */
    public static Set<BlockPosition> componentPositions(Block block, BlockState state) {
        Set<BlockPosition> positions = new LinkedHashSet<>();
        positions.add(positionOf(block));
        if (state instanceof Chest chest) {
            Inventory inventory = chest.getInventory();
            if (inventory instanceof DoubleChestInventory doubleChestInventory
                    && doubleChestInventory.getHolder() instanceof DoubleChest doubleChest) {
                BlockPosition left = positionOf(doubleChest.getLeftSide());
                BlockPosition right = positionOf(doubleChest.getRightSide());
                if (left != null) positions.add(left);
                if (right != null) positions.add(right);
            }
        }
        return Set.copyOf(positions);
    }

    public static String containerType(BlockState state) {
        return state.getType().getKey().toString();
    }

    public static String containerTypeForHolder(InventoryHolder holder) {
        if (holder instanceof DoubleChest doubleChest) {
            if (doubleChest.getLeftSide() instanceof Chest chest) {
                return containerType((BlockState) chest);
            }
        }
        if (holder instanceof Chest chest) return containerType((BlockState) chest);
        if (holder instanceof Barrel barrel) return containerType((BlockState) barrel);
        if (holder instanceof ShulkerBox shulkerBox) return containerType((BlockState) shulkerBox);
        throw new IllegalArgumentException("Inventory holder has no tracked container type");
    }

    private static BlockPosition positionOf(InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            return positionOf(chest.getBlock());
        }
        if (holder instanceof Barrel barrel) {
            return positionOf(barrel.getBlock());
        }
        if (holder instanceof ShulkerBox shulkerBox) {
            return positionOf(shulkerBox.getBlock());
        }
        return null;
    }

    private static BlockPosition positionOf(Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    private static int compare(BlockPosition first, BlockPosition second) {
        int x = Integer.compare(first.x(), second.x());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(first.y(), second.y());
        return y != 0 ? y : Integer.compare(first.z(), second.z());
    }
}
