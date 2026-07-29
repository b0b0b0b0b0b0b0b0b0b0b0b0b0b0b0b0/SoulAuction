package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.gui.AuctionBrowserMenu;
import bm.b0b0b0.soulAuction.gui.PlayerRecordsMenu;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.PlayerHistoryView;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionCommand implements CommandExecutor {

    private static final String PERMISSION_AH = "soulauction.command.ah";
    private static final String PERMISSION_SELL = "soulauction.command.sell";
    private static final String PERMISSION_MY = "soulauction.command.my";
    private static final String PERMISSION_CLAIM = "soulauction.command.claim";
    private static final String PERMISSION_CANCEL_ANY = "soulauction.command.cancel.any";
    private static final String PERMISSION_LIMIT = "soulauction.command.limit";
    private static final String PERMISSION_RELOAD = "soulauction.command.reload";

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final MessageService messageService;
    private final AuctionService auctionService;
    private final Runnable reloadAction;

    public AuctionCommand(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            MessageService messageService,
            AuctionService auctionService,
            Runnable reloadAction
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.messageService = messageService;
        this.auctionService = auctionService;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("limit")) {
            return handleLimit(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageService.component("error-only-player"));
            return true;
        }
        if (!player.hasPermission(PERMISSION_AH)) {
            player.sendMessage(messageService.component("error-no-permission"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
            return handleSell(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("my")) {
            return handleMy(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("claim")) {
            return handleClaim(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            return handleCancel(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("selling")) {
            return openRecordsGui(player, args, PlayerHistoryView.SELLING);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("expired")) {
            return openRecordsGui(player, args, PlayerHistoryView.EXPIRED);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("purchased")) {
            return openRecordsGui(player, args, PlayerHistoryView.PURCHASED);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("history")) {
            return openRecordsGui(player, args, PlayerHistoryView.MY_SALES);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("search")) {
            return handleSearch(player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("page")) {
            return handlePage(player, args);
        }
        String auctionId = args.length > 0 ? args[0] : auctionService.defaultAuctionId();
        return openAuction(player, auctionId);
    }

    private boolean openRecordsGui(Player player, String[] args, PlayerHistoryView view) {
        if (!player.hasPermission(PERMISSION_MY)) {
            player.sendMessage(messageService.component("error-no-permission"));
            return true;
        }
        String auctionId = args.length > 1 ? args[1] : auctionService.defaultAuctionId();
        if (!auctionService.auctionExists(auctionId)) {
            player.sendMessage(messageService.component("error-auction-not-found"));
            return true;
        }
        PlayerRecordsMenu menu = new PlayerRecordsMenu(player.getUniqueId(), auctionId, view, auctionService, messageService);
        PluginSchedulers.run(plugin, player, () -> player.openInventory(menu.getInventory()));
        return true;
    }

    private boolean handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageService.component("error-search-usage"));
            return true;
        }
        String auctionId = auctionService.defaultAuctionId();
        String query;
        if (args.length >= 3 && auctionService.auctionExists(args[1])) {
            auctionId = args[1];
            query = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        } else {
            query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        if (query.isBlank()) {
            player.sendMessage(messageService.component("error-search-usage"));
            return true;
        }
        auctionService.setBrowsePreferences(player.getUniqueId(), new AuctionService.BrowsePreferences(auctionId, 0, query));
        return openAuction(player, auctionId);
    }

    private boolean handlePage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageService.component("error-page-usage"));
            return true;
        }
        int page;
        try {
            page = Integer.parseInt(args[1]) - 1;
        } catch (NumberFormatException exception) {
            player.sendMessage(messageService.component("error-page-usage"));
            return true;
        }
        if (page < 0) {
            player.sendMessage(messageService.component("error-page-usage"));
            return true;
        }
        String auctionId = args.length > 2 ? args[2] : auctionService.defaultAuctionId();
        auctionService.setBrowsePreferences(player.getUniqueId(), new AuctionService.BrowsePreferences(auctionId, page, null));
        return openAuction(player, auctionId);
    }

    private boolean handleReload(CommandSender sender) {
        if (sender instanceof Player player) {
            if (!player.hasPermission(PERMISSION_RELOAD)) {
                player.sendMessage(messageService.component("error-no-permission"));
                return true;
            }
        }
        reloadAction.run();
        sender.sendMessage(messageService.component("success-reloaded"));
        return true;
    }

    private boolean handleLimit(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_LIMIT)) {
            sender.sendMessage(messageService.component("error-no-permission"));
            return true;
        }
        if (args.length != 3 && args.length != 4) {
            sender.sendMessage(messageService.component("error-limit-usage"));
            return true;
        }
        int limit;
        try {
            limit = Integer.parseInt(args[args.length - 1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(messageService.component("error-limit-usage"));
            return true;
        }
        if (limit < 0) {
            sender.sendMessage(messageService.component("error-limit-usage"));
            return true;
        }
        OfflinePlayer target;
        String scope;
        if (args.length == 3) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messageService.component("error-limit-usage"));
                return true;
            }
            target = player;
            scope = args[1];
        } else {
            target = Bukkit.getOfflinePlayer(args[1]);
            scope = args[2];
        }
        if (target.getUniqueId() == null) {
            sender.sendMessage(messageService.component("error-limit-target"));
            return true;
        }
        auctionService.setLimitOverride(target.getUniqueId(), scope.toLowerCase(), limit);
        sender.sendMessage(messageService.component(
                "success-limit-set",
                Map.of("player", target.getName() == null ? target.getUniqueId().toString() : target.getName(), "scope", scope.toLowerCase(), "limit", String.valueOf(limit))
        ));
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_SELL)) {
            player.sendMessage(messageService.component("error-no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(messageService.component("error-invalid-price"));
            return true;
        }
        String auctionId;
        int price;
        if (args.length == 2) {
            auctionId = auctionService.defaultAuctionId();
            price = parsePrice(args[1]);
        } else {
            int maybePriceFirst = parsePrice(args[1]);
            int maybePriceSecond = parsePrice(args[2]);
            if (maybePriceFirst > 0) {
                price = maybePriceFirst;
                auctionId = args[2];
            } else if (maybePriceSecond > 0) {
                auctionId = args[1];
                price = maybePriceSecond;
            } else {
                player.sendMessage(messageService.component("error-invalid-price"));
                return true;
            }
        }
        if (price <= 0) {
            player.sendMessage(messageService.component("error-invalid-price"));
            return true;
        }
        AuctionService.SellResult result = auctionService.createListing(player, auctionId, price);
        if (!result.success()) {
            sendSellError(player, result.failure());
            return true;
        }
        player.sendMessage(messageService.component(
                "success-listed",
                Map.of("price", auctionService.formatPrice(result.listing().price(), result.listing().economyType()))
        ));
        return true;
    }

    private boolean handleMy(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_MY)) {
            player.sendMessage(messageService.component("error-no-permission"));
            return true;
        }
        String auctionId = args.length > 1 ? args[1] : null;
        List<AuctionListing> listings = auctionService.myListings(player.getUniqueId(), auctionId);
        player.sendMessage(messageService.component("my-listings-header", Map.of("count", String.valueOf(listings.size()))));
        int limit = Math.min(listings.size(), 15);
        for (int i = 0; i < limit; i++) {
            AuctionListing listing = listings.get(i);
            player.sendMessage(messageService.component(
                    "my-listing-line",
                    Map.of(
                            "id", String.valueOf(listing.listingId()),
                            "auction", listing.auctionId(),
                            "price", auctionService.formatPrice(listing.price(), listing.economyType())
                    )
            ));
        }
        if (listings.isEmpty()) {
            player.sendMessage(messageService.component("my-listings-empty"));
        }
        return true;
    }

    private boolean handleClaim(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_CLAIM)) {
            player.sendMessage(messageService.component("error-no-permission"));
            return true;
        }
        boolean claimAll = args.length > 1 && args[1].equalsIgnoreCase("all");
        AuctionService.ClaimResult result = auctionService.claim(player, claimAll);
        if (result.claimed() == 0 && result.failed() == 0) {
            player.sendMessage(messageService.component("claim-empty"));
            return true;
        }
        player.sendMessage(messageService.component(
                "claim-result",
                Map.of("claimed", String.valueOf(result.claimed()), "failed", String.valueOf(result.failed()))
        ));
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageService.component("error-cancel-usage"));
            return true;
        }
        long listingId;
        try {
            listingId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            player.sendMessage(messageService.component("error-cancel-usage"));
            return true;
        }
        boolean canCancelAny = player.hasPermission(PERMISSION_CANCEL_ANY);
        AuctionService.CancelResult result = auctionService.cancelListing(player, listingId, canCancelAny);
        if (!result.success()) {
            if (result.failure() == AuctionService.CancelFailure.NOT_OWNER) {
                player.sendMessage(messageService.component("error-cancel-not-owner"));
            } else {
                player.sendMessage(messageService.component("error-listing-unavailable"));
            }
            return true;
        }
        Component message = result.movedToClaim()
                ? messageService.component("cancelled-to-claim")
                : messageService.component("cancelled-and-returned");
        player.sendMessage(message);
        return true;
    }

    private boolean openAuction(Player player, String auctionId) {
        return openAuctionWithPreferences(player, auctionId);
    }

    private boolean openAuctionWithPreferences(Player player, String auctionId) {
        if (!auctionService.auctionExists(auctionId)) {
            player.sendMessage(messageService.component("error-auction-not-found"));
            return true;
        }
        if (!auctionService.canOpenAuction(player, auctionId)) {
            player.sendMessage(messageService.component("error-open-auction-denied"));
            return true;
        }
        var prefs = auctionService.consumeBrowsePreferences(player.getUniqueId());
        int page = prefs.map(AuctionService.BrowsePreferences::page).orElse(0);
        String search = prefs.map(AuctionService.BrowsePreferences::searchQuery).orElse(null);
        AuctionBrowserMenu menu = new AuctionBrowserMenu(
                player.getUniqueId(),
                auctionId,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings(),
                page,
                search
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(menu.getInventory()));
        return true;
    }

    private int parsePrice(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void sendSellError(Player player, AuctionService.SellFailure failure) {
        String key = switch (failure) {
            case SELL_DISABLED -> "error-sell-disabled";
            case SELL_LOCK_FAILED -> "error-sell-lock-failed";
            case SELL_DISABLED_IN_AUCTION -> "error-sell-disabled-in-auction";
            case SELL_PERMISSION_DENIED -> "error-sell-auction-denied";
            case AUCTION_NOT_FOUND -> "error-auction-not-found";
            case INVALID_PRICE -> "error-invalid-price";
            case ECONOMY_UNAVAILABLE -> "error-economy-unavailable";
            case AUCTION_LIMIT_REACHED -> "error-auction-limit";
            case GLOBAL_LIMIT_REACHED -> "error-global-limit";
            case BLOCKED_ITEM -> "error-blocked-item";
            case EMPTY_HAND -> "error-main-hand-empty";
            case PRICE_TOO_LOW -> "error-price-too-low";
            case PRICE_TOO_HIGH -> "error-price-too-high";
        };
        player.sendMessage(messageService.component(key));
    }
}
