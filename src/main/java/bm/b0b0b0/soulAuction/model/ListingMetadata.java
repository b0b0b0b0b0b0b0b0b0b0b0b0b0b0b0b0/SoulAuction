package bm.b0b0b0.soulAuction.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;

public final class ListingMetadata {

    private static final Gson GSON = new GsonBuilder().create();

    public String serverOrigin = "";
    public boolean syntheticSeller = false;
    public ListingKind kind = ListingKind.FIXED_PRICE;
    public List<String> bundleItemsBase64 = new ArrayList<>();
    public String removalReason = "";
    public String regionWorld = "";
    public String regionId = "";
    public String regionDescription = "";
    public int regionCenterX = 0;
    public int regionCenterY = 0;
    public int regionCenterZ = 0;
    public int regionSizeX = 0;
    public int regionSizeY = 0;
    public int regionSizeZ = 0;
    public int regionMinX = 0;
    public int regionMinY = 0;
    public int regionMinZ = 0;
    public int regionMaxX = 0;
    public int regionMaxY = 0;
    public int regionMaxZ = 0;
    public long regionVolume = 0L;
    public int regionPriority = 0;
    public String regionParent = "";
    public int regionOwnersCount = 0;
    public int regionMembersCount = 0;
    public String regionFlags = "";

    public static ListingMetadata empty() {
        return new ListingMetadata();
    }

    public static ListingMetadata fromJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            ListingMetadata parsed = GSON.fromJson(json, ListingMetadata.class);
            return parsed == null ? empty() : parsed;
        } catch (Exception exception) {
            return empty();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public enum ListingKind {
        FIXED_PRICE,
        BUNDLE,
        BID,
        RENT,
        REGION
    }
}
