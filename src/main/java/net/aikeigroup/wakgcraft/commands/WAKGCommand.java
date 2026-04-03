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
        if (!sender.hasPermission("wakgcraft.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.fullReload();
            sender.sendMessage(plugin.getConfigManager().getMessage("reloaded"));
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("send")) {
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
            String adminJid = plugin.getConfigManager().getConfig().getString("Channels.AdminAlerts");

            if (adminJid != null && !adminJid.isEmpty() && !adminJid.equals("ENTER_ADMIN_GROUP_JID_HERE")) {
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
            sender.sendMessage(ChatColor.GOLD + "  Custom Commands:");
            
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
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("");
    }
}
