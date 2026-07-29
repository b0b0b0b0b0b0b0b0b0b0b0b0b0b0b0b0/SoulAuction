package bm.b0b0b0.soulAuction.api;

import bm.b0b0b0.soulAuction.SoulAuction;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.service.AuctionService;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoulAuctionAPI {

    private SoulAuctionAPI() {
    }

    public static AuctionService service() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(SoulAuctionAPI.class);
        if (!(plugin instanceof SoulAuction soulAuction)) {
            throw new IllegalStateException("SoulAuction plugin is not loaded");
        }
        return soulAuction.auctionService();
    }

    public static AuctionListing findListing(long listingId) {
        return service().listingById(listingId);
    }
}
