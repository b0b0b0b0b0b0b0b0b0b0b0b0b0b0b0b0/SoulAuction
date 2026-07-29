package bm.b0b0b0.soulAuction.model.result;

import bm.b0b0b0.soulAuction.model.AuctionListing;

public record PurchaseQuote(AuctionListing listing, int totalCharge, int saleTax, int buyTax) {
}
