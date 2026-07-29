package bm.b0b0b0.soulAuction.model.result;

import bm.b0b0b0.soulAuction.model.AuctionListing;

public record SellResult(boolean success, SellFailure failure, AuctionListing listing) {

    public static SellResult success(AuctionListing listing) {
        return new SellResult(true, null, listing);
    }

    public static SellResult failure(SellFailure failure) {
        return new SellResult(false, failure, null);
    }
}
