package net.aikeigroup.wakgcraft.listeners;

import net.aikeigroup.wakgcraft.WAKGCraft;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final WAKGCraft plugin;

    public PlayerListener(WAKGCraft plugin) {
        this.plugin = plugin;
    }

    private String setPlaceholders(Player player, String text) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("minecraft-to-whatsapp.chat.enabled", false)) return;

        String targetJid = plugin.getConfigManager().getConfig().getString("Channels.GlobalChat");
        if (targetJid == null || targetJid.isEmpty() || targetJid.equals("ENTER_GLOBAL_CHAT_JID_HERE")) return;

        String format = plugin.getConfigManager().getConfig().getString("minecraft-to-whatsapp.chat.format", "[%player_name%] %message%");
        
        // Parse basic placeholders
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        format = format.replace("%player_name%", event.getPlayer().getName())
                       .replace("%message%", plainMessage);
                       
        // Parse PlaceholderAPI if available
        format = setPlaceholders(event.getPlayer(), format);

        plugin.getWaClient().sendMessage(targetJid, format);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("minecraft-to-whatsapp.player-join.enabled", false)) return;

        String targetJid = plugin.getConfigManager().getConfig().getString("Channels.GlobalChat");
        if (targetJid == null || targetJid.isEmpty() || targetJid.equals("ENTER_GLOBAL_CHAT_JID_HERE")) return;

        String format = plugin.getConfigManager().getConfig().getString("minecraft-to-whatsapp.player-join.format", "%player_name% joined the server");
        format = format.replace("%player_name%", event.getPlayer().getName());
        format = setPlaceholders(event.getPlayer(), format);

        plugin.getWaClient().sendMessage(targetJid, format);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("minecraft-to-whatsapp.player-quit.enabled", false)) return;

        String targetJid = plugin.getConfigManager().getConfig().getString("Channels.GlobalChat");
        if (targetJid == null || targetJid.isEmpty() || targetJid.equals("ENTER_GLOBAL_CHAT_JID_HERE")) return;

        String format = plugin.getConfigManager().getConfig().getString("minecraft-to-whatsapp.player-quit.format", "%player_name% left the server");
        format = format.replace("%player_name%", event.getPlayer().getName());
        format = setPlaceholders(event.getPlayer(), format);

        plugin.getWaClient().sendMessage(targetJid, format);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("minecraft-to-whatsapp.player-death.enabled", false)) return;

        String targetJid = plugin.getConfigManager().getConfig().getString("Channels.GlobalChat");
        if (targetJid == null || targetJid.isEmpty() || targetJid.equals("ENTER_GLOBAL_CHAT_JID_HERE")) return;

        String deathMsg = "";
        if (event.deathMessage() != null) {
             deathMsg = PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
        }

        String format = plugin.getConfigManager().getConfig().getString("minecraft-to-whatsapp.player-death.format", "%player_name% died: %death_message%");
        format = format.replace("%player_name%", event.getEntity().getName())
                       .replace("%death_message%", deathMsg);
        format = setPlaceholders(event.getEntity(), format);

        plugin.getWaClient().sendMessage(targetJid, format);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (event.getAdvancement().getKey().getKey().contains("recipes/")) return;
        
        if (!plugin.getConfigManager().getConfig().getBoolean("minecraft-to-whatsapp.advancement.enabled", false)) return;

        String targetJid = plugin.getConfigManager().getConfig().getString("Channels.GlobalChat");
        if (targetJid == null || targetJid.isEmpty() || targetJid.equals("ENTER_GLOBAL_CHAT_JID_HERE")) return;

        String advName = event.getAdvancement().getKey().getKey();
        if (event.getAdvancement().getDisplay() != null && event.getAdvancement().getDisplay().title() != null) {
            advName = PlainTextComponentSerializer.plainText().serialize(event.getAdvancement().getDisplay().title());
        }

        String format = plugin.getConfigManager().getConfig().getString("minecraft-to-whatsapp.advancement.format", "%player_name% made advancement [%advancement%]!");
        format = format.replace("%player_name%", event.getPlayer().getName())
                       .replace("%advancement%", advName);
        format = setPlaceholders(event.getPlayer(), format);

        plugin.getWaClient().sendMessage(targetJid, format);
    }
}
