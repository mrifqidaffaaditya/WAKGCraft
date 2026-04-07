package net.aikeigroup.wakgcraft.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    // Stores id (UUID or JID) -> timestamp of when they can use commands again
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * Checks if a user is currently on cooldown.
     * If they are not, it applies the cooldown and returns false.
     * @param id The user identifier (UUID or JID)
     * @param cooldownSeconds Cooldown duration in seconds
     * @return true if they are on cooldown, false otherwise.
     */
    public static boolean isOnCooldown(String id, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;

        long currentTime = System.currentTimeMillis();
        long expireTime = cooldowns.getOrDefault(id, 0L);

        if (currentTime < expireTime) {
            return true;
        }

        // Apply new cooldown
        cooldowns.put(id, currentTime + (cooldownSeconds * 1000L));
        return false;
    }

    /**
     * Gets remaining cooldown time in seconds.
     * @param id The user identifier.
     * @return remaining seconds, or 0 if none.
     */
    public static long getRemainingCooldown(String id) {
        long expireTime = cooldowns.getOrDefault(id, 0L);
        long currentTime = System.currentTimeMillis();

        if (currentTime >= expireTime) {
            return 0;
        }

        return (expireTime - currentTime) / 1000L;
    }
}
