package bm.b0b0b0.soulAuction.model.result;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import org.bukkit.entity.Player;

public record RegionPurchaseResult(
        boolean success,
        RegionPurchaseFailure failure,
        AuctionListing listing,
        Player sellerOnline,
        int sellerPayout,
        int saleTax,
        int buyTax,
        int buyerCharge
) {
    public static RegionPurchaseResult success(
            AuctionListing listing,
            Player sellerOnline,
            int sellerPayout,
            int saleTax,
            int buyTax,
            int buyerCharge
    ) {
        return new RegionPurchaseResult(true, null, listing, sellerOnline, sellerPayout, saleTax, buyTax, buyerCharge);
    }

    public static RegionPurchaseResult failure(RegionPurchaseFailure failure) {
        return new RegionPurchaseResult(false, failure, null, null, 0, 0, 0, 0);
    }
}
