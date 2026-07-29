package bm.b0b0b0.soulAuction.service.browse;

import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionListingCache;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import bm.b0b0b0.soulAuction.util.ListingSearchText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AuctionBrowseService {

    public record BrowsePage(int total, List<AuctionListing> listings) {
    }

    public record BrowseFilterState(String searchQuery, boolean favoritesOnly, int minPrice, int maxPrice) {
        public BrowseFilterState {
            if (minPrice < 0) {
                minPrice = 0;
            }
            if (maxPrice < 0) {
                maxPrice = 0;
            }
        }

        public static BrowseFilterState empty() {
            return new BrowseFilterState(null, false, 0, 0);
        }

        public BrowseFilterState withSearch(String query) {
            return new BrowseFilterState(query, favoritesOnly, minPrice, maxPrice);
        }

        public BrowseFilterState withFavoritesOnly(boolean value) {
            return new BrowseFilterState(searchQuery, value, minPrice, maxPrice);
        }
    }

    private final AuctionRepository repository;
    private final AuctionListingCache listingCache;
    private final AuctionRuntimeStorage runtimeStorage;
    private final PermissionPriorityResolver priorityResolver;

    public AuctionBrowseService(
            AuctionRepository repository,
            AuctionListingCache listingCache,
            AuctionRuntimeStorage runtimeStorage,
            PermissionPriorityResolver priorityResolver
    ) {
        this.repository = repository;
        this.listingCache = listingCache;
        this.runtimeStorage = runtimeStorage;
        this.priorityResolver = priorityResolver;
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
        BrowseFilterState effectiveFilter = filter == null ? BrowseFilterState.empty() : filter;
        List<UUID> favorites = viewerId == null ? List.of() : runtimeStorage.favoriteSellers(viewerId);
        List<AuctionListing> source = listingCache.listingsForAuction(auctionId, () -> repository.listByAuction(auctionId));
        List<AuctionListing> filtered = new ArrayList<>();
        for (AuctionListing listing : source) {
            if (category != AuctionCategory.ALL && listing.category() != category) {
                continue;
            }
            if (normalizedQuery != null && !matchesSearch(listing, normalizedQuery)) {
                continue;
            }
            if (effectiveFilter.favoritesOnly() && !favorites.contains(listing.sellerId())) {
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
        filtered.sort(comparator(sort));
        int offset = Math.max(0, page) * pageSize;
        if (offset >= filtered.size()) {
            return new BrowsePage(filtered.size(), List.of());
        }
        int end = Math.min(filtered.size(), offset + pageSize);
        return new BrowsePage(filtered.size(), filtered.subList(offset, end));
    }

    public int count(String auctionId, AuctionCategory category, String searchQuery, UUID viewerId, BrowseFilterState filter) {
        return browsePage(auctionId, AuctionSort.NEWEST, category, 0, 1, searchQuery, viewerId, filter).total();
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
        };
        return priority.thenComparing(base);
    }

    private boolean matchesSearch(AuctionListing listing, String query) {
        if (Long.toString(listing.listingId()).contains(query)) {
            return true;
        }
        return ListingSearchText.resolve(listing).contains(query);
    }

    private String normalizeSearch(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return null;
        }
        return searchQuery.trim().toLowerCase(Locale.ROOT);
    }
}
