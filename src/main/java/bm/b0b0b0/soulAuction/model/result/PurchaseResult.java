package bm.b0b0b0.soulAuction.model.result;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import org.bukkit.entity.Player;

public record PurchaseResult(
        boolean success,
        PurchaseFailure failure,
        AuctionListing listing,
        Player seller,
        int sellerPayout,
        int tax,
        int buyTax,
        int buyerCharge
) {
    public static PurchaseResult success(
            AuctionListing listing,
            Player seller,
            int sellerPayout,
            int tax,
            int buyTax,
            int buyerCharge
    ) {
        return new PurchaseResult(true, null, listing, seller, sellerPayout, tax, buyTax, buyerCharge);
    }

    public static PurchaseResult failure(PurchaseFailure failure) {
        return new PurchaseResult(false, failure, null, null, 0, 0, 0, 0);
    }
}
