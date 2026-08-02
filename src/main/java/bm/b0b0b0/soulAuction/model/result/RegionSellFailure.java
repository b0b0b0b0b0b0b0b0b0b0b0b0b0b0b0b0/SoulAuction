package bm.b0b0b0.soulAuction.model.result;

public enum RegionSellFailure {
    DISABLED("region-error-disabled"),
    WORLDGUARD_UNAVAILABLE("region-error-worldguard-unavailable"),
    NO_PERMISSION("region-error-sell-permission"),
    STORAGE_NOT_READY("error-still-loading"),
    SELL_LOCK_FAILED("error-sell-lock-failed"),
    REGION_NOT_FOUND("region-error-not-found"),
    NOT_OWNER("region-error-not-owner"),
    ALREADY_LISTED("region-error-already-listed"),
    AUCTION_NOT_FOUND("error-auction-not-found"),
    AUCTION_NOT_ALLOWED("region-error-auction-not-allowed"),
    SELL_DISABLED_IN_AUCTION("error-sell-disabled-in-auction"),
    SELL_PERMISSION_DENIED("error-sell-auction-denied"),
    ECONOMY_UNAVAILABLE("error-economy-unavailable"),
    INVALID_PRICE("error-invalid-price"),
    PRICE_TOO_LOW("error-price-too-low"),
    PRICE_TOO_HIGH("error-price-too-high"),
    AUCTION_LIMIT_REACHED("error-auction-limit"),
    GLOBAL_LIMIT_REACHED("error-global-limit"),
    PLAYER_BLACKLISTED("error-player-blacklisted"),
    WORLD_BLOCKED("error-world-blocked"),
    COOLDOWN("error-sell-cooldown"),
    LISTING_ID_ALLOCATION_FAILED("error-listing-id-failed");

    private final String messageKey;

    RegionSellFailure(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
