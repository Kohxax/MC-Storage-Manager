package dev.bokukoha.mcstoragemanager.platform.region;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import dev.bokukoha.mcstoragemanager.core.region.Cuboid;
import dev.bokukoha.mcstoragemanager.platform.container.TrackedContainer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Performs a first region scan in bounded tick slices. It never loads a chunk; a chunk becoming
 * unavailable aborts the entire candidate so incomplete container data is never registered.
 */
public final class InitialRegionScanner extends BukkitRunnable {
    private final World world;
    private final Cuboid cuboid;
    private final int blocksPerTick;
    private final int maximumContainers;
    private final Consumer<Set<BlockPosition>> success;
    private final Consumer<String> failure;
    private final long volume;
    private final long lengthX;
    private final long lengthY;
    private final Set<BlockPosition> containers = new LinkedHashSet<>();
    private long nextBlock;
    private boolean aborted;

    public InitialRegionScanner(
            World world,
            Cuboid cuboid,
            int blocksPerTick,
            int maximumContainers,
            Consumer<Set<BlockPosition>> success,
            Consumer<String> failure) {
        this.world = world;
        this.cuboid = cuboid;
        this.blocksPerTick = blocksPerTick;
        this.maximumContainers = maximumContainers;
        this.success = success;
        this.failure = failure;
        this.volume = cuboid.volume();
        this.lengthX = cuboid.lengthX();
        this.lengthY = cuboid.lengthY();
    }

    public void start(JavaPlugin plugin) {
        runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        try {
            int processed = 0;
            while (!aborted && processed < blocksPerTick && nextBlock < volume) {
                BlockPosition position = positionAt(nextBlock++);
                if (!world.isChunkLoaded(Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16))) {
                    abort("選択範囲のチャンクがアンロードされました。登録を破棄しました。");
                    return;
                }
                inspect(world.getBlockAt(position.x(), position.y(), position.z()));
                processed++;
            }
            if (!aborted && nextBlock == volume) {
                cancel();
                success.accept(Set.copyOf(containers));
            }
        } catch (RuntimeException exception) {
            abort("初回スキャンに失敗したため登録を破棄しました: " + exception.getMessage());
        }
    }

    private BlockPosition positionAt(long offset) {
        long x = cuboid.minX() + offset % lengthX;
        long yzOffset = offset / lengthX;
        long y = cuboid.minY() + yzOffset % lengthY;
        long z = cuboid.minZ() + yzOffset / lengthY;
        return new BlockPosition(Math.toIntExact(x), Math.toIntExact(y), Math.toIntExact(z));
    }

    private void inspect(Block block) {
        BlockState state = block.getState();
        if (!TrackedContainer.isTracked(state)) {
            return;
        }
        containers.add(TrackedContainer.normalizedPosition(block, state));
        if (containers.size() > maximumContainers) {
            abort("コンテナ数が上限 " + maximumContainers + " を超えたため登録を破棄しました。");
        }
    }

    private void abort(String message) {
        if (aborted) {
            return;
        }
        aborted = true;
        cancel();
        failure.accept(message);
    }
}
