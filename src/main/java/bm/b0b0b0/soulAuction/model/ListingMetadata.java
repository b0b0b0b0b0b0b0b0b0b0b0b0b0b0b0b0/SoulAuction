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
