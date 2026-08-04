package bm.b0b0b0.soulAuction.listener;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.model.result.RegionSellResult;
import bm.b0b0b0.soulAuction.service.region.RegionMarketPresentation;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
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
        RegionSellSessionService sessions = regionMarketService.sessionService();
        if (sessions.peek(player.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Session session = sessions.peek(player.getUniqueId()).orElseThrow();
        PluginSchedulers.run(plugin, player, () -> handleInput(player, session, text));
    }

    private void handleInput(Player player, Session session, String text) {
        if (text.equalsIgnoreCase("cancel") || text.equals("-")) {
            regionMarketService.sessionService().clear(player.getUniqueId());
            messageService.send(player, "region-sell-chat-cancelled");
            return;
        }
        RegionSellSessionService sessions = regionMarketService.sessionService();
        if (session.step() == Step.REGION) {
            RegionRef region = regionMarketService.resolveSellerRegion(player, text);
            if (region == null) {
                messageService.send(player, RegionMarketPresentation.sellChatInvalidRegionKey(regionMarketService.settings()));
                return;
            }
            if (!regionMarketService.worldGuardBridge().isOwner(player.getUniqueId(), region)) {
                messageService.send(player, "region-error-not-owner");
                return;
            }
            sessions.submitRegion(player.getUniqueId(), region);
            messageService.send(player, "region-sell-chat-auction");
            return;
        }
        if (session.step() == Step.AUCTION) {
            if (text.isBlank()) {
                messageService.send(player, "region-sell-chat-invalid-auction");
                return;
            }
            Session updated = sessions.submitAuction(player.getUniqueId(), text);
            if (updated == null) {
                messageService.send(player, "region-sell-chat-invalid-auction");
                return;
            }
            if (regionMarketService.findDefinition(text) == null) {
                sessions.clear(player.getUniqueId());
                messageService.send(player, "region-error-auction-not-allowed");
                return;
            }
            messageService.send(player, "region-sell-chat-price");
            return;
        }
        if (session.step() == Step.PRICE) {
            int price;
            try {
                price = Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                messageService.send(player, "error-invalid-price");
                return;
            }
            RegionRef region = session.region();
            String auctionId = session.auctionId();
            sessions.clear(player.getUniqueId());
            RegionSellResult result = regionMarketService.sell(player, region, auctionId, price);
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
}
