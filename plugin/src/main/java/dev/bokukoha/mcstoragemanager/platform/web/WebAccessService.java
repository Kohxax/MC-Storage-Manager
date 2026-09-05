package dev.bokukoha.mcstoragemanager.platform.web;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Captures a player's current permission state on the main thread, then delegates HTTP asynchronously. */
public final class WebAccessService {
    private static final List<String> RELEVANT_PERMISSIONS = List.of(
            "storage.region.create", "storage.region.manage.own", "storage.region.manage.any",
            "storage.web.login", "storage.admin");
    private final JavaPlugin plugin;
    private final WebAccessClient client;

    public WebAccessService(JavaPlugin plugin, WebAccessClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void createLoginLink(Player player) {
        UUID playerId = player.getUniqueId();
        String currentName = player.getName();
        List<String> permissions = RELEVANT_PERMISSIONS.stream().filter(player::hasPermission).toList();
        player.sendMessage("WebログインURLを発行しています…");
        client.createLoginLink(playerId, currentName, permissions).whenComplete((url, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player current = plugin.getServer().getPlayer(playerId);
                    if (current == null || !current.isOnline()) return;
                    if (error != null) {
                        logFailure("create web login link", playerId, error);
                        current.sendMessage("WebログインURLを発行できませんでした。管理者に連絡してください。");
                        return;
                    }
                    current.sendMessage(Component.text("あなた専用のWebログインURL（5分間有効）: ")
                            .append(Component.text(url).clickEvent(ClickEvent.openUrl(url))));
                }));
    }

    public void revokeSessions(Player player) {
        UUID playerId = player.getUniqueId();
        player.sendMessage("Webセッションを失効しています…");
        client.revokeSessions(playerId).whenComplete((ignored, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player current = plugin.getServer().getPlayer(playerId);
                    if (current == null || !current.isOnline()) return;
                    if (error != null) {
                        logFailure("revoke web sessions", playerId, error);
                        current.sendMessage("Webセッションを失効できませんでした。管理者に連絡してください。");
                        return;
                    }
                    current.sendMessage("あなたのすべてのWebセッションを失効しました。");
                }));
    }

    private void logFailure(String operation, UUID playerId, Throwable error) {
        Throwable cause = unwrap(error);
        String detail = cause.getMessage();
        String description = cause.getClass().getSimpleName()
                + (detail == null || detail.isBlank() ? "" : ": " + detail);
        plugin.getLogger().log(Level.WARNING,
                "Could not " + operation + " for " + playerId + ": " + description, cause);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
