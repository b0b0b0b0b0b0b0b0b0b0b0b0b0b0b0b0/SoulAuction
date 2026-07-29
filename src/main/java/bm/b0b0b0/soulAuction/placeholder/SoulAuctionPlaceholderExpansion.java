package bm.b0b0b0.soulAuction.placeholder;

import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class SoulAuctionPlaceholderExpansion extends PlaceholderExpansion {

    private final AuctionService auctionService;

    public SoulAuctionPlaceholderExpansion(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "soulauction";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SoulAuction";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String normalized = params.toLowerCase(Locale.ROOT);
        if (normalized.equals("listings_total")) {
            return String.valueOf(auctionService.totalListingsCount());
        }
        if (player == null || player.getUniqueId() == null) {
            return "0";
        }
        if (normalized.equals("claims")) {
            return String.valueOf(auctionService.pendingClaims(player.getUniqueId()));
        }
        if (normalized.equals("listings_all")) {
            return String.valueOf(auctionService.activeListingsCount(player.getUniqueId(), null));
        }
        if (normalized.startsWith("listings_")) {
            String auctionId = normalized.substring("listings_".length());
            return String.valueOf(auctionService.activeListingsCount(player.getUniqueId(), auctionId));
        }
        return null;
    }
}
