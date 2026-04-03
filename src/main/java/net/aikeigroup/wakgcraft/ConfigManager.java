package net.aikeigroup.wakgcraft;

import java.io.File;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigManager {

    private final WAKGCraft plugin;
    private FileConfiguration config;

    private FileConfiguration commandsConfig;
    private File commandsFile;

    public ConfigManager(WAKGCraft plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        saveDefaultCommands();
        reload();
    }

    private void saveDefaultCommands() {
        commandsFile = new File(plugin.getDataFolder(), "commands.yml");
        if (!commandsFile.exists()) {
            plugin.saveResource("commands.yml", false);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.commandsConfig = YamlConfiguration.loadConfiguration(commandsFile);
    }

    public FileConfiguration getCommandsConfig() {
        return commandsConfig;
    }

    public String getApiUrl() {
        return config.getString("wa-akg.api-url", "https://wa-akg.aikeigroup.net/api");
    }

    public String getApiKey() {
        return config.getString("wa-akg.api-key", "");
    }

    public String getSessionId() {
        return config.getString("wa-akg.session-id", "session-01");
    }

    public String getMessage(String key) {
        String prefix = config.getString("plugin-messages.prefix", "&7[&aWAKGCraft&7] &r");
        String message = config.getString("plugin-messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
