package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionListingCache;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import bm.b0b0b0.soulAuction.service.browse.AuctionListingSorter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public final class RegionBrowseService {

    public record BrowsePage(int total, List<AuctionListing> listings) {
    }

    private final AuctionRepository repository;
    private final AuctionListingCache listingCache;
    private final PermissionPriorityResolver priorityResolver;
    private final Supplier<PluginConfig> configSupplier;

    public RegionBrowseService(
            AuctionRepository repository,
            AuctionListingCache listingCache,
            PermissionPriorityResolver priorityResolver,
            Supplier<PluginConfig> configSupplier
    ) {
        this.repository = repository;
        this.listingCache = listingCache;
        this.priorityResolver = priorityResolver;
        this.configSupplier = configSupplier;
    }

    public BrowsePage browsePage(AuctionSort sort, int page, int pageSize, UUID sellerFilter) {
        List<AuctionListing> source = collectRegionListings(sort);
        List<AuctionListing> filtered = new ArrayList<>();
        for (AuctionListing listing : source) {
            if (sellerFilter != null && !sellerFilter.equals(listing.sellerId())) {
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

    public boolean isRegionListed(RegionRef region) {
        if (region == null) {
            return false;
        }
        for (AuctionListing listing : repository.listAll()) {
            if (!RegionListingHelper.isRegionListing(listing)) {
                continue;
            }
            if (RegionListingHelper.regionRef(listing).matchesIgnoreCase(region)) {
                return true;
            }
        }
        return false;
    }

    public int countBySeller(UUID sellerId) {
        int count = 0;
        for (AuctionListing listing : repository.listAll()) {
            if (!RegionListingHelper.isRegionListing(listing)) {
                continue;
            }
            if (listing.sellerId().equals(sellerId)) {
                count++;
            }
        }
        return count;
    }

    public List<AuctionDefinitionSettings> sellableAuctions(UUID sellerId, AuctionSettings.RegionMarketSettings settings) {
        List<AuctionDefinitionSettings> output = new ArrayList<>();
        List<String> allowed = normalizedAllowedIds(settings);
        for (AuctionDefinitionSettings definition : configSupplier.get().auctionDefinitions()) {
            if (definition == null || definition.id == null || definition.id.isBlank()) {
                continue;
            }
            if (!allowed.isEmpty() && !allowed.contains(definition.id.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!definition.sellEnabled) {
                continue;
            }
            output.add(definition);
        }
        return output;
    }

    private List<String> normalizedAllowedIds(AuctionSettings.RegionMarketSettings settings) {
        if (settings == null || settings.allowedAuctionIds == null || settings.allowedAuctionIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String id : settings.allowedAuctionIds) {
            if (id != null && !id.isBlank()) {
                normalized.add(id.toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private List<AuctionListing> collectRegionListings(AuctionSort sort) {
        List<AuctionListing> combined = new ArrayList<>();
        AuctionSettings settings = configSupplier.get().auctionSettings();
        boolean preSorted = settings.features != null && settings.features.preSortedBrowseCache;
        for (AuctionDefinitionSettings definition : configSupplier.get().auctionDefinitions()) {
            if (definition == null || definition.id == null || definition.id.isBlank()) {
                continue;
            }
            String auctionId = definition.id.toLowerCase(Locale.ROOT);
            List<AuctionListing> auctionListings = listingCache.sortedListings(
                    auctionId,
                    sort,
                    () -> repository.listByAuction(auctionId),
                    priorityResolver,
                    preSorted
            );
            for (AuctionListing listing : auctionListings) {
                if (RegionListingHelper.isRegionListing(listing)) {
                    combined.add(listing);
                }
            }
        }
        return AuctionListingSorter.sort(combined, sort, priorityResolver);
    }
}
