package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.ListingMetadata;
import bm.b0b0b0.soulAuction.model.region.RegionInfo;
import bm.b0b0b0.soulAuction.model.region.RegionRef;

public final class RegionListingHelper {

    private RegionListingHelper() {
    }

    public static boolean isRegionListing(AuctionListing listing) {
        if (listing == null) {
            return false;
        }
        ListingMetadata metadata = listing.metadata();
        return metadata.kind == ListingMetadata.ListingKind.REGION
                && metadata.regionId != null
                && !metadata.regionId.isBlank();
    }

    public static RegionRef regionRef(AuctionListing listing) {
        ListingMetadata metadata = listing.metadata();
        if (metadata.regionWorld == null || metadata.regionWorld.isBlank()) {
            return new RegionRef("world", metadata.regionId);
        }
        return new RegionRef(metadata.regionWorld, metadata.regionId);
    }

    public static ListingMetadata regionMetadata(RegionRef region, String serverOrigin, RegionInfo info, String description) {
        ListingMetadata metadata = ListingMetadata.empty();
        metadata.kind = ListingMetadata.ListingKind.REGION;
        metadata.regionWorld = region.worldName();
        metadata.regionId = region.regionId();
        metadata.serverOrigin = serverOrigin == null ? "" : serverOrigin;
        if (description != null && !description.isBlank()) {
            metadata.regionDescription = description.trim();
        }
        return RegionListingPresentation.applyRegionInfo(metadata, info);
    }
}
