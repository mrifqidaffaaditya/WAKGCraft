package net.aikeigroup.wakgcraft.commands;

import net.aikeigroup.wakgcraft.WAKGCraft;
import net.aikeigroup.wakgcraft.utils.ArgsParser;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handles execution of custom commands defined in commands.yml (minecraft-commands section).
 * These commands are dynamically registered to Bukkit's CommandMap at plugin startup.
 */
public class CustomCommandExecutor implements CommandExecutor {

    private final WAKGCraft plugin;
    private final String configKey;

    public CustomCommandExecutor(WAKGCraft plugin, String configKey) {
        this.plugin = plugin;
        this.configKey = configKey;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ConfigurationSection mcCmds = plugin.getConfigManager().getCommandsConfig().getConfigurationSection("minecraft-commands");
        if (mcCmds == null) return true;

        ConfigurationSection cmdSection = mcCmds.getConfigurationSection(configKey);
        if (cmdSection == null) return true;

        // Check permission
        String permission = cmdSection.getString("permission", "");
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // Resolve target channels to JIDs
        List<String> targetChannels = cmdSection.getStringList("target-channels");
        List<String> targetJids = plugin.resolveChannelJids(targetChannels);

        if (targetJids.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "This command's WhatsApp channel routing is not configured.");
            return true;
        }

        // Build raw args
        String rawArgs = String.join(" ", args);

        // Get format and apply args
        String format = cmdSection.getString("format", "");
        String executeConsole = cmdSection.getString("execute-console", "");

        // Try to parse format with ArgsParser
        String parsedFormat = ArgsParser.parse(format, rawArgs);
        String parsedConsole = ArgsParser.parse(executeConsole, rawArgs);

        // If format parsing returns null -> not enough args, show usage
        if (parsedFormat == null || (!executeConsole.isEmpty() && parsedConsole == null)) {
            String usage = cmdSection.getString("usage", "");
            String description = cmdSection.getString("description", "");
            String errorMsg = cmdSection.getString("error-message", "");
            String cmdName = cmdSection.getString("command", configKey);

            if (!errorMsg.isEmpty()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', errorMsg));
            } else {
                sender.sendMessage(ChatColor.RED + "❌ Wrong usage!");
                if (!description.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE + description);
                }
                if (!usage.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + "/" + cmdName + " " + usage);
                } else {
                    int requiredArgs = Math.max(ArgsParser.getRequiredArgCount(format), ArgsParser.getRequiredArgCount(executeConsole));
                    StringBuilder usageBuilder = new StringBuilder("/" + cmdName);
                    for (int i = 1; i <= requiredArgs; i++) {
                        usageBuilder.append(" <arg").append(i).append(">");
                    }
                    if (format.contains("%args%") || executeConsole.contains("%args%")) {
                        usageBuilder.append(" <text...>");
                    }
                    sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + usageBuilder);
                }
                String example = cmdSection.getString("example", "");
                if (!example.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Example: " + ChatColor.WHITE + "/" + cmdName + " " + example);
                }
            }
            return true;
        }

        // Replace player placeholders
        if (sender instanceof Player) {
            parsedFormat = parsedFormat.replace("%player_name%", sender.getName());
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                parsedFormat = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((Player) sender, parsedFormat);
            }
        } else {
            parsedFormat = parsedFormat.replace("%player_name%", sender.getName());
        }

        // Send to ALL resolved WhatsApp channels
        if (!parsedFormat.isEmpty()) {
            for (String jid : targetJids) {
                plugin.getWaClient().sendMessage(jid, parsedFormat);
            }
        }

        // Execute console command if defined
        if (parsedConsole != null && !parsedConsole.isEmpty()) {
            String finalConsole = parsedConsole;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), finalConsole);
            });
        }

        // Send response message
        String response = cmdSection.getString("response-message", "");
        if (!response.isEmpty()) {
            response = ArgsParser.parse(response, rawArgs);
            if (response == null) response = cmdSection.getString("response-message", "");
            if (sender instanceof Player) {
                response = response.replace("%player_name%", sender.getName());
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', response));
        }

        return true;
    }
}
