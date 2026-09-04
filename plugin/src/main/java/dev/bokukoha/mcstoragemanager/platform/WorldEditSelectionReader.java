package dev.bokukoha.mcstoragemanager.platform;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Reads a player's WorldEdit selection and exposes only primitive registration data. */
public final class WorldEditSelectionReader {
    private final WorldEditPlugin worldEdit;

    public WorldEditSelectionReader(WorldEditPlugin worldEdit) {
        this.worldEdit = worldEdit;
    }

    public SelectionSnapshot read(Player player) throws IncompleteRegionException {
        World world = player.getWorld();
        Region selection = worldEdit.getSession(player).getSelection(BukkitAdapter.adapt(world));
        BlockVector3 minimum = selection.getMinimumPoint();
        BlockVector3 maximum = selection.getMaximumPoint();
        return new SelectionSnapshot(
                world.getUID(),
                world.getName(),
                world.getKey().toString(),
                minimum.x(), minimum.y(), minimum.z(),
                maximum.x(), maximum.y(), maximum.z());
    }

    public record SelectionSnapshot(
            UUID worldId,
            String worldName,
            String dimensionKey,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) {
    }
}
