package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.gui.AuctionBrowserMenu;
import bm.b0b0b0.soulAuction.gui.PlayerRecordsMenu;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.PlayerHistoryView;
import bm.b0b0b0.soulAuction.model.result.CancelFailure;
import bm.b0b0b0.soulAuction.model.result.CancelResult;
import bm.b0b0b0.soulAuction.model.result.ClaimResult;
import bm.b0b0b0.soulAuction.model.result.SellFailure;
import bm.b0b0b0.soulAuction.model.result.SellResult;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowseFilterState;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
    private static final String PERMISSION_ADMIN = "soulauction.command.admin";
    private static final String PERMISSION_VIEW = "soulauction.command.view";

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final MessageService messageService;
    private final AuctionService auctionService;
    private final Runnable reloadAction;
    private final AuctionAdminCommand adminCommand;

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
        this.adminCommand = new AuctionAdminCommand(plugin, configSupplier, messageService, auctionService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("limit")) {
            return handleLimit(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("purge")) {
            if (!auctionService.isLoaded()) {
                messageService.send(sender, "error-still-loading");
                return true;
            }
            return adminCommand.purge(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!auctionService.isLoaded() && !isAdminReadOnly(args)) {
                messageService.send(sender, "error-still-loading");
                return true;
            }
            return adminCommand.handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error-only-player");
            return true;
        }
        if (!player.hasPermission(PERMISSION_AH)) {
            messageService.send(player, "error-no-permission");
            return true;
        }
        if (!auctionService.isLoaded()) {
            messageService.send(player, "error-still-loading");
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
        if (args.length > 0 && args[0].equalsIgnoreCase("view")) {
            return handleView(player, args);
        }
        String auctionId = args.length > 0 ? args[0] : auctionService.defaultAuctionId();
        return openAuction(player, auctionId);
    }

    private boolean openRecordsGui(Player player, String[] args, PlayerHistoryView view) {
        if (!player.hasPermission(PERMISSION_MY)) {
            messageService.send(player, "error-no-permission");
            return true;
        }
        String auctionId = args.length > 1 ? args[1] : auctionService.defaultAuctionId();
        if (!auctionService.auctionExists(auctionId)) {
            messageService.send(player, "error-auction-not-found");
            return true;
        }
        PlayerRecordsMenu menu = new PlayerRecordsMenu(player, auctionId, view, auctionService, messageService);
        PluginSchedulers.run(plugin, player, () -> player.openInventory(menu.getInventory()));
        return true;
    }

    private boolean cancelPendingSearch(Player player) {
        var pending = auctionService.consumePendingChatSearch(player.getUniqueId());
        if (pending.isEmpty()) {
            return true;
        }
        messageService.send(player, "search-chat-cancelled");
        return openAuction(player, pending.get().auctionId());
    }

    private boolean handleSearch(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
            return cancelPendingSearch(player);
        }
        if (args.length < 2) {
            messageService.send(player, "error-search-usage");
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
            messageService.send(player, "error-search-usage");
            return true;
        }
        auctionService.setBrowsePreferences(player.getUniqueId(), new AuctionService.BrowsePreferences(auctionId, 0, query));
        BrowseFilterState current = auctionService.browseFilterState(player.getUniqueId());
        auctionService.setBrowseFilterState(player.getUniqueId(), current.withSearch(query));
        return openAuction(player, auctionId);
    }

    private boolean handlePage(Player player, String[] args) {
        if (args.length < 2) {
            messageService.send(player, "error-page-usage");
            return true;
        }
        int page;
        try {
            page = Integer.parseInt(args[1]) - 1;
        } catch (NumberFormatException exception) {
            messageService.send(player, "error-page-usage");
            return true;
        }
        if (page < 0) {
            messageService.send(player, "error-page-usage");
            return true;
        }
        String auctionId = args.length > 2 ? args[2] : auctionService.defaultAuctionId();
        auctionService.setBrowsePreferences(player.getUniqueId(), new AuctionService.BrowsePreferences(auctionId, page, null));
        return openAuction(player, auctionId);
    }

    private boolean handleReload(CommandSender sender) {
        if (sender instanceof Player player) {
            if (!player.hasPermission(PERMISSION_RELOAD)) {
                messageService.send(player, "error-no-permission");
                return true;
            }
        }
        reloadAction.run();
        messageService.send(sender, "success-reloaded");
        return true;
    }

    private boolean handleLimit(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_LIMIT)) {
            messageService.send(sender, "error-no-permission");
            return true;
        }
        if (args.length != 3 && args.length != 4) {
            messageService.send(sender, "error-limit-usage");
            return true;
        }
        int limit;
        try {
            limit = Integer.parseInt(args[args.length - 1]);
        } catch (NumberFormatException exception) {
            messageService.send(sender, "error-limit-usage");
            return true;
        }
        if (limit < 0) {
            messageService.send(sender, "error-limit-usage");
            return true;
        }
        OfflinePlayer target;
        String scope;
        if (args.length == 3) {
            if (!(sender instanceof Player player)) {
                messageService.send(sender, "error-limit-usage");
                return true;
            }
            target = player;
            scope = args[1];
        } else {
            target = Bukkit.getOfflinePlayer(args[1]);
            scope = args[2];
        }
        if (target.getUniqueId() == null) {
            messageService.send(sender, "error-limit-target");
            return true;
        }
        auctionService.setLimitOverride(target.getUniqueId(), scope.toLowerCase(), limit);
        messageService.send(sender, 
                "success-limit-set",
                Map.of("player", target.getName() == null ? target.getUniqueId().toString() : target.getName(), "scope", scope.toLowerCase(), "limit", String.valueOf(limit))
        );
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_SELL)) {
            messageService.send(player, "error-no-permission");
            return true;
        }
        if (args.length < 2) {
            messageService.send(player, "error-sell-usage");
            return true;
        }
        SellArgs sellArgs = parseSellArgs(args);
        if (sellArgs == null) {
            messageService.send(player, "error-invalid-price");
            return true;
        }
        SellResult result = auctionService.createListing(player, sellArgs.auctionId(), sellArgs.price(), sellArgs.amount());
        if (!result.success()) {
            sendSellError(player, result.failure());
            return true;
        }
        auctionService.sendListingCreatedMessage(player, result.listing());
        return true;
    }

    private record SellArgs(String auctionId, int price, int amount) {
    }

    private SellArgs parseSellArgs(String[] args) {
        if (args.length == 2) {
            int price = parsePrice(args[1]);
            return price > 0 ? new SellArgs(auctionService.defaultAuctionId(), price, 0) : null;
        }
        if (args.length == 3) {
            int first = parsePrice(args[1]);
            int second = parsePrice(args[2]);
            if (first > 0 && auctionService.auctionExists(args[2])) {
                return new SellArgs(args[2], first, 0);
            }
            if (first > 0 && second > 0) {
                return new SellArgs(auctionService.defaultAuctionId(), first, second);
            }
            if (second > 0 && auctionService.auctionExists(args[1])) {
                return new SellArgs(args[1], second, 0);
            }
            return null;
        }
        if (args.length >= 4) {
            int price = parsePrice(args[1]);
            int amount = parsePrice(args[2]);
            String auctionId = args[3];
            if (price > 0 && amount > 0 && auctionService.auctionExists(auctionId)) {
                return new SellArgs(auctionId, price, amount);
            }
            if (auctionService.auctionExists(args[1])) {
                price = parsePrice(args[2]);
                amount = parsePrice(args[3]);
                if (price > 0 && amount > 0) {
                    return new SellArgs(args[1], price, amount);
                }
            }
        }
        return null;
    }

    private boolean handleMy(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_MY)) {
            messageService.send(player, "error-no-permission");
            return true;
        }
        String auctionId = args.length > 1 ? args[1] : null;
        List<AuctionListing> listings = auctionService.myListings(player.getUniqueId(), auctionId);
        messageService.send(player, "my-listings-header", Map.of("count", String.valueOf(listings.size())));
        int limit = Math.min(listings.size(), 15);
        for (int i = 0; i < limit; i++) {
            AuctionListing listing = listings.get(i);
            messageService.send(player, 
                    "my-listing-line",
                    Map.of(
                            "id", String.valueOf(listing.listingId()),
                            "auction", auctionService.auctionDisplayName(listing.auctionId()),
                            "price", auctionService.formatPrice(listing.price(), listing.auctionId(), player)
                    )
            );
        }
        if (listings.isEmpty()) {
            messageService.send(player, "my-listings-empty");
        }
        return true;
    }

    private boolean handleClaim(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_CLAIM)) {
            messageService.send(player, "error-no-permission");
            return true;
        }
        boolean claimAll = args.length > 1 && args[1].equalsIgnoreCase("all");
        boolean moneyClaimed = auctionService.claimPendingSalePayments(player);
        ClaimResult result = auctionService.claim(player, claimAll);
        if (moneyClaimed) {
            messageService.send(player, "success-claim-money-auto");
        }
        if (result.claimed() == 0 && result.failed() == 0 && !moneyClaimed) {
            messageService.send(player, "claim-empty");
            return true;
        }
        if (result.claimed() > 0 || result.failed() > 0) {
            messageService.send(player, 
                    "claim-result",
                    Map.of("claimed", String.valueOf(result.claimed()), "failed", String.valueOf(result.failed()))
            );
        }
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            messageService.send(player, "error-cancel-usage");
            return true;
        }
        long listingId;
        try {
            listingId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            messageService.send(player, "error-cancel-usage");
            return true;
        }
        boolean canCancelAny = player.hasPermission(PERMISSION_CANCEL_ANY);
        CancelResult result = auctionService.cancelListing(player, listingId, canCancelAny);
        if (!result.success()) {
            if (result.failure() == CancelFailure.NOT_OWNER) {
                messageService.send(player, "error-cancel-not-owner");
            } else {
                messageService.send(player, "error-listing-unavailable");
            }
            return true;
        }
        messageService.send(
                player,
                result.movedToClaim() ? "cancelled-to-claim" : "cancelled-and-returned"
        );
        return true;
    }

    private boolean openAuction(Player player, String auctionId) {
        return openAuctionWithPreferences(player, auctionId, false);
    }

    private boolean openAuctionWithPreferences(Player player, String auctionId, boolean keepSellerFilter) {
        if (!keepSellerFilter) {
            BrowseFilterState current = auctionService.browseFilterState(player.getUniqueId());
            if (current.sellerFilter() != null) {
                auctionService.setBrowseFilterState(player.getUniqueId(), current.withSellerFilter(null));
            }
        }
        if (!auctionService.auctionExists(auctionId)) {
            messageService.send(player, "error-auction-not-found");
            return true;
        }
        if (!auctionService.canOpenAuction(player, auctionId)) {
            messageService.send(player, "error-open-auction-denied");
            return true;
        }
        var prefs = auctionService.consumeBrowsePreferences(player.getUniqueId());
        int page = prefs.map(AuctionService.BrowsePreferences::page).orElse(0);
        String search = prefs.map(AuctionService.BrowsePreferences::searchQuery).orElse(null);
        if (search != null && !search.isBlank()) {
            BrowseFilterState current = auctionService.browseFilterState(player.getUniqueId());
            auctionService.setBrowseFilterState(player.getUniqueId(), current.withSearch(search));
        }
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

    private boolean handleView(Player player, String[] args) {
        if (!player.hasPermission(PERMISSION_VIEW)) {
            messageService.send(player, "error-no-permission");
            return true;
        }
        if (args.length < 2) {
            messageService.send(player, "error-view-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String auctionId = auctionService.defaultAuctionId();
        if (args.length >= 3 && auctionService.auctionExists(args[2])) {
            auctionId = args[2];
        }
        String displayName = target.getName() == null ? args[1] : target.getName();
        auctionService.setBrowseFilterState(
                player.getUniqueId(),
                BrowseFilterState.empty().withSellerFilter(target.getUniqueId())
        );
        messageService.send(player, "success-view-seller", Map.of("player", displayName));
        return openAuctionWithPreferences(player, auctionId, true);
    }

    private int parsePrice(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void sendSellError(Player player, SellFailure failure) {
        messageService.send(player, failure.messageKey());
    }

    private static boolean isAdminReadOnly(String[] args) {
        return args.length > 1 && args[1].equalsIgnoreCase("migrate");
    }
}
