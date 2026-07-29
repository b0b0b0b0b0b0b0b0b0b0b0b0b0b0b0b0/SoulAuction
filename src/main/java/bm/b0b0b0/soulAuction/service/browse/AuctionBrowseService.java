package bm.b0b0b0.soulAuction.service.browse;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionListingCache;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.ListingSearchText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.inventory.ItemStack;

public final class AuctionBrowseService {

    public record BrowsePage(int total, List<AuctionListing> listings) {
    }

    public enum FavoriteFilterMode {
        OFF,
        SELLERS,
        LISTINGS,
        ANY
    }

    public record BrowseFilterState(
            String searchQuery,
            boolean favoritesOnly,
            boolean favoriteListingsOnly,
            int minPrice,
            int maxPrice
    ) {
        public BrowseFilterState {
            if (minPrice < 0) {
                minPrice = 0;
            }
            if (maxPrice < 0) {
                maxPrice = 0;
            }
        }

        public static BrowseFilterState empty() {
            return new BrowseFilterState(null, false, false, 0, 0);
        }

        public BrowseFilterState withSearch(String query) {
            return new BrowseFilterState(query, favoritesOnly, favoriteListingsOnly, minPrice, maxPrice);
        }

        public BrowseFilterState withFavoritesOnly(boolean value) {
            return new BrowseFilterState(searchQuery, value, favoriteListingsOnly, minPrice, maxPrice);
        }

        public FavoriteFilterMode favoriteMode() {
            if (favoritesOnly && favoriteListingsOnly) {
                return FavoriteFilterMode.ANY;
            }
            if (favoriteListingsOnly) {
                return FavoriteFilterMode.LISTINGS;
            }
            if (favoritesOnly) {
                return FavoriteFilterMode.SELLERS;
            }
            return FavoriteFilterMode.OFF;
        }
    }

    private final AuctionRepository repository;
    private final AuctionListingCache listingCache;
    private final AuctionRuntimeStorage runtimeStorage;
    private final PermissionPriorityResolver priorityResolver;
    private final Supplier<AuctionSettings> settingsSupplier;

    public AuctionBrowseService(
            AuctionRepository repository,
            AuctionListingCache listingCache,
            AuctionRuntimeStorage runtimeStorage,
            PermissionPriorityResolver priorityResolver,
            Supplier<AuctionSettings> settingsSupplier
    ) {
        this.repository = repository;
        this.listingCache = listingCache;
        this.runtimeStorage = runtimeStorage;
        this.priorityResolver = priorityResolver;
        this.settingsSupplier = settingsSupplier;
    }

    public BrowsePage browsePage(
            String auctionId,
            AuctionSort sort,
            AuctionCategory category,
            int page,
            int pageSize,
            String searchQuery,
            UUID viewerId,
            BrowseFilterState filter
    ) {
        String normalizedQuery = normalizeSearch(searchQuery != null ? searchQuery : filter == null ? null : filter.searchQuery());
        Pattern regex = compileRegex(normalizedQuery);
        BrowseFilterState effectiveFilter = filter == null ? BrowseFilterState.empty() : filter;
        List<UUID> favoriteSellers = viewerId == null ? List.of() : runtimeStorage.favoriteSellers(viewerId);
        Set<Long> favoriteListings = viewerId == null ? Set.of() : runtimeStorage.favoriteListings(viewerId);
        List<AuctionListing> source = listingCache.listingsForAuction(auctionId, () -> repository.listByAuction(auctionId));
        List<AuctionListing> filtered = new ArrayList<>();
        for (AuctionListing listing : source) {
            if (category != AuctionCategory.ALL && listing.category() != category) {
                continue;
            }
            if (normalizedQuery != null && !matchesSearch(listing, normalizedQuery, regex)) {
                continue;
            }
            if (!matchesFavoriteFilter(effectiveFilter.favoriteMode(), listing, favoriteSellers, favoriteListings)) {
                continue;
            }
            if (effectiveFilter.minPrice() > 0 && listing.price() < effectiveFilter.minPrice()) {
                continue;
            }
            if (effectiveFilter.maxPrice() > 0 && listing.price() > effectiveFilter.maxPrice()) {
                continue;
            }
            filtered.add(listing);
        }
        List<AuctionListing> sorted = sortListings(filtered, sort);
        int offset = Math.max(0, page) * pageSize;
        if (offset >= sorted.size()) {
            return new BrowsePage(sorted.size(), List.of());
        }
        int end = Math.min(sorted.size(), offset + pageSize);
        return new BrowsePage(sorted.size(), sorted.subList(offset, end));
    }

    public int count(String auctionId, AuctionCategory category, String searchQuery, UUID viewerId, BrowseFilterState filter) {
        return browsePage(auctionId, AuctionSort.NEWEST, category, 0, 1, searchQuery, viewerId, filter).total();
    }

    private boolean matchesFavoriteFilter(
            FavoriteFilterMode mode,
            AuctionListing listing,
            List<UUID> favoriteSellers,
            Set<Long> favoriteListings
    ) {
        return switch (mode) {
            case OFF -> true;
            case SELLERS -> favoriteSellers.contains(listing.sellerId());
            case LISTINGS -> favoriteListings.contains(listing.listingId());
            case ANY -> favoriteSellers.contains(listing.sellerId()) || favoriteListings.contains(listing.listingId());
        };
    }

    private List<AuctionListing> sortListings(List<AuctionListing> listings, AuctionSort sort) {
        if (listings.isEmpty()) {
            return listings;
        }
        boolean needsItemDecode = switch (sort) {
            case AMOUNT_ASC, AMOUNT_DESC, MATERIAL_ASC, MATERIAL_DESC, UNIT_PRICE_ASC, UNIT_PRICE_DESC -> true;
            default -> false;
        };
        if (!needsItemDecode) {
            List<AuctionListing> copy = new ArrayList<>(listings);
            copy.sort(comparator(sort));
            return copy;
        }
        List<ListingSortRow> rows = new ArrayList<>(listings.size());
        for (AuctionListing listing : listings) {
            rows.add(ListingSortRow.from(listing));
        }
        rows.sort(rowComparator(sort));
        List<AuctionListing> sorted = new ArrayList<>(rows.size());
        for (ListingSortRow row : rows) {
            sorted.add(row.listing());
        }
        return sorted;
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

    private Comparator<ListingSortRow> rowComparator(AuctionSort sort) {
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

    private Comparator<AuctionListing> comparator(AuctionSort sort) {
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

    private boolean matchesSearch(AuctionListing listing, String query, Pattern regex) {
        if (Long.toString(listing.listingId()).contains(query)) {
            return true;
        }
        String haystack = ListingSearchText.resolve(listing);
        if (regex != null) {
            return regex.matcher(haystack).find();
        }
        return haystack.contains(query);
    }

    private Pattern compileRegex(String normalizedQuery) {
        if (normalizedQuery == null) {
            return null;
        }
        AuctionSettings settings = settingsSupplier.get();
        if (settings == null || settings.features == null || !settings.features.advancedSearchRegex) {
            return null;
        }
        try {
            return Pattern.compile(normalizedQuery, Pattern.CASE_INSENSITIVE);
        } catch (Exception exception) {
            return null;
        }
    }

    private String normalizeSearch(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return null;
        }
        return searchQuery.trim().toLowerCase(Locale.ROOT);
    }
}
