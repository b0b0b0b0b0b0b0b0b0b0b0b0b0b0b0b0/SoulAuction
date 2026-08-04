package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.ListingMetadata;
import bm.b0b0b0.soulAuction.model.region.RegionBounds;
import bm.b0b0b0.soulAuction.model.region.RegionInfo;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RegionListingPresentation {

    private RegionListingPresentation() {
    }

    public static Map<String, String> listingPlaceholders(
            AuctionListing listing,
            String price,
            String economyLabel,
            WorldGuardBridge worldGuardBridge
    ) {
        return buildPlaceholders(listing, price, economyLabel, worldGuardBridge);
    }

    public static Map<String, String> purchasePlaceholders(
            AuctionListing listing,
            String price,
            WorldGuardBridge worldGuardBridge
    ) {
        return buildPlaceholders(listing, price, "", worldGuardBridge);
    }

    private static Map<String, String> buildPlaceholders(
            AuctionListing listing,
            String price,
            String economyLabel,
            WorldGuardBridge worldGuardBridge
    ) {
        ListingMetadata metadata = listing.metadata();
        RegionRef region = RegionListingHelper.regionRef(listing);
        RegionInfo info = resolveInfo(metadata, region, worldGuardBridge);
        RegionBounds bounds = info.bounds();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("region", safe(metadata.regionId));
        placeholders.put("world", safe(metadata.regionWorld));
        placeholders.put("seller", listing.sellerName());
        placeholders.put("price", price);
        placeholders.put("economy", economyLabel);
        placeholders.put("auction", listing.auctionId());
        placeholders.put("id", String.valueOf(listing.listingId()));
        placeholders.put("coords", formatCenter(bounds, metadata));
        placeholders.put("coords_min", formatMinCorner(bounds, metadata));
        placeholders.put("coords_max", formatMaxCorner(bounds, metadata));
        placeholders.put("size", formatSize(bounds, metadata));
        placeholders.put("volume", formatVolume(info, metadata));
        placeholders.put("priority", formatPriority(info, metadata));
        placeholders.put("parent", formatParent(info, metadata));
        placeholders.put("owners", formatCount(info.ownersCount(), metadata.regionOwnersCount, metadata, bounds));
        placeholders.put("members", formatCount(info.membersCount(), metadata.regionMembersCount, metadata, bounds));
        placeholders.put("flags", formatFlags(info, metadata));
        placeholders.put("description", formatDescription(metadata));
        return Map.copyOf(placeholders);
    }

    public static String buildSearchText(RegionRef region, String sellerName, String auctionLabel, String description) {
        StringBuilder builder = new StringBuilder();
        builder.append(region.regionId()).append(' ')
                .append(region.worldName()).append(' ')
                .append(sellerName).append(' ')
                .append(auctionLabel);
        if (description != null && !description.isBlank()) {
            builder.append(' ').append(description);
        }
        return builder.toString().toLowerCase(Locale.ROOT).trim();
    }

    public static ListingMetadata applyRegionInfo(ListingMetadata metadata, RegionInfo info) {
        if (metadata == null) {
            metadata = ListingMetadata.empty();
        }
        if (info == null || info.bounds() == null) {
            return metadata;
        }
        RegionBounds bounds = info.bounds();
        metadata.regionMinX = bounds.minX();
        metadata.regionMinY = bounds.minY();
        metadata.regionMinZ = bounds.minZ();
        metadata.regionMaxX = bounds.maxX();
        metadata.regionMaxY = bounds.maxY();
        metadata.regionMaxZ = bounds.maxZ();
        metadata.regionCenterX = bounds.centerX();
        metadata.regionCenterY = bounds.centerY();
        metadata.regionCenterZ = bounds.centerZ();
        metadata.regionSizeX = bounds.spanX();
        metadata.regionSizeY = bounds.spanY();
        metadata.regionSizeZ = bounds.spanZ();
        metadata.regionVolume = info.volume() > 0L ? info.volume() : bounds.blockVolume();
        metadata.regionPriority = info.priority();
        metadata.regionParent = info.parentId() == null ? "" : info.parentId();
        metadata.regionOwnersCount = info.ownersCount();
        metadata.regionMembersCount = info.membersCount();
        metadata.regionFlags = info.flagsSummary() == null ? "" : info.flagsSummary();
        return metadata;
    }

    public static ListingMetadata applyBounds(ListingMetadata metadata, RegionBounds bounds) {
        if (bounds == null) {
            return metadata;
        }
        return applyRegionInfo(metadata, new RegionInfo(bounds, bounds.blockVolume(), 0, "", 0, 0, ""));
    }

    private static RegionInfo resolveInfo(ListingMetadata metadata, RegionRef region, WorldGuardBridge bridge) {
        if (bridge != null && bridge.available()) {
            RegionInfo live = bridge.regionInfo(region);
            if (live.bounds() != null) {
                return live;
            }
        }
        RegionBounds cached = cachedBounds(metadata);
        if (cached != null) {
            return new RegionInfo(
                    cached,
                    metadata.regionVolume > 0L ? metadata.regionVolume : cached.blockVolume(),
                    metadata.regionPriority,
                    metadata.regionParent == null ? "" : metadata.regionParent,
                    metadata.regionOwnersCount,
                    metadata.regionMembersCount,
                    metadata.regionFlags == null ? "" : metadata.regionFlags
            );
        }
        return RegionInfo.empty();
    }

    private static RegionBounds cachedBounds(ListingMetadata metadata) {
        if (metadata.regionSizeX <= 0 || metadata.regionSizeY <= 0 || metadata.regionSizeZ <= 0) {
            return null;
        }
        if (metadata.regionMaxX != 0 || metadata.regionMaxY != 0 || metadata.regionMaxZ != 0
                || metadata.regionMinX != 0 || metadata.regionMinY != 0 || metadata.regionMinZ != 0) {
            return new RegionBounds(
                    metadata.regionMinX,
                    metadata.regionMinY,
                    metadata.regionMinZ,
                    metadata.regionMaxX,
                    metadata.regionMaxY,
                    metadata.regionMaxZ
            );
        }
        int minX = metadata.regionCenterX - metadata.regionSizeX / 2;
        int minY = metadata.regionCenterY - metadata.regionSizeY / 2;
        int minZ = metadata.regionCenterZ - metadata.regionSizeZ / 2;
        return new RegionBounds(
                minX,
                minY,
                minZ,
                minX + metadata.regionSizeX - 1,
                minY + metadata.regionSizeY - 1,
                minZ + metadata.regionSizeZ - 1
        );
    }

    private static String formatCenter(RegionBounds bounds, ListingMetadata metadata) {
        if (bounds != null) {
            return bounds.formattedCenter();
        }
        if (metadata.regionCenterX != 0 || metadata.regionCenterY != 0 || metadata.regionCenterZ != 0) {
            return metadata.regionCenterX + ", " + metadata.regionCenterY + ", " + metadata.regionCenterZ;
        }
        return dash();
    }

    private static String formatMinCorner(RegionBounds bounds, ListingMetadata metadata) {
        if (bounds != null) {
            return bounds.formattedMinCorner();
        }
        if (hasStoredBounds(metadata)) {
            return metadata.regionMinX + ", " + metadata.regionMinY + ", " + metadata.regionMinZ;
        }
        return dash();
    }

    private static String formatMaxCorner(RegionBounds bounds, ListingMetadata metadata) {
        if (bounds != null) {
            return bounds.formattedMaxCorner();
        }
        if (hasStoredBounds(metadata)) {
            return metadata.regionMaxX + ", " + metadata.regionMaxY + ", " + metadata.regionMaxZ;
        }
        return dash();
    }

    private static boolean hasStoredBounds(ListingMetadata metadata) {
        return metadata.regionSizeX > 0
                && metadata.regionMaxX >= metadata.regionMinX
                && metadata.regionMaxY >= metadata.regionMinY
                && metadata.regionMaxZ >= metadata.regionMinZ;
    }

    private static String formatSize(RegionBounds bounds, ListingMetadata metadata) {
        if (bounds != null) {
            return bounds.formattedSizeLowercase();
        }
        if (metadata.regionSizeX > 0 && metadata.regionSizeY > 0 && metadata.regionSizeZ > 0) {
            return metadata.regionSizeX + "x" + metadata.regionSizeY + "x" + metadata.regionSizeZ;
        }
        return dash();
    }

    private static String formatVolume(RegionInfo info, ListingMetadata metadata) {
        long volume = info.volume();
        if (volume <= 0L && info.bounds() != null) {
            volume = info.bounds().blockVolume();
        }
        if (volume <= 0L && metadata.regionVolume > 0L) {
            volume = metadata.regionVolume;
        }
        if (volume <= 0L) {
            return dash();
        }
        return formatNumber(volume);
    }

    private static String formatPriority(RegionInfo info, ListingMetadata metadata) {
        if (info.bounds() != null) {
            return String.valueOf(info.priority());
        }
        if (hasStoredBounds(metadata)) {
            return String.valueOf(metadata.regionPriority);
        }
        return dash();
    }

    private static String formatParent(RegionInfo info, ListingMetadata metadata) {
        String parent = info.parentId();
        if (parent == null || parent.isBlank()) {
            parent = metadata.regionParent;
        }
        return parent == null || parent.isBlank() ? dash() : parent;
    }

    private static String formatCount(int live, int cached, ListingMetadata metadata, RegionBounds bounds) {
        if (bounds != null) {
            return String.valueOf(live);
        }
        if (hasStoredBounds(metadata)) {
            return String.valueOf(cached);
        }
        return dash();
    }

    private static String formatFlags(RegionInfo info, ListingMetadata metadata) {
        String flags = info.flagsSummary();
        if (flags == null || flags.isBlank()) {
            flags = metadata.regionFlags;
        }
        return flags == null || flags.isBlank() ? dash() : flags;
    }

    private static String formatDescription(ListingMetadata metadata) {
        if (metadata.regionDescription == null || metadata.regionDescription.isBlank()) {
            return dash();
        }
        return metadata.regionDescription.trim();
    }

    private static String formatNumber(long value) {
        return String.format(Locale.US, "%,d", value).replace(',', ' ');
    }

    private static String dash() {
        return "—";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }
}
