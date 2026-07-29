package bm.b0b0b0.soulAuction.listener;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerSaleNotificationListener implements Listener {

    private final AuctionService auctionService;
    private final MessageService messageService;

    public PlayerSaleNotificationListener(AuctionService auctionService, MessageService messageService) {
        this.auctionService = auctionService;
        this.messageService = messageService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (auctionService.claimPendingSalePayments(player)) {
            player.sendMessage(messageService.component("success-claim-money-auto"));
        }
        List<PendingSaleNotification> notifications = auctionService.takePendingSaleNotifications(player.getUniqueId());
        for (PendingSaleNotification notification : notifications) {
            String payout = auctionService.formatPrice(
                    notification.payout(),
                    notification.economyType(),
                    notification.auctionId()
            );
            String tax = String.valueOf(notification.tax());
            player.sendMessage(messageService.component(
                    "offline-sale-notification",
                    Map.of("payout", payout, "tax", tax, "auction", notification.auctionId())
            ));
        }
    }
}
