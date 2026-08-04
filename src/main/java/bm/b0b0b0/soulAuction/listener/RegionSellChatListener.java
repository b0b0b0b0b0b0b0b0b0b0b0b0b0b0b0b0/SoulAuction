package bm.b0b0b0.soulAuction.listener;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.model.result.EditDescriptionResult;
import bm.b0b0b0.soulAuction.model.result.RegionSellResult;
import bm.b0b0b0.soulAuction.service.region.RegionMarketPresentation;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
import bm.b0b0b0.soulAuction.service.region.RegionOwnerEditSessionService;
import bm.b0b0b0.soulAuction.service.region.RegionSellSessionService;
import bm.b0b0b0.soulAuction.service.region.RegionSellSessionService.Session;
import bm.b0b0b0.soulAuction.service.region.RegionSellSessionService.Step;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionSellChatListener implements Listener {

    private final JavaPlugin plugin;
    private final RegionMarketService regionMarketService;
    private final MessageService messageService;

    public RegionSellChatListener(
            JavaPlugin plugin,
            RegionMarketService regionMarketService,
            MessageService messageService
    ) {
        this.plugin = plugin;
        this.regionMarketService = regionMarketService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (regionMarketService.ownerEditSessionService().peekDescriptionEdit(player.getUniqueId()).isPresent()
                || regionMarketService.sessionService().peek(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
            String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            PluginSchedulers.run(plugin, player, () -> handleInput(player, text));
        }
    }

    private void handleInput(Player player, String text) {
        var ownerSession = regionMarketService.ownerEditSessionService().peekDescriptionEdit(player.getUniqueId());
        Session sellSession = regionMarketService.sessionService().peek(player.getUniqueId()).orElse(null);
        if (text.equalsIgnoreCase("cancel")) {
            if (ownerSession.isPresent()) {
                regionMarketService.ownerEditSessionService().clearDescriptionEdit(player.getUniqueId());
                messageService.send(player, "region-owner-description-cancelled");
                return;
            }
            if (sellSession != null) {
                regionMarketService.sessionService().clear(player.getUniqueId());
                messageService.send(player, "region-sell-chat-cancelled");
            }
            return;
        }
        if (ownerSession.isPresent()) {
            handleOwnerDescription(player, ownerSession.get(), text);
            return;
        }
        if (sellSession == null) {
            return;
        }
        if (sellSession.step() == Step.REGION) {
            handleSellRegion(player, text);
            return;
        }
        if (sellSession.step() == Step.AUCTION) {
            handleSellAuction(player, sellSession, text);
            return;
        }
        if (sellSession.step() == Step.PRICE) {
            handleSellPrice(player, sellSession, text);
            return;
        }
        if (sellSession.step() == Step.DESCRIPTION) {
            handleSellDescription(player, sellSession, text);
        }
    }

    private void handleOwnerDescription(Player player, RegionOwnerEditSessionService.Session session, String text) {
        EditDescriptionResult result = regionMarketService.editListingDescription(player, session.listingId(), text);
        regionMarketService.ownerEditSessionService().clearDescriptionEdit(player.getUniqueId());
        if (result == EditDescriptionResult.SUCCESS) {
            messageService.send(player, "region-owner-description-updated");
            return;
        }
        if (result == EditDescriptionResult.TOO_LONG) {
            messageService.send(player, "region-owner-description-too-long");
            return;
        }
        if (result == EditDescriptionResult.NOT_OWNER) {
            messageService.send(player, "error-not-owner");
            return;
        }
        messageService.send(player, "error-listing-unavailable");
    }

    private void handleSellRegion(Player player, String text) {
        RegionRef region = regionMarketService.resolveSellerRegion(player, text);
        if (region == null) {
            messageService.send(player, RegionMarketPresentation.sellChatInvalidRegionKey(regionMarketService.settings()));
            return;
        }
        if (!regionMarketService.worldGuardBridge().isOwner(player.getUniqueId(), region)) {
            messageService.send(player, "region-error-not-owner");
            return;
        }
        regionMarketService.sessionService().submitRegion(player.getUniqueId(), region);
        messageService.send(player, "region-sell-chat-auction");
    }

    private void handleSellAuction(Player player, Session session, String text) {
        if (text.isBlank()) {
            messageService.send(player, "region-sell-chat-invalid-auction");
            return;
        }
        Session updated = regionMarketService.sessionService().submitAuction(player.getUniqueId(), text);
        if (updated == null) {
            messageService.send(player, "region-sell-chat-invalid-auction");
            return;
        }
        if (regionMarketService.findDefinition(text) == null) {
            regionMarketService.sessionService().clear(player.getUniqueId());
            messageService.send(player, "region-error-auction-not-allowed");
            return;
        }
        messageService.send(player, "region-sell-chat-price");
    }

    private void handleSellPrice(Player player, Session session, String text) {
        int price;
        try {
            price = Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            messageService.send(player, "error-invalid-price");
            return;
        }
        Session updated = regionMarketService.sessionService().submitPrice(player.getUniqueId(), price);
        if (updated == null) {
            regionMarketService.sessionService().clear(player.getUniqueId());
            messageService.send(player, "error-invalid-price");
            return;
        }
        messageService.send(player, "region-sell-chat-description");
    }

    private void handleSellDescription(Player player, Session session, String text) {
        RegionRef region = session.region();
        String auctionId = session.auctionId();
        int price = session.price();
        String description = text.equals("-") ? "" : text;
        regionMarketService.sessionService().clear(player.getUniqueId());
        RegionSellResult result = regionMarketService.sell(player, region, auctionId, price, description);
        if (!result.success()) {
            messageService.send(player, result.failure().messageKey());
            return;
        }
        messageService.send(
                player,
                "region-success-listed",
                java.util.Map.of(
                        "region", region.regionId(),
                        "world", region.worldName(),
                        "price", regionMarketService.formatPrice(price, auctionId, player.getUniqueId()),
                        "id", String.valueOf(result.listing().listingId())
                )
        );
    }
}
