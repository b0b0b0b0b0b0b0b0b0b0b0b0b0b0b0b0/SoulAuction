package bm.b0b0b0.soulAuction.service.browse;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class AuctionListingSorter {

    private AuctionListingSorter() {
    }

    public static List<AuctionListing> sort(
            List<AuctionListing> listings,
            AuctionSort sort,
            PermissionPriorityResolver priorityResolver
    ) {
        if (listings.isEmpty()) {
            return listings;
        }
        boolean needsItemDecode = switch (sort) {
            case AMOUNT_ASC, AMOUNT_DESC, MATERIAL_ASC, MATERIAL_DESC, UNIT_PRICE_ASC, UNIT_PRICE_DESC -> true;
            default -> false;
        };
        if (!needsItemDecode) {
            List<AuctionListing> copy = new ArrayList<>(listings);
            copy.sort(comparator(sort, priorityResolver));
            return List.copyOf(copy);
        }
        List<ListingSortRow> rows = new ArrayList<>(listings.size());
        for (AuctionListing listing : listings) {
            rows.add(ListingSortRow.from(listing));
        }
        rows.sort(rowComparator(sort, priorityResolver));
        List<AuctionListing> sorted = new ArrayList<>(rows.size());
        for (ListingSortRow row : rows) {
            sorted.add(row.listing());
        }
        return List.copyOf(sorted);
    }

    private record ListingSortRow(AuctionListing listing, int amount, String material, double unitPrice) {
        static ListingSortRow from(AuctionListing listing) {
            ItemStack item = ItemStackCodec.decode(listing.itemBase64());
            int amount = item == null || item.isEmpty() ? 1 : Math.max(1, item.getAmount());
            String material = item == null || item.isEmpty() ? "" : item.getType().name();
            double unitPrice = listing.price() / (double) amount;
            return new ListingSortRow(listing, amount, material, unitPrice);
        }
    }

    private static Comparator<ListingSortRow> rowComparator(AuctionSort sort, PermissionPriorityResolver priorityResolver) {
        Comparator<ListingSortRow> priority = Comparator.<ListingSortRow>comparingInt(
                row -> priorityResolver.resolve(row.listing().sellerId())
        ).reversed();
        Comparator<ListingSortRow> base = switch (sort) {
            case NEWEST -> Comparator.comparingLong((ListingSortRow row) -> row.listing().createdAtEpochMillis()).reversed();
            case OLDEST -> Comparator.comparingLong((ListingSortRow row) -> row.listing().createdAtEpochMillis());
            case PRICE_ASC -> Comparator.comparingInt((ListingSortRow row) -> row.listing().price());
            case PRICE_DESC -> Comparator.comparingInt((ListingSortRow row) -> row.listing().price()).reversed();
            case SELLER_ASC -> Comparator.comparing((ListingSortRow row) -> row.listing().sellerName(), String.CASE_INSENSITIVE_ORDER);
            case SELLER_DESC -> Comparator.comparing((ListingSortRow row) -> row.listing().sellerName(), String.CASE_INSENSITIVE_ORDER).reversed();
            case AMOUNT_ASC -> Comparator.comparingInt(ListingSortRow::amount);
            case AMOUNT_DESC -> Comparator.comparingInt(ListingSortRow::amount).reversed();
            case MATERIAL_ASC -> Comparator.comparing(ListingSortRow::material, String.CASE_INSENSITIVE_ORDER);
            case MATERIAL_DESC -> Comparator.comparing(ListingSortRow::material, String.CASE_INSENSITIVE_ORDER).reversed();
            case CATEGORY_ASC -> Comparator.comparing((ListingSortRow row) -> row.listing().category().name());
            case LISTING_ID_ASC -> Comparator.comparingLong((ListingSortRow row) -> row.listing().listingId());
            case LISTING_ID_DESC -> Comparator.comparingLong((ListingSortRow row) -> row.listing().listingId()).reversed();
            case UNIT_PRICE_ASC -> Comparator.comparingDouble(ListingSortRow::unitPrice);
            case UNIT_PRICE_DESC -> Comparator.comparingDouble(ListingSortRow::unitPrice).reversed();
        };
        return priority.thenComparing(base);
    }

    private static Comparator<AuctionListing> comparator(AuctionSort sort, PermissionPriorityResolver priorityResolver) {
        Comparator<AuctionListing> priority = Comparator.<AuctionListing>comparingInt(
                listing -> priorityResolver.resolve(listing.sellerId())
        ).reversed();
        Comparator<AuctionListing> base = switch (sort) {
            case NEWEST -> Comparator.comparingLong(AuctionListing::createdAtEpochMillis).reversed();
            case OLDEST -> Comparator.comparingLong(AuctionListing::createdAtEpochMillis);
            case PRICE_ASC -> Comparator.comparingInt(AuctionListing::price);
            case PRICE_DESC -> Comparator.comparingInt(AuctionListing::price).reversed();
            case SELLER_ASC -> Comparator.comparing(AuctionListing::sellerName, String.CASE_INSENSITIVE_ORDER);
            case SELLER_DESC -> Comparator.comparing(AuctionListing::sellerName, String.CASE_INSENSITIVE_ORDER).reversed();
            case AMOUNT_ASC, AMOUNT_DESC, MATERIAL_ASC, MATERIAL_DESC, UNIT_PRICE_ASC, UNIT_PRICE_DESC ->
                    Comparator.comparingLong(AuctionListing::listingId);
            case CATEGORY_ASC -> Comparator.comparing(listing -> listing.category().name());
            case LISTING_ID_ASC -> Comparator.comparingLong(AuctionListing::listingId);
            case LISTING_ID_DESC -> Comparator.comparingLong(AuctionListing::listingId).reversed();
        };
        return priority.thenComparing(base);
    }
}
