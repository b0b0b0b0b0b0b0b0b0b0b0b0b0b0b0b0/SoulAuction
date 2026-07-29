package bm.b0b0b0.soulAuction.util;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class MinecraftAvatarUrls {

    private MinecraftAvatarUrls() {
    }

    public static String resolve(AuctionSettings.DiscordNotificationSettings discord, UUID playerId, String playerName) {
        if (playerId != null) {
            return byUuid(discord.avatarProvider, playerId);
        }
        if (playerName != null && !playerName.isBlank()) {
            return byName(discord.avatarProvider, playerName);
        }
        return null;
    }

    private static String byUuid(String provider, UUID playerId) {
        String normalized = playerId.toString().replace("-", "");
        String mode = provider == null ? "MINOTAR" : provider.trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "CRAFATAR" -> "https://crafatar.com/avatars/" + playerId + "?size=128&overlay=true";
            default -> "https://minotar.net/avatar/" + normalized + "/128.png";
        };
    }

    private static String byName(String provider, String playerName) {
        String encoded = URLEncoder.encode(playerName.trim(), StandardCharsets.UTF_8);
        String mode = provider == null ? "MINOTAR" : provider.trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "CRAFATAR" -> "https://minotar.net/avatar/" + encoded + "/128.png";
            default -> "https://minotar.net/avatar/" + encoded + "/128.png";
        };
    }
}
