package bm.b0b0b0.soulAuction.repository;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
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
            String searchText
    );

    AuctionListing remove(long listingId);

    void putBack(AuctionListing listing);

    AuctionListing findById(long listingId);

    boolean updatePrice(long listingId, int newPrice);

    List<AuctionListing> listAll();

    List<AuctionListing> listByAuction(String auctionId);

    int countBySeller(UUID sellerId);

    int countBySellerInAuction(UUID sellerId, String auctionId);
}
