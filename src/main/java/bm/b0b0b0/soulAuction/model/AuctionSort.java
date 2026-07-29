package bm.b0b0b0.soulAuction.model;

public enum AuctionSort {
    NEWEST,
    OLDEST,
    PRICE_ASC,
    PRICE_DESC,
    SELLER_ASC;

    public AuctionSort next() {
        return switch (this) {
            case NEWEST -> OLDEST;
            case OLDEST -> PRICE_ASC;
            case PRICE_ASC -> PRICE_DESC;
            case PRICE_DESC -> SELLER_ASC;
            case SELLER_ASC -> NEWEST;
        };
    }
}
