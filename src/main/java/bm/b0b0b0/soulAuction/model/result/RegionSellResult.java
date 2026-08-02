package bm.b0b0b0.soulAuction.model.result;

import bm.b0b0b0.soulAuction.model.AuctionListing;

public record RegionSellResult(
        boolean success,
        RegionSellFailure failure,
        AuctionListing listing
) {
    public static RegionSellResult success(AuctionListing listing) {
        return new RegionSellResult(true, null, listing);
    }

    public static RegionSellResult failure(RegionSellFailure failure) {
        return new RegionSellResult(false, failure, null);
    }
}
