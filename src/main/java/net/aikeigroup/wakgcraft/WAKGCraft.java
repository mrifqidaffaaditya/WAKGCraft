package net.aikeigroup.wakgcraft;

import net.aikeigroup.wakgcraft.commands.WAKGCommand;
import net.aikeigroup.wakgcraft.commands.CustomCommandExecutor;
import net.aikeigroup.wakgcraft.listeners.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;

public class WAKGCraft extends JavaPlugin {

    private ConfigManager configManager;
    private WAClient waClient;
    private WebhookServer webhookServer;

    @Override
    public void onEnable() {
        // Initialize Configuration
        configManager = new ConfigManager(this);
        
        // Initialize WA Client
        waClient = new WAClient(this);
        
        // Initialize Webhook Server if enabled
        if (configManager.getConfig().getBoolean("whatsapp-to-minecraft.webhook.enabled", false)) {
            int port = configManager.getConfig().getInt("whatsapp-to-minecraft.webhook.port", 8080);
            webhookServer = new WebhookServer(this, port);
            webhookServer.start();
        }

        // Register Main Commands
        if (getCommand("wakgcraft") != null) {
            getCommand("wakgcraft").setExecutor(new WAKGCommand(this));
        }

        // Register Custom Commands from commands.yml
        registerCustomCommands();

        // Register Listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        // Check for PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI found and integrated successfully.");
        } else {
            getLogger().warning("Could not find PlaceholderAPI! Placeholders may not work.");
        }

        // Send Server Start Message
        if (configManager.getConfig().getBoolean("minecraft-to-whatsapp.server-start.enabled", false)) {
            String targetJid = configManager.getConfig().getString("Channels.GlobalChat");
            String format = configManager.getConfig().getString("minecraft-to-whatsapp.server-start.format", "Server Started");
            format = parsePlaceholders(format);
            if (targetJid != null && !targetJid.isEmpty() && !targetJid.equals("ENTER_GLOBAL_CHAT_JID_HERE")) {
                waClient.sendMessage(targetJid, format);
            }
        }

        getLogger().info("WAKGCraft has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (webhookServer != null) {
            webhookServer.stop();
        }

        // Send Server Stop Message
        if (configManager.getConfig().getBoolean("minecraft-to-whatsapp.server-stop.enabled", false)) {
            String targetJid = configManager.getConfig().getString("Channels.GlobalChat");
            String format = configManager.getConfig().getString("minecraft-to-whatsapp.server-stop.format", "Server Stopped");
            format = parsePlaceholders(format);
            if (targetJid != null && !targetJid.isEmpty() && !targetJid.equals("ENTER_GLOBAL_CHAT_JID_HERE")) {
                // Must be synchronous or use simple wait since plugin is disabling
                try {
                    waClient.sendMessage(targetJid, format).join();
                } catch (Exception ignored) {}
            }
        }

        getLogger().info("WAKGCraft has been disabled.");
    }

    /**
     * Dynamically registers custom Minecraft commands from commands.yml.
     * Uses reflection to create PluginCommand instances and registers them to Bukkit's CommandMap.
     */
    private void registerCustomCommands() {
        ConfigurationSection mcCmds = configManager.getCommandsConfig().getConfigurationSection("minecraft-commands");
        if (mcCmds == null) return;

        CommandMap commandMap = Bukkit.getCommandMap();

        for (String key : mcCmds.getKeys(false)) {
            String cmdName = mcCmds.getString(key + ".command", key).toLowerCase();
            String description = mcCmds.getString(key + ".description", "A WAKGCraft custom command");
            String usage = mcCmds.getString(key + ".usage", "");
            String permission = mcCmds.getString(key + ".permission", "");

            try {
                // Create a PluginCommand via reflection (constructor is protected)
                Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
                constructor.setAccessible(true);
                PluginCommand pluginCommand = constructor.newInstance(cmdName, this);
                
                pluginCommand.setDescription(description);
                if (!usage.isEmpty()) {
                    pluginCommand.setUsage("/" + cmdName + " " + usage);
                }
                if (!permission.isEmpty()) {
                    pluginCommand.setPermission(permission);
                }
                pluginCommand.setExecutor(new CustomCommandExecutor(this, key));

                // Register command to Bukkit
                commandMap.register("wakgcraft", pluginCommand);
                getLogger().info("Registered custom command: /" + cmdName);
            } catch (Exception e) {
                getLogger().warning("Failed to register custom command /" + cmdName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Performs a full reload of the entire plugin — equivalent to a restart.
     * Stops webhook, reloads all configs, re-registers commands, and restarts webhook.
     */
    public void fullReload() {
        getLogger().info("Performing full WAKGCraft reload...");

        // 1. Stop Webhook Server
        if (webhookServer != null) {
            webhookServer.stop();
            webhookServer = null;
        }

        // 2. Reload all configurations (config.yml + commands.yml)
        configManager.reload();

        // 3. Re-initialize WA Client (picks up new API keys/URLs)
        waClient = new WAClient(this);

        // 4. Re-register custom commands from commands.yml
        registerCustomCommands();

        // 5. Restart Webhook Server with potentially new settings
        if (configManager.getConfig().getBoolean("whatsapp-to-minecraft.webhook.enabled", false)) {
            int port = configManager.getConfig().getInt("whatsapp-to-minecraft.webhook.port", 8080);
            webhookServer = new WebhookServer(this, port);
            webhookServer.start();
        }

        getLogger().info("WAKGCraft full reload complete.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Resolves a list of channel names to their JIDs from config.yml.
     * If the list contains "global", returns ALL channel JIDs.
     *
     * @param channelNames List of channel names (e.g., ["GlobalChat", "AdminAlerts"] or ["global"])
     * @return List of resolved JID strings
     */
    public java.util.List<String> resolveChannelJids(java.util.List<String> channelNames) {
        java.util.List<String> jids = new java.util.ArrayList<>();
        if (channelNames == null || channelNames.isEmpty()) return jids;

        ConfigurationSection channelsSection = configManager.getConfig().getConfigurationSection("Channels");
        if (channelsSection == null) return jids;

        // Check for "global" keyword
        for (String name : channelNames) {
            if (name.equalsIgnoreCase("global")) {
                // Return ALL channels
                for (String key : channelsSection.getKeys(false)) {
                    String jid = channelsSection.getString(key, "");
                    if (!jid.isEmpty() && !jid.startsWith("ENTER_")) {
                        jids.add(jid);
                    }
                }
                return jids;
            }
        }

        // Resolve specific channel names
        for (String name : channelNames) {
            String jid = channelsSection.getString(name, "");
            if (!jid.isEmpty() && !jid.startsWith("ENTER_")) {
                jids.add(jid);
            } else {
                getLogger().warning("Channel '" + name + "' not found or not configured in Channels section.");
            }
        }
        return jids;
    }

    /**
     * Checks if a JID belongs to any of the specified channel names.
     * If channels list contains "global", returns true for any JID that is in any channel.
     *
     * @param jid The JID to check
     * @param channelNames List of channel names to check against
     * @return true if the JID matches any of the resolved channels
     */
    public boolean isJidInChannels(String jid, java.util.List<String> channelNames) {
        if (channelNames == null || channelNames.isEmpty()) return false;
        java.util.List<String> resolvedJids = resolveChannelJids(channelNames);
        return resolvedJids.contains(jid);
    }

    private String parsePlaceholders(String text) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, text);
        }
        return text;
    }

    public WAClient getWaClient() {
        return waClient;
    }
}
