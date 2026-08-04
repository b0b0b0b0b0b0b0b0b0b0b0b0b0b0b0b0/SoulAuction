package bm.b0b0b0.soulAuction.model.region;

public record RegionInfo(
        RegionBounds bounds,
        long volume,
        int priority,
        String parentId,
        int ownersCount,
        int membersCount,
        String flagsSummary
) {

    public static RegionInfo empty() {
        return new RegionInfo(null, 0L, 0, "", 0, 0, "");
    }
}
