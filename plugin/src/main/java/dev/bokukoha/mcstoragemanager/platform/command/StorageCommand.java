package dev.bokukoha.mcstoragemanager.platform.command;

import com.sk89q.worldedit.IncompleteRegionException;
import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import dev.bokukoha.mcstoragemanager.core.region.Cuboid;
import dev.bokukoha.mcstoragemanager.core.region.WorldIdentity;
import dev.bokukoha.mcstoragemanager.platform.WorldEditSelectionReader;
import dev.bokukoha.mcstoragemanager.platform.region.RegionCreationCoordinator;
import dev.bokukoha.mcstoragemanager.platform.web.WebAccessService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Bukkit command adapter for region creation. Application registration logic remains in the coordinator. */
public final class StorageCommand implements CommandExecutor {
    private final WorldEditSelectionReader selectionReader;
    private final RegionCreationCoordinator creationCoordinator;
    private final WebAccessService webAccessService;

    public StorageCommand(WorldEditSelectionReader selectionReader, RegionCreationCoordinator creationCoordinator,
                          WebAccessService webAccessService) {
        this.selectionReader = selectionReader;
        this.creationCoordinator = creationCoordinator;
        this.webAccessService = webAccessService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        if (arguments.length >= 1 && arguments[0].equalsIgnoreCase("web")) {
            return handleWeb(player, arguments);
        }
        if (arguments.length != 2 || !arguments[0].equalsIgnoreCase("create")) {
            sender.sendMessage("使い方: /storage create <name>");
            return true;
        }
        if (!player.hasPermission("storage.region.create")) {
            player.sendMessage("この操作を行う権限がありません。");
            return true;
        }
        try {
            WorldEditSelectionReader.SelectionSnapshot selection = selectionReader.read(player);
            Cuboid cuboid = Cuboid.between(
                    new BlockPosition(selection.minX(), selection.minY(), selection.minZ()),
                    new BlockPosition(selection.maxX(), selection.maxY(), selection.maxZ()));
            WorldIdentity world = new WorldIdentity(
                    selection.worldId(), selection.worldName(), selection.dimensionKey());
            creationCoordinator.create(player, arguments[1], world, cuboid);
        } catch (IncompleteRegionException exception) {
            player.sendMessage("WorldEditで範囲の2点を選択してください。");
        } catch (RuntimeException exception) {
            player.sendMessage("選択範囲を読み取れませんでした。登録を開始していません。");
        }
        return true;
    }

    private boolean handleWeb(Player player, String[] arguments) {
        if (!player.hasPermission("storage.web.login")) {
            player.sendMessage("You do not have permission to create a web login link.");
            return true;
        }
        if (webAccessService == null) {
            player.sendMessage("Web integration is not configured on this server.");
            return true;
        }
        if (arguments.length == 1) {
            webAccessService.createLoginLink(player);
            return true;
        }
        if (arguments.length == 2 && arguments[1].equalsIgnoreCase("revoke")) {
            webAccessService.revokeSessions(player);
            return true;
        }
        player.sendMessage("Usage: /storage web [revoke]");
        return true;
    }
}
