package bm.b0b0b0.soulAuction.model.result;

public enum PurchaseFailure {
    LISTING_UNAVAILABLE("error-listing-unavailable"),
    AUCTION_NOT_FOUND("error-auction-not-found"),
    BUY_DISABLED_IN_AUCTION("error-buy-disabled-in-auction"),
    BUY_PERMISSION_DENIED("error-buy-auction-denied"),
    ECONOMY_UNAVAILABLE("error-economy-unavailable"),
    OWN_LISTING("error-own-listing"),
    NOT_ENOUGH_MONEY("error-not-enough-money"),
    INVENTORY_FULL("error-inventory-full");

    private final String messageKey;

    PurchaseFailure(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
