package net.aikeigroup.wakgcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class WAClient {

    private final WAKGCraft plugin;
    private final HttpClient client;
    private final Gson gson;

    public WAClient(WAKGCraft plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    /**
     * Sends a text message to a specific JID using WA-AKG
     *
     * @param targetJid The destination WhatsApp number or Group ID.
     * @param text      The parsed text message to send.
     */
    public CompletableFuture<Boolean> sendMessage(String targetJid, String text) {
        String apiUrl = plugin.getConfigManager().getApiUrl();
        String sessionId = plugin.getConfigManager().getSessionId();
        String apiKey = plugin.getConfigManager().getApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            plugin.getLogger().warning("API Key is missing! Cannot send WhatsApp message.");
            return CompletableFuture.completedFuture(false);
        }

        // Endpoint: [POST] /messages/{sessionId}/{jid}/send
        String url = String.format("%s/messages/%s/%s/send", apiUrl, sessionId, targetJid);

        JsonObject payload = new JsonObject();
        JsonObject messageObj = new JsonObject();
        
        if (text != null) {
            text = text.replace("%nl%", "\n").replace("%newline%", "\n");
        }
        
        messageObj.addProperty("text", text);
        payload.add("message", messageObj);

        String jsonPayload = gson.toJson(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200 || response.statusCode() == 201) {
                        return true;
                    } else {
                        plugin.getLogger().warning("Failed to send message via WA-AKG. Status: " + response.statusCode() + " | Body: " + response.body());
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Exception while sending WhatsApp message", ex);
                    return false;
                });
    }
}
