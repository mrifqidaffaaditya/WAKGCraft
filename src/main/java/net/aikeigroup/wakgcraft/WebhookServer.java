package net.aikeigroup.wakgcraft;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.aikeigroup.wakgcraft.utils.ArgsParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class WebhookServer {

    private final WAKGCraft plugin;
    private final int port;
    private HttpServer server;
    private final Gson gson;

    public WebhookServer(WAKGCraft plugin, int port) {
        this.plugin = plugin;
        this.port = port;
        this.gson = new Gson();
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/webhook", new WebhookHandler());
            // Use a basic executor to handle requests asynchronously
            server.setExecutor(Executors.newCachedThreadPool()); 
            server.start();
            plugin.getLogger().info("Webhook Server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start Webhook Server on port " + port, e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Webhook Server stopped.");
        }
    }

    private class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);// 405 Method Not Allowed
                return;
            }

            try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
                // Parse Incoming JSON from WA-AKG
                JsonObject payload = gson.fromJson(reader, JsonObject.class);

                if (plugin.getConfigManager().getConfig().getBoolean("whatsapp-to-minecraft.webhook.debug", false)) {
                    plugin.getLogger().info("[Webhook Debug] Received Payload: " + payload.toString());
                }

                // Assuming the webhook sends standard Baileys message objects or custom structured events
                // Typically WA-AKG webhook sends { "event": "messages.upsert", "data": { ... } }
                // Let's make an automated router based on what's available
                processMessage(payload);

                String response = "{\"status\":\"ok\"}";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error processing webhook payload", e);
                exchange.sendResponseHeaders(500, -1);
            }
        }

        private void processMessage(JsonObject payload) {
            if (payload.has("event") && payload.get("event").getAsString().startsWith("message.")) {
                // New WA-AKG Webhook API format
                if (!payload.has("data")) return;
                JsonObject data = payload.getAsJsonObject("data");
                if (!data.has("key") || !data.has("content")) return;
                
                JsonObject key = data.getAsJsonObject("key");
                if (key.has("fromMe") && !key.get("fromMe").isJsonNull() && key.get("fromMe").getAsBoolean()) return;
                
                String remoteJid = key.has("remoteJid") && !key.get("remoteJid").isJsonNull() ? key.get("remoteJid").getAsString() : "";
                if (remoteJid.isEmpty() && data.has("from") && !data.get("from").isJsonNull()) {
                    remoteJid = data.get("from").getAsString();
                }
                
                String pushName = data.has("pushName") && !data.get("pushName").isJsonNull() ? data.get("pushName").getAsString() : "WhatsApp User";
                String text = data.has("content") && !data.get("content").isJsonNull() ? data.get("content").getAsString() : "";
                
                String participant = remoteJid;
                if (key.has("participant") && key.get("participant").isJsonObject()) {
                    JsonObject partObj = key.getAsJsonObject("participant");
                    if (partObj.has("phoneNumber") && !partObj.get("phoneNumber").isJsonNull()) {
                        participant = partObj.get("phoneNumber").getAsString();
                    } else if (partObj.has("id") && !partObj.get("id").isJsonNull()) {
                        participant = partObj.get("id").getAsString();
                    }
                } else if (key.has("participant") && key.get("participant").isJsonPrimitive()) {
                    participant = key.get("participant").getAsString();
                }
                
                if (!text.isEmpty()) {
                    handleIncomingMessage(remoteJid, participant, pushName, text);
                }
            } else if (payload.has("messages")) {
                // Old Baileys raw messages structure
                payload.getAsJsonArray("messages").forEach(msgElement -> {
                    JsonObject msg = msgElement.getAsJsonObject();
                    if (!msg.has("key") || !msg.has("message")) return;

                    JsonObject key = msg.getAsJsonObject("key");
                    if (key.has("fromMe") && !key.get("fromMe").isJsonNull() && key.get("fromMe").getAsBoolean()) return;

                    String remoteJid = key.get("remoteJid").getAsString();
                    String participant = key.has("participant") && !key.get("participant").isJsonNull() ? key.get("participant").getAsString() : remoteJid;
                    String pushName = msg.has("pushName") && !msg.get("pushName").isJsonNull() ? msg.get("pushName").getAsString() : "WhatsApp User";
                    
                    String text = extractText(msg.getAsJsonObject("message"));
                    if (!text.isEmpty()) {
                        handleIncomingMessage(remoteJid, participant, pushName, text);
                    }
                });
            }
        }

        private void handleIncomingMessage(String remoteJid, String participant, String pushName, String text) {
            String prefix = plugin.getConfigManager().getConfig().getString("whatsapp-to-minecraft.command-prefix", "!");
            
            // Process Commands (e.g., !list)
            if (text.startsWith(prefix)) {
                // Rate Limiting (Skip if admin)
                boolean isAdmin = plugin.getConfigManager().getConfig().getStringList("whatsapp-to-minecraft.admin-jids").contains(remoteJid) || 
                                  plugin.getConfigManager().getConfig().getStringList("whatsapp-to-minecraft.admin-jids").contains(participant);
                
                if (!isAdmin) {
                    int cooldownSecs = plugin.getConfigManager().getConfig().getInt("general.command-cooldown", 0);
                    if (net.aikeigroup.wakgcraft.utils.CooldownManager.isOnCooldown(remoteJid, cooldownSecs)) {
                        long remaining = net.aikeigroup.wakgcraft.utils.CooldownManager.getRemainingCooldown(remoteJid);
                        String formatMsg = plugin.getConfigManager().getConfig().getString("general.cooldown-message", "⏳ Please wait %time%s before using commands again!");
                        plugin.getWaClient().sendMessage(remoteJid, formatMsg.replace("%time%", String.valueOf(remaining)));
                        return; // Block command
                    }
                }

                String[] args = text.substring(prefix.length()).split(" ");
                String cmd = args[0].toLowerCase();
                
                ConfigurationSection builtIn = plugin.getConfigManager().getConfig().getConfigurationSection("whatsapp-to-minecraft.built-in-commands");

                String cmdList      = builtIn != null ? builtIn.getString("list.command", "list").toLowerCase() : "list";
                boolean enabledList = builtIn == null || builtIn.getBoolean("list.enabled", true);
                String cmdStatus      = builtIn != null ? builtIn.getString("status.command", "status").toLowerCase() : "status";
                boolean enabledStatus = builtIn == null || builtIn.getBoolean("status.enabled", true);
                String cmdWhitelist      = builtIn != null ? builtIn.getString("whitelist.command", "whitelist").toLowerCase() : "whitelist";
                boolean enabledWhitelist = builtIn == null || builtIn.getBoolean("whitelist.enabled", true);
                String cmdExecute      = builtIn != null ? builtIn.getString("execute.command", "execute").toLowerCase() : "execute";
                boolean enabledExecute = builtIn == null || builtIn.getBoolean("execute.enabled", true);
                String cmdHelp      = builtIn != null ? builtIn.getString("help.command", "help").toLowerCase() : "help";
                boolean enabledHelp = builtIn == null || builtIn.getBoolean("help.enabled", true);

                if (enabledList && cmd.equals(cmdList)) {
                    java.util.List<String> allowed = builtIn != null ? builtIn.getStringList("list.channels") : new java.util.ArrayList<>();
                    if (!allowed.isEmpty() && !plugin.isJidInChannels(remoteJid, allowed)) return;

                    int count = Bukkit.getOnlinePlayers().size();
                    String headerFormat = builtIn != null ? builtIn.getString("list.format.header", "👥 *Online Players (%online_count%/%max_players%)*%nl%") : "👥 *Online Players (%online_count%/%max_players%)*%nl%";
                    String header = headerFormat.replace("%online_count%", String.valueOf(count)).replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers())).replace("%nl%", "\n");
                    StringBuilder sb = new StringBuilder(header);
                    
                    String playerFormat = builtIn != null ? builtIn.getString("list.format.player-item", "- %player_name%%nl%") : "- %player_name%%nl%";
                    Bukkit.getOnlinePlayers().forEach(p -> sb.append(playerFormat.replace("%player_name%", p.getName()).replace("%nl%", "\n")));
                    
                    if (count == 0) {
                        String emptyFormat = builtIn != null ? builtIn.getString("list.format.empty", "No players online") : "No players online";
                        sb.append(emptyFormat.replace("%nl%", "\n"));
                    }
                    plugin.getWaClient().sendMessage(remoteJid, sb.toString());
                    return;
                }
                
                if (enabledStatus && cmd.equals(cmdStatus)) {
                    java.util.List<String> allowed = builtIn != null ? builtIn.getStringList("status.channels") : new java.util.ArrayList<>();
                    if (!allowed.isEmpty() && !plugin.isJidInChannels(remoteJid, allowed)) return;

                    double[] tps = Bukkit.getTPS();
                    String tpsStr = String.format("%.2f", tps[0]);
                    long maxMemory = Runtime.getRuntime().maxMemory() / 1048576;
                    long allocatedMemory = Runtime.getRuntime().totalMemory() / 1048576;
                    long freeMemory = Runtime.getRuntime().freeMemory() / 1048576;
                    int count = Bukkit.getOnlinePlayers().size();
                    
                    String format = builtIn != null ? builtIn.getString("status.format", "📊 *Server Status*%nl%• TPS: *%tps%*%nl%• Players: *%online_count%/%max_players%*%nl%• Memory: *%used_memory%MB / %max_memory%MB*") : "📊 *Server Status*%nl%• TPS: *%tps%*%nl%• Players: *%online_count%/%max_players%*%nl%• Memory: *%used_memory%MB / %max_memory%MB*";
                    
                    String statusMsg = format.replace("%tps%", tpsStr)
                                             .replace("%online_count%", String.valueOf(count))
                                             .replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers()))
                                             .replace("%used_memory%", String.valueOf(allocatedMemory - freeMemory))
                                             .replace("%max_memory%", String.valueOf(maxMemory))
                                             .replace("%nl%", "\n");
                    plugin.getWaClient().sendMessage(remoteJid, statusMsg);
                    return;
                }

                if (enabledWhitelist && cmd.equals(cmdWhitelist)) {
                    java.util.List<String> allowed = builtIn != null ? builtIn.getStringList("whitelist.channels") : new java.util.ArrayList<>();
                    if (!allowed.isEmpty() && !plugin.isJidInChannels(remoteJid, allowed)) return;

                    List<String> admins = plugin.getConfigManager().getConfig().getStringList("whatsapp-to-minecraft.admin-jids");
                    if (admins.contains(participant) || admins.contains(remoteJid)) {
                        if (args.length >= 3) {
                            String action = args[1].toLowerCase();
                            String player = args[2];
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist " + action + " " + player);
                                String execFormat = builtIn != null ? builtIn.getString("whitelist.format.executing", "✅ Whitelist command executing for: %player%") : "✅ Whitelist command executing for: %player%";
                                plugin.getWaClient().sendMessage(remoteJid, execFormat.replace("%player%", player).replace("%nl%", "\n"));
                            });
                        } else {
                            String usageFormat = builtIn != null ? builtIn.getString("whitelist.format.usage", "❌ Usage: %prefix%whitelist <add|remove> <player>") : "❌ Usage: %prefix%whitelist <add|remove> <player>";
                            plugin.getWaClient().sendMessage(remoteJid, usageFormat.replace("%prefix%", prefix).replace("%nl%", "\n"));
                        }
                    } else {
                        String permFormat = builtIn != null ? builtIn.getString("whitelist.format.no-permission", "❌ You do not have permission.") : "❌ You do not have permission.";
                        plugin.getWaClient().sendMessage(remoteJid, permFormat.replace("%nl%", "\n"));
                    }
                    return;
                }
                
                if (enabledExecute && cmd.equals(cmdExecute)) {
                    java.util.List<String> allowed = builtIn != null ? builtIn.getStringList("execute.channels") : new java.util.ArrayList<>();
                    if (!allowed.isEmpty() && !plugin.isJidInChannels(remoteJid, allowed)) return;

                    List<String> admins = plugin.getConfigManager().getConfig().getStringList("whatsapp-to-minecraft.admin-jids");
                    
                    if (admins.contains(participant) || admins.contains(remoteJid)) {
                        String mcCommand = text.substring(prefix.length() + cmd.length()).trim();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), mcCommand);
                            String successFormat = builtIn != null ? builtIn.getString("execute.format.success", "⚙️ Command execution dispatched.") : "⚙️ Command execution dispatched.";
                            String failedFormat = builtIn != null ? builtIn.getString("execute.format.failed", "⚙️ Command execution failed.") : "⚙️ Command execution failed.";
                            plugin.getWaClient().sendMessage(remoteJid, (success ? successFormat : failedFormat).replace("%nl%", "\n"));
                        });
                    } else {
                        String permFormat = builtIn != null ? builtIn.getString("execute.format.no-permission", "❌ You do not have permission to run console commands.") : "❌ You do not have permission to run console commands.";
                        plugin.getWaClient().sendMessage(remoteJid, permFormat.replace("%nl%", "\n"));
                    }
                    return;
                }

                if (enabledHelp && cmd.equals(cmdHelp)) {
                    java.util.List<String> allowed = builtIn != null ? builtIn.getStringList("help.channels") : new java.util.ArrayList<>();
                    if (!allowed.isEmpty() && !plugin.isJidInChannels(remoteJid, allowed)) return;

                    handleHelpCommand(remoteJid, participant);
                    return;
                }
                
                // Parse custom WhatsApp commands from commands.yml
                ConfigurationSection waCmds = plugin.getConfigManager().getCommandsConfig().getConfigurationSection("whatsapp-commands");
                if (waCmds != null) {
                    for (String keyName : waCmds.getKeys(false)) {
                        String matchCmd = waCmds.getString(keyName + ".command", "");
                        if (cmd.equals(matchCmd.toLowerCase())) {
                            // Check if this command should respond from this channel
                            java.util.List<String> cmdChannels = waCmds.getStringList(keyName + ".channels");
                            if (!cmdChannels.isEmpty() && !plugin.isJidInChannels(remoteJid, cmdChannels)) {
                                // Message came from a channel this command doesn't listen to
                                continue;
                            }

                            boolean adminOnly = waCmds.getBoolean(keyName + ".admin-only", false);
                            
                            if (adminOnly) {
                                List<String> admins = plugin.getConfigManager().getConfig().getStringList("whatsapp-to-minecraft.admin-jids");
                                if (!admins.contains(participant) && !admins.contains(remoteJid)) {
                                    plugin.getWaClient().sendMessage(remoteJid, "❌ You do not have permission to use this command.");
                                    return;
                                }
                            }
                            
                            String executeConsole = waCmds.getString(keyName + ".execute-console", "");
                            String replyMsg = waCmds.getString(keyName + ".reply", "");
                            
                            String cmndArgs = text.substring(prefix.length() + cmd.length()).trim();
                            
                            // Parse with ArgsParser
                            if (!executeConsole.isEmpty()) {
                                String parsedConsole = ArgsParser.parse(executeConsole, cmndArgs);
                                if (parsedConsole == null) {
                                    String usage = waCmds.getString(keyName + ".usage", "");
                                    String errorMsg = "❌ Wrong usage!\n";
                                    errorMsg += "Command: " + prefix + matchCmd;
                                    if (!usage.isEmpty()) {
                                        errorMsg += " " + usage;
                                    }
                                    String example = waCmds.getString(keyName + ".example", "");
                                    if (!example.isEmpty()) {
                                        errorMsg += "\nExample: " + prefix + matchCmd + " " + example;
                                    }
                                    plugin.getWaClient().sendMessage(remoteJid, errorMsg);
                                    return;
                                }
                                String finalConsole = parsedConsole;
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalConsole);
                                });
                            }
                            
                            if (!replyMsg.isEmpty()) {
                                String parsedReply = ArgsParser.parse(replyMsg, cmndArgs);
                                if (parsedReply == null) parsedReply = replyMsg;
                                plugin.getWaClient().sendMessage(remoteJid, parsedReply);
                            }
                            return;
                        }
                    }
                }
            }

            // Two-way Chat Sync — check ALL channels
            ConfigurationSection channelsSection = plugin.getConfigManager().getConfig().getConfigurationSection("Channels");
            if (channelsSection != null) {
                for (String channelKey : channelsSection.getKeys(false)) {
                    String channelJid = plugin.getChannelJid(channelKey, false, true);
                    if (channelJid != null && remoteJid.equals(channelJid)) {
                        String format = plugin.getConfigManager().getConfig().getString("whatsapp-to-minecraft.chat-format", "&7[&aWA&7] &f%wa_name%&8: &7%message%");
                        String finalMessage = format.replace("%wa_name%", pushName).replace("%message%", text).replace("%channel%", channelKey);
                        finalMessage = ChatColor.translateAlternateColorCodes('&', finalMessage);
                        
                        String fMsg = finalMessage;
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(fMsg));
                        break;
                    }
                }
            }
        }

        private void handleHelpCommand(String remoteJid, String participant) {
            List<String> admins = plugin.getConfigManager().getConfig().getStringList("whatsapp-to-minecraft.admin-jids");
            boolean isAdmin = admins.contains(participant) || admins.contains(remoteJid);
            String prefix = plugin.getConfigManager().getConfig().getString("whatsapp-to-minecraft.command-prefix", "!");
            
            ConfigurationSection builtIn = plugin.getConfigManager().getConfig().getConfigurationSection("whatsapp-to-minecraft.built-in-commands");
            String headerFormat = builtIn != null ? builtIn.getString("help.format.header", "📋 *WAKGCraft Commands*%nl%━━━━━━━━━━━━━━━%nl%") : "📋 *WAKGCraft Commands*%nl%━━━━━━━━━━━━━━━%nl%";
            StringBuilder help = new StringBuilder(headerFormat.replace("%nl%", "\n"));
            
            // Built-in commands
            help.append("*Built-in Commands:*\n");
            if (builtIn == null || builtIn.getBoolean("list.enabled", true)) {
                help.append(prefix).append(builtIn != null ? builtIn.getString("list.command", "list") : "list").append(" - View online players\n");
            }
            if (builtIn == null || builtIn.getBoolean("status.enabled", true)) {
                help.append(prefix).append(builtIn != null ? builtIn.getString("status.command", "status") : "status").append(" - View server status\n");
            }
            if (builtIn == null || builtIn.getBoolean("help.enabled", true)) {
                help.append(prefix).append(builtIn != null ? builtIn.getString("help.command", "help") : "help").append(" - Show this help\n");
            }
            
            if (isAdmin) {
                help.append("\n*Admin Commands:*\n");
                if (builtIn == null || builtIn.getBoolean("execute.enabled", true)) {
                    help.append(prefix).append(builtIn != null ? builtIn.getString("execute.command", "execute") : "execute").append(" <command> - Run console command\n");
                }
                if (builtIn == null || builtIn.getBoolean("whitelist.enabled", true)) {
                    help.append(prefix).append(builtIn != null ? builtIn.getString("whitelist.command", "whitelist") : "whitelist").append(" <add|remove> <player> - Manage whitelist\n");
                }
            }
            
            // Custom commands
            ConfigurationSection waCmds = plugin.getConfigManager().getCommandsConfig().getConfigurationSection("whatsapp-commands");
            if (waCmds != null && !waCmds.getKeys(false).isEmpty()) {
                boolean headerShown = false;
                for (String keyName : waCmds.getKeys(false)) {
                    boolean adminOnly = waCmds.getBoolean(keyName + ".admin-only", false);
                    if (adminOnly && !isAdmin) continue;
                    
                    if (!headerShown) {
                        help.append("\n*Custom WhatsApp Commands:*\n");
                        headerShown = true;
                    }
                    
                    String cmdName = waCmds.getString(keyName + ".command", keyName);
                    String description = waCmds.getString(keyName + ".description", "No description");
                    String usage = waCmds.getString(keyName + ".usage", "");
                    
                    help.append(prefix).append(cmdName);
                    if (!usage.isEmpty()) help.append(" ").append(usage);
                    help.append(" - ").append(description);
                    if (adminOnly) help.append(" 🔒");
                    help.append("\n");
                }
            }

            // Custom Minecraft commands
            ConfigurationSection mcCmds = plugin.getConfigManager().getCommandsConfig().getConfigurationSection("minecraft-commands");
            if (mcCmds != null && !mcCmds.getKeys(false).isEmpty()) {
                help.append("\n*Custom In-Game Commands:*\n");
                for (String keyName : mcCmds.getKeys(false)) {
                    String permission = mcCmds.getString(keyName + ".permission", "");
                    String cmdName = mcCmds.getString(keyName + ".command", keyName);
                    String description = mcCmds.getString(keyName + ".description", "No description");
                    String usage = mcCmds.getString(keyName + ".usage", "");
                    
                    help.append("/").append(cmdName);
                    if (!usage.isEmpty()) help.append(" ").append(usage);
                    help.append(" - ").append(description);
                    if (!permission.isEmpty()) help.append(" 🔒"); // Requires permission
                    help.append("\n");
                }
            }
            
            String footerFormat = builtIn != null ? builtIn.getString("help.format.footer", "%nl%━━━━━━━━━━━━━━━") : "%nl%━━━━━━━━━━━━━━━";
            help.append(footerFormat.replace("%nl%", "\n"));
            plugin.getWaClient().sendMessage(remoteJid, help.toString());
        }

        private String extractText(JsonObject message) {
            if (message.has("conversation")) {
                return message.get("conversation").getAsString();
            } else if (message.has("extendedTextMessage")) {
                JsonObject extended = message.getAsJsonObject("extendedTextMessage");
                if (extended.has("text")) {
                    return extended.get("text").getAsString();
                }
            }
            return "";
        }
    }
}
