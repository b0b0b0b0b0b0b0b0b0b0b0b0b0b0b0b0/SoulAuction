package bm.b0b0b0.soulAuction.service.browse;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionListingCache;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import bm.b0b0b0.soulAuction.service.region.RegionListingHelper;
import bm.b0b0b0.soulAuction.util.ListingSearchText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
            int maxPrice,
            UUID sellerFilter
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
            return new BrowseFilterState(null, false, false, 0, 0, null);
        }

        public BrowseFilterState withSearch(String query) {
            return new BrowseFilterState(query, favoritesOnly, favoriteListingsOnly, minPrice, maxPrice, sellerFilter);
        }

        public BrowseFilterState withFavoritesOnly(boolean value) {
            return new BrowseFilterState(searchQuery, value, favoriteListingsOnly, minPrice, maxPrice, sellerFilter);
        }

        public BrowseFilterState withSellerFilter(UUID seller) {
            return new BrowseFilterState(searchQuery, favoritesOnly, favoriteListingsOnly, minPrice, maxPrice, seller);
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
        AuctionSettings settings = settingsSupplier.get();
        String normalizedQuery = normalizeSearch(searchQuery != null ? searchQuery : filter == null ? null : filter.searchQuery());
        Pattern regex = compileRegex(normalizedQuery, settings);
        SearchQueryContext searchContext = normalizedQuery == null
                ? null
                : SearchQueryContext.compile(normalizedQuery, regex, settings.features);
        Locale[] searchLocales = ListingSearchText.parseSearchLocales(
                settings.features == null ? null : settings.features.searchLocales
        );
        BrowseFilterState effectiveFilter = filter == null ? BrowseFilterState.empty() : filter;
        List<UUID> favoriteSellers = viewerId == null ? List.of() : runtimeStorage.favoriteSellers(viewerId);
        Set<Long> favoriteListings = viewerId == null ? Set.of() : runtimeStorage.favoriteListings(viewerId);
        boolean preSorted = settingsSupplier.get().features.preSortedBrowseCache;
        List<AuctionListing> source = listingCache.sortedListings(
                auctionId,
                sort,
                () -> repository.listByAuction(auctionId),
                priorityResolver,
                preSorted
        );
        List<AuctionListing> filtered = new ArrayList<>();
        for (AuctionListing listing : source) {
            if (RegionListingHelper.isRegionListing(listing)) {
                continue;
            }
            if (category != AuctionCategory.ALL && listing.category() != category) {
                continue;
            }
            if (effectiveFilter.sellerFilter() != null && !effectiveFilter.sellerFilter().equals(listing.sellerId())) {
                continue;
            }
            if (searchContext != null && !matchesSearch(listing, searchContext, searchLocales)) {
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

    private boolean matchesSearch(AuctionListing listing, SearchQueryContext context, Locale[] searchLocales) {
        String listingId = Long.toString(listing.listingId());
        for (SearchQueryContext.SearchQueryAttempt attempt : context.attempts()) {
            if (listingId.contains(attempt.query())) {
                return true;
            }
        }
        String haystack = ListingSearchText.resolve(listing, searchLocales);
        return ListingSearchMatcher.matches(haystack, context);
    }

    private Pattern compileRegex(String normalizedQuery, AuctionSettings settings) {
        if (normalizedQuery == null) {
            return null;
        }
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
