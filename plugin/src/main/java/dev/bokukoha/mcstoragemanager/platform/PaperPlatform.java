package dev.bokukoha.mcstoragemanager.platform;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Adapter for Paper/Bukkit APIs; keep platform calls out of core packages. */
public final class PaperPlatform {
    private final PluginManager pluginManager;

    public PaperPlatform(JavaPlugin plugin) {
        this.pluginManager = plugin.getServer().getPluginManager();
    }

    public boolean isWorldEditAvailable() {
        return pluginManager.isPluginEnabled("WorldEdit");
    }
}
