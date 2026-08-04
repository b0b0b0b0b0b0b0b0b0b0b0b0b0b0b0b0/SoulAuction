package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.gui.region.RegionMarketMenu;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.model.result.CancelFailure;
import bm.b0b0b0.soulAuction.model.result.CancelResult;
import bm.b0b0b0.soulAuction.model.result.RegionSellResult;
import bm.b0b0b0.soulAuction.region.RegionMarketPermissions;
import bm.b0b0b0.soulAuction.service.region.RegionMarketPresentation;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionMarketCommandHandler {

    private static final String PERMISSION_CANCEL_ANY = "soulauction.command.cancel.any";

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final MessageService messageService;
    private final RegionMarketService regionMarketService;

    public RegionMarketCommandHandler(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            MessageService messageService,
            RegionMarketService regionMarketService
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.messageService = messageService;
        this.regionMarketService = regionMarketService;
    }

    public boolean handle(Player player, String[] args) {
        if (!regionMarketService.isOperational()) {
            messageService.send(player, "region-error-disabled");
            return true;
        }
        AuctionSettings.RegionMarketSettings settings = regionMarketService.settings();
        if (settings == null || !player.hasPermission(RegionMarketPermissions.COMMAND)) {
            messageService.send(player, "error-no-permission");
            return true;
        }
        if (args.length == 0) {
            return openBrowse(player, null);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "sell" -> handleSell(player, args);
            case "cancel" -> handleCancel(player, args);
            case "my" -> openBrowse(player, player.getUniqueId());
            case "clear" -> handleClear(player);
            default -> openBrowse(player, null);
        };
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (!regionMarketService.isOperational()) {
            return List.of();
        }
        AuctionSettings.RegionMarketSettings settings = regionMarketService.settings();
        if (settings == null || !player.hasPermission(RegionMarketPermissions.COMMAND)) {
            return List.of();
        }
        if (args.length <= 1) {
            return CommandSuggestions.filter(args.length == 0 ? "" : args[0], List.of("sell", "cancel", "my", "clear"));
        }
        String sub = args[0].equalsIgnoreCase("sell") ? "sell" : "";
        if (!sub.equals("sell")) {
            if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
                return List.of();
            }
            return List.of();
        }
        if (args.length == 2) {
            return CommandSuggestions.filter(args[1], regionMarketService.tabCompleteOwnedRegions(player, args[1]));
        }
        if (args.length == 3) {
            return CommandSuggestions.filter(args[2], regionMarketService.tabCompleteSellableAuctions(player, args[2]));
        }
        if (args.length == 4) {
            return CommandSuggestions.filter(args[3], List.of("100", "500", "1000", "5000", "10000"));
        }
        return List.of();
    }

    private boolean handleSell(Player player, String[] args) {
        if (!player.hasPermission(RegionMarketPermissions.SELL)) {
            messageService.send(player, "error-no-permission");
            return true;
        }
        if (args.length >= 4) {
            RegionRef region = regionMarketService.resolveSellerRegion(player, args[1]);
            if (region == null) {
                messageService.send(player, RegionMarketPresentation.sellChatInvalidRegionKey(regionMarketService.settings()));
                return true;
            }
            int price;
            try {
                price = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                messageService.send(player, "error-invalid-price");
                return true;
            }
            RegionSellResult result = regionMarketService.sell(player, region, args[2], price);
            if (!result.success()) {
                messageService.send(player, result.failure().messageKey());
                return true;
            }
            messageService.send(
                    player,
                    "region-success-listed",
                    java.util.Map.of(
                            "region", region.regionId(),
                            "world", region.worldName(),
                            "price", regionMarketService.formatPrice(price, args[2], player.getUniqueId()),
                            "id", String.valueOf(result.listing().listingId())
                    )
            );
            return true;
        }
        regionMarketService.sessionService().start(player.getUniqueId());
        messageService.send(player, RegionMarketPresentation.sellChatRegionKey(regionMarketService.settings()));
        return true;
    }

    private boolean openBrowse(Player player, java.util.UUID sellerFilter) {
        AuctionSettings.RegionMarketSettings settings = regionMarketService.settings();
        RegionMarketMenu menu = new RegionMarketMenu(
                player.getUniqueId(),
                regionMarketService,
                messageService,
                configSupplier.get().guiGeneralSettings(),
                settings,
                0,
                null,
                sellerFilter
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(menu.getInventory()));
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            messageService.send(player, "region-error-cancel-usage");
            return true;
        }
        long listingId;
        try {
            listingId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            messageService.send(player, "error-listing-unavailable");
            return true;
        }
        boolean canCancelAny = player.hasPermission(PERMISSION_CANCEL_ANY);
        CancelResult result = regionMarketService.cancelListing(player, listingId, canCancelAny);
        if (!result.success()) {
            if (result.failure() == CancelFailure.NOT_OWNER) {
                messageService.send(player, "error-cancel-not-owner");
            } else {
                messageService.send(player, "error-listing-unavailable");
            }
            return true;
        }
        messageService.send(player, "region-success-cancelled");
        return true;
    }

    private boolean handleClear(Player player) {
        regionMarketService.sessionService().clear(player.getUniqueId());
        messageService.send(player, "region-sell-chat-cancelled");
        return true;
    }
}
