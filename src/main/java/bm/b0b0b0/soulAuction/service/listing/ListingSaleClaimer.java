package bm.b0b0b0.soulAuction.service.listing;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.repository.SqlAuctionRepository;
import bm.b0b0b0.soulAuction.service.RedisSellGuard;
import java.util.Optional;

public final class ListingSaleClaimer {

    private final AuctionRepository repository;
    private final RedisSellGuard redisSellGuard;

    public ListingSaleClaimer(AuctionRepository repository, RedisSellGuard redisSellGuard) {
        this.repository = repository;
        this.redisSellGuard = redisSellGuard;
    }

    public Optional<AuctionListing> claim(long listingId) {
        if (repository instanceof SqlAuctionRepository sqlRepository) {
            return sqlRepository.claimForSaleBlocking(listingId, redisSellGuard);
        }
        if (!redisSellGuard.tryAcquireListingLockLocal(listingId)) {
            return Optional.empty();
        }
        if (redisSellGuard.distributedLocksRequired() && !redisSellGuard.tryAcquireListingLockDistributed(listingId)) {
            redisSellGuard.releaseListingLockLocal(listingId);
            return Optional.empty();
        }
        AuctionListing removed = repository.remove(listingId);
        if (removed == null) {
            redisSellGuard.releaseListingLock(listingId);
            return Optional.empty();
        }
        return Optional.of(removed);
    }

    public void rollback(AuctionListing listing) {
        if (listing == null) {
            return;
        }
        if (repository instanceof SqlAuctionRepository sqlRepository) {
            sqlRepository.restoreActiveBlocking(listing);
        } else {
            repository.putBack(listing);
        }
        redisSellGuard.releaseListingLock(listing.listingId());
    }

    public void commit(long listingId) {
        redisSellGuard.releaseListingLock(listingId);
    }
}
