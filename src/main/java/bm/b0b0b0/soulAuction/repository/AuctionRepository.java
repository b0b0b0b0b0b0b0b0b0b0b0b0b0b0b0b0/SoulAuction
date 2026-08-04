package bm.b0b0b0.soulAuction.repository;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AuctionRepository {

    CompletableFuture<Void> load();

    CompletableFuture<Void> flush();

    CompletableFuture<Void> close();

    AuctionListing create(
            String auctionId,
            UUID sellerId,
            String sellerName,
            int price,
            AuctionEconomyType economyType,
            String itemBase64,
            AuctionCategory category,
            String searchText,
            String metadataJson,
            long createdAtEpochMillis
    );

    default AuctionListing create(
            String auctionId,
            UUID sellerId,
            String sellerName,
            int price,
            AuctionEconomyType economyType,
            String itemBase64,
            AuctionCategory category,
            String searchText,
            String metadataJson
    ) {
        return create(
                auctionId,
                sellerId,
                sellerName,
                price,
                economyType,
                itemBase64,
                category,
                searchText,
                metadataJson,
                System.currentTimeMillis()
        );
    }

    AuctionListing remove(long listingId);

    void putBack(AuctionListing listing);

    AuctionListing findById(long listingId);

    boolean updatePrice(long listingId, int newPrice);

    boolean updateMetadata(long listingId, String metadataJson, String searchText);

    List<AuctionListing> listAll();

    List<AuctionListing> listByAuction(String auctionId);

    int countBySeller(UUID sellerId);

    int countBySellerInAuction(UUID sellerId, String auctionId);

    default boolean sharedPendingPayouts() {
        return false;
    }

    default void storePendingPayout(PendingSaleNotification notification) {
    }

    default List<PendingSaleNotification> drainPendingPayouts(UUID sellerId) {
        return Collections.emptyList();
    }

    default boolean importListing(AuctionListing listing) {
        if (listing == null || findById(listing.listingId()) != null) {
            return false;
        }
        putBack(listing);
        return true;
    }
}
