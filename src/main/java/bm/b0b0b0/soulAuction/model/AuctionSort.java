package bm.b0b0b0.soulAuction.model;

import java.util.Locale;

public enum AuctionSort {
    NEWEST,
    OLDEST,
    PRICE_ASC,
    PRICE_DESC,
    SELLER_ASC,
    SELLER_DESC,
    AMOUNT_ASC,
    AMOUNT_DESC,
    MATERIAL_ASC,
    MATERIAL_DESC,
    CATEGORY_ASC,
    LISTING_ID_ASC,
    LISTING_ID_DESC,
    UNIT_PRICE_ASC,
    UNIT_PRICE_DESC;

    public AuctionSort next() {
        AuctionSort[] values = values();
        int next = (ordinal() + 1) % values.length;
        return values[next];
    }

    public String messageKey() {
        return switch (this) {
            case LISTING_ID_ASC -> "sort-id-asc";
            case LISTING_ID_DESC -> "sort-id-desc";
            default -> "sort-" + name().toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }
}
