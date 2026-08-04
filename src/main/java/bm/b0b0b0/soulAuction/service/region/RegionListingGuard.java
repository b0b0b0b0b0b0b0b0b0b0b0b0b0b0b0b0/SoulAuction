package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.model.result.CancelResult;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionListingGuard {

    private final JavaPlugin plugin;
    private final WorldGuardBridge worldGuardBridge;
    private final AuctionService auctionService;
    private final MessageService messageService;

    public RegionListingGuard(
            JavaPlugin plugin,
            WorldGuardBridge worldGuardBridge,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.plugin = plugin;
        this.worldGuardBridge = worldGuardBridge;
        this.auctionService = auctionService;
        this.messageService = messageService;
    }

    public boolean isSellable(AuctionListing listing) {
        if (listing == null || !RegionListingHelper.isRegionListing(listing)) {
            return false;
        }
        if (!worldGuardBridge.available()) {
            return true;
        }
        RegionRef region = RegionListingHelper.regionRef(listing);
        return worldGuardBridge.regionExists(region) && worldGuardBridge.isOwner(listing.sellerId(), region);
    }

    public boolean invalidateIfNotSellable(AuctionListing listing) {
        if (isSellable(listing)) {
            return false;
        }
        CancelResult result = auctionService.cancelRegionListingSystem(listing.listingId());
        if (result.success()) {
            notifySeller(listing);
        }
        return true;
    }

    private void notifySeller(AuctionListing listing) {
        Player seller = Bukkit.getPlayer(listing.sellerId());
        if (seller == null) {
            return;
        }
        RegionRef region = RegionListingHelper.regionRef(listing);
        PluginSchedulers.run(plugin, seller, () -> messageService.send(
                seller,
                "region-listing-invalidated-owner",
                Map.of("region", region.regionId(), "world", region.worldName())
        ));
    }
}
