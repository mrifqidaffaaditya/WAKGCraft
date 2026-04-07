package net.aikeigroup.wakgcraft.commands;

import net.aikeigroup.wakgcraft.WAKGCraft;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WAKGCommand implements CommandExecutor {

    private final WAKGCraft plugin;

    public WAKGCommand(WAKGCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        // Apply Cooldown if not skipping and is player
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (!p.hasPermission("wakgcraft.bypasscooldown")) {
                int cooldownSecs = plugin.getConfigManager().getConfig().getInt("general.command-cooldown", 0);
                if (net.aikeigroup.wakgcraft.utils.CooldownManager.isOnCooldown(p.getUniqueId().toString(), cooldownSecs)) {
                    long remaining = net.aikeigroup.wakgcraft.utils.CooldownManager.getRemainingCooldown(p.getUniqueId().toString());
                    String msg = plugin.getConfigManager().getConfig().getString("general.cooldown-message", "⏳ Please wait %time%s before using commands again!");
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.replace("%time%", String.valueOf(remaining))));
                    return true;
                }
            }
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("wakgcraft.admin")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            plugin.fullReload();
            sender.sendMessage(plugin.getConfigManager().getMessage("reloaded"));
            return true;
        }

        if (args[0].equalsIgnoreCase("send")) {
            if (!sender.hasPermission("wakgcraft.admin")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(plugin.getConfigManager().getMessage("usage-send"));
                return true;
            }

            String targetJid = args[1];
            
            // Build the message
            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                messageBuilder.append(args[i]).append(" ");
            }
            
            String finalMessage = messageBuilder.toString().trim();
            
            // Send asynchronously
            plugin.getWaClient().sendMessage(targetJid, finalMessage).thenAccept(success -> {
                if (success) {
                    String msg = plugin.getConfigManager().getMessage("sent-success")
                            .replace("%target%", targetJid);
                    sender.sendMessage(msg);
                } else {
                    String msg = plugin.getConfigManager().getMessage("sent-failed")
                            .replace("%error%", "Failed to send (Check console)");
                    sender.sendMessage(msg);
                }
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("report")) {
            if (!sender.hasPermission("wakgcraft.report")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(plugin.getConfigManager().getMessage("usage-report"));
                return true;
            }

            String targetPlayer = args[1];
            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            String adminJid = plugin.getChannelJid("AdminAlerts", true, false);

            if (adminJid != null) {
                String locationStr = sender instanceof Player ? 
                    ((Player) sender).getLocation().getBlockX() + ", " + 
                    ((Player) sender).getLocation().getBlockY() + ", " + 
                    ((Player) sender).getLocation().getBlockZ() : "Console";
                    
                String reportMsg = "🚨 *NEW PLAYER REPORT* 🚨\n\n" +
                                   "👤 *Reporter*: " + sender.getName() + " (Loc: " + locationStr + ")\n" +
                                   "🎯 *Suspect*: " + targetPlayer + "\n" +
                                   "📝 *Reason*: " + reason;
                
                plugin.getWaClient().sendMessage(adminJid, reportMsg);
                sender.sendMessage(plugin.getConfigManager().getMessage("report-success"));
            } else {
                sender.sendMessage("§cAdmin alert channel is not configured correctly.");
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    /**
     * Sends a help message listing available commands based on the sender's permissions.
     */
    private void sendHelp(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("wakgcraft.admin");
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "━━━━ " + ChatColor.WHITE + ChatColor.BOLD + "WAKGCraft Commands" + ChatColor.GREEN + " ━━━━");
        sender.sendMessage("");
        
        // Built-in commands
        sender.sendMessage(ChatColor.GOLD + "  Built-in Commands:");
        if (isAdmin) {
            sender.sendMessage(ChatColor.YELLOW + "  /wakg reload " + ChatColor.GRAY + "- Reload configurations");
            sender.sendMessage(ChatColor.YELLOW + "  /wakg send <jid> <message> " + ChatColor.GRAY + "- Send message to WhatsApp");
        }
        if (sender.hasPermission("wakgcraft.report") || isAdmin) {
            sender.sendMessage(ChatColor.YELLOW + "  /wakg report <player> <reason> " + ChatColor.GRAY + "- Report a player");
        }
        sender.sendMessage(ChatColor.YELLOW + "  /wakg help " + ChatColor.GRAY + "- Show this help message");
        
        // Custom commands from commands.yml
        ConfigurationSection mcCmds = plugin.getConfigManager().getCommandsConfig().getConfigurationSection("minecraft-commands");
        if (mcCmds != null && !mcCmds.getKeys(false).isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "  Custom In-Game Commands:");
            
            for (String key : mcCmds.getKeys(false)) {
                String cmdName = mcCmds.getString(key + ".command", key);
                String description = mcCmds.getString(key + ".description", "No description");
                String permission = mcCmds.getString(key + ".permission", "");
                String usage = mcCmds.getString(key + ".usage", "");
                
                // Show command only if player has permission or is admin
                if (isAdmin || permission.isEmpty() || sender.hasPermission(permission)) {
                    String cmdDisplay = "/" + cmdName + (!usage.isEmpty() ? " " + usage : "");
                    sender.sendMessage(ChatColor.YELLOW + "  " + cmdDisplay + " " + ChatColor.GRAY + "- " + description);
                }
            }
        }
        
        // WhatsApp commands from commands.yml
        ConfigurationSection waCmds = plugin.getConfigManager().getCommandsConfig().getConfigurationSection("whatsapp-commands");
        ConfigurationSection builtIn = plugin.getConfigManager().getConfig().getConfigurationSection("whatsapp-to-minecraft.built-in-commands");
        String prefix = plugin.getConfigManager().getConfig().getString("whatsapp-to-minecraft.command-prefix", "!");

        if ((waCmds != null && !waCmds.getKeys(false).isEmpty()) || builtIn != null) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "  WhatsApp Custom Commands:");
            
            if (builtIn != null) {
                if (builtIn.getBoolean("list.enabled", true)) {
                    sender.sendMessage(ChatColor.GREEN + "  " + prefix + builtIn.getString("list.command", "list") + ChatColor.GRAY + " - View online players" + ChatColor.DARK_GRAY + " (Built-in)");
                }
                if (builtIn.getBoolean("status.enabled", true)) {
                    sender.sendMessage(ChatColor.GREEN + "  " + prefix + builtIn.getString("status.command", "status") + ChatColor.GRAY + " - View server status" + ChatColor.DARK_GRAY + " (Built-in)");
                }
                if (builtIn.getBoolean("help.enabled", true)) {
                    sender.sendMessage(ChatColor.GREEN + "  " + prefix + builtIn.getString("help.command", "help") + ChatColor.GRAY + " - Show help in WhatsApp" + ChatColor.DARK_GRAY + " (Built-in)");
                }
                if (isAdmin) {
                    if (builtIn.getBoolean("whitelist.enabled", true)) {
                        sender.sendMessage(ChatColor.GREEN + "  " + prefix + builtIn.getString("whitelist.command", "whitelist") + " <add|remove> <player> " + ChatColor.GRAY + "- Manage whitelist" + ChatColor.RED + " [Admin]" + ChatColor.DARK_GRAY + " (Built-in)");
                    }
                    if (builtIn.getBoolean("execute.enabled", true)) {
                        sender.sendMessage(ChatColor.GREEN + "  " + prefix + builtIn.getString("execute.command", "execute") + " <command> " + ChatColor.GRAY + "- Run console command" + ChatColor.RED + " [Admin]" + ChatColor.DARK_GRAY + " (Built-in)");
                    }
                }
            }

            if (waCmds != null) {
                for (String key : waCmds.getKeys(false)) {
                    boolean adminOnly = waCmds.getBoolean(key + ".admin-only", false);
                    if (adminOnly && !isAdmin) continue;

                    String cmdName = waCmds.getString(key + ".command", key);
                    String description = waCmds.getString(key + ".description", "No description");
                    String usage = waCmds.getString(key + ".usage", "");
                    
                    String cmdDisplay = prefix + cmdName + (!usage.isEmpty() ? " " + usage : "");
                    sender.sendMessage(ChatColor.GREEN + "  " + cmdDisplay + " " + ChatColor.GRAY + "- " + description + (adminOnly ? ChatColor.RED + " [Admin]" : ""));
                }
            }
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("");
    }
}
