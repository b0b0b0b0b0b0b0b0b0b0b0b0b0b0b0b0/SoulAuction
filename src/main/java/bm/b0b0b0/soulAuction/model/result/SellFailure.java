package bm.b0b0b0.soulAuction.model.result;

public enum SellFailure {
    SELL_DISABLED("error-sell-disabled"),
    SELL_LOCK_FAILED("error-sell-lock-failed"),
    SELL_DISABLED_IN_AUCTION("error-sell-disabled-in-auction"),
    SELL_PERMISSION_DENIED("error-sell-auction-denied"),
    AUCTION_NOT_FOUND("error-auction-not-found"),
    ECONOMY_UNAVAILABLE("error-economy-unavailable"),
    INVALID_PRICE("error-invalid-price"),
    AUCTION_LIMIT_REACHED("error-auction-limit"),
    GLOBAL_LIMIT_REACHED("error-global-limit"),
    BLOCKED_ITEM("error-blocked-item"),
    WHITELIST_ITEM("error-whitelist-item"),
    EMPTY_HAND("error-main-hand-empty"),
    PRICE_TOO_LOW("error-price-too-low"),
    PRICE_TOO_HIGH("error-price-too-high"),
    PLAYER_BLACKLISTED("error-player-blacklisted"),
    WORLD_BLOCKED("error-world-blocked"),
    COOLDOWN("error-sell-cooldown"),
    CUSTOM_ITEM_BLOCKED("error-custom-item-blocked"),
    STORAGE_NOT_READY("error-still-loading"),
    INVALID_AMOUNT("error-invalid-amount"),
    LISTING_ID_ALLOCATION_FAILED("error-listing-id-failed");

    private final String messageKey;

    SellFailure(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
