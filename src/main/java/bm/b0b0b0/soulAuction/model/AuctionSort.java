package bm.b0b0b0.soulAuction.model;

public enum AuctionSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC;

    public AuctionSort next() {
        return switch (this) {
            case NEWEST -> PRICE_ASC;
            case PRICE_ASC -> PRICE_DESC;
            case PRICE_DESC -> NEWEST;
        };
    }
}
