package bm.b0b0b0.soulAuction.model.result;

public enum RegionPurchaseFailure {
    DISABLED("region-error-disabled"),
    LISTING_UNAVAILABLE("error-listing-unavailable"),
    NOT_REGION_LISTING("region-error-not-region-listing"),
    AUCTION_NOT_FOUND("error-auction-not-found"),
    BUY_DISABLED_IN_AUCTION("error-buy-disabled-in-auction"),
    BUY_PERMISSION_DENIED("error-buy-auction-denied"),
    ECONOMY_UNAVAILABLE("error-economy-unavailable"),
    OWN_LISTING("error-own-listing"),
    NOT_ENOUGH_MONEY("error-not-enough-money"),
    REGION_UNAVAILABLE("region-error-unavailable"),
    TRANSFER_FAILED("region-error-transfer-failed"),
    STORAGE_NOT_READY("error-still-loading");

    private final String messageKey;

    RegionPurchaseFailure(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
