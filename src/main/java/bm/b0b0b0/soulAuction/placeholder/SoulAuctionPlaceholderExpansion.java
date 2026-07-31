package bm.b0b0b0.soulAuction.placeholder;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionStatType;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.NumberDisplayFormat;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public final class SoulAuctionPlaceholderExpansion extends PlaceholderExpansion {

    private final AuctionService auctionService;
    private final MessageService messageService;

    public SoulAuctionPlaceholderExpansion(AuctionService auctionService, MessageService messageService) {
        this.auctionService = auctionService;
        this.messageService = messageService;
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
        return "1.1";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String normalized = params.toLowerCase(Locale.ROOT);
        Locale locale = player == null ? Locale.ROOT : messageService.javaLocale(player.getUniqueId());
        boolean raw = normalized.endsWith("_raw");
        String key = raw ? normalized.substring(0, normalized.length() - "_raw".length()) : normalized;
        String global = globalValue(key, raw, locale);
        if (global != null) {
            return global;
        }
        if (player == null) {
            return "0";
        }
        String playerValue = playerValue(player, key, raw, locale);
        if (playerValue != null) {
            return playerValue;
        }
        if (normalized.startsWith("listings_")) {
            String auctionId = normalized.substring("listings_".length());
            return String.valueOf(auctionService.activeListingsCount(player.getUniqueId(), auctionId));
        }
        return null;
    }

    private String globalValue(String key, boolean raw, Locale locale) {
        switch (key) {
            case "listings_total" -> {
                return String.valueOf(auctionService.totalListingsCount());
            }
            case "total_active_count" -> {
                return number(auctionService.totalListingsCount(), raw, locale);
            }
            case "categories_enabled", "expired_items_enabled", "auction_listing_confirmation_enabled" -> {
                return "true";
            }
            default -> {
                if (key.startsWith("total_")) {
                    return statValue(null, key.substring("total_".length()), raw, locale);
                }
                return null;
            }
        }
    }

    private String playerValue(OfflinePlayer player, String key, boolean raw, Locale locale) {
        UUID playerId = player.getUniqueId();
        switch (key) {
            case "claims" -> {
                return String.valueOf(auctionService.pendingClaims(playerId));
            }
            case "listings_all" -> {
                return String.valueOf(auctionService.activeListingsCount(playerId, null));
            }
            case "active_count", "sell_count", "purchasable_count" -> {
                return number(auctionService.activeListingsCount(playerId, null), raw, locale);
            }
            case "expired_count" -> {
                return number(auctionService.expiredClaimsCount(playerId), raw, locale);
            }
            case "sell_limit" -> {
                return number(auctionService.sellLimit(player), raw, locale);
            }
            case "selected_sorting" -> {
                AuctionService.BrowseSelection selection = auctionService.browseSelection(playerId);
                return raw
                        ? selection.sort().name().toLowerCase(Locale.ROOT)
                        : messageService.raw(playerId, selection.sort().messageKey());
            }
            case "selected_category" -> {
                AuctionService.BrowseSelection selection = auctionService.browseSelection(playerId);
                return raw
                        ? selection.category().name().toLowerCase(Locale.ROOT)
                        : messageService.raw(playerId, selection.category().messageKey());
            }
            case "selected_currency" -> {
                AuctionService.BrowseSelection selection = auctionService.browseSelection(playerId);
                AuctionEconomyType economyType = auctionService.economyType(selection.auctionId());
                return raw
                        ? economyType.name().toLowerCase(Locale.ROOT)
                        : messageService.raw(playerId, economyType.messageKey());
            }
            default -> {
                return statValue(playerId, key, raw, locale);
            }
        }
    }

    private String statValue(UUID playerId, String key, boolean raw, Locale locale) {
        AuctionStatType type = statType(key);
        if (type == null) {
            return null;
        }
        String base = statBase(type);
        String currency = key.length() > base.length() ? key.substring(base.length() + 1) : null;
        long value = playerId == null
                ? auctionService.globalDealStat(type, currency)
                : auctionService.dealStat(playerId, type, currency);
        return number(value, raw, locale);
    }

    private static AuctionStatType statType(String key) {
        for (AuctionStatType type : AuctionStatType.values()) {
            String base = statBase(type);
            if (key.equals(base) || key.startsWith(base + "_")) {
                return type;
            }
        }
        return null;
    }

    private static String statBase(AuctionStatType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static String number(long value, boolean raw, Locale locale) {
        if (raw) {
            return String.valueOf(value);
        }
        return NumberDisplayFormat.grouped(value, locale);
    }
}
