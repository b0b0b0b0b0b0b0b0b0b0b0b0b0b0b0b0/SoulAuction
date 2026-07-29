package bm.b0b0b0.soulAuction.service.listing;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import java.util.Optional;

/** Removes a listing once; {@link #close()} puts it back unless {@link #commit()} was called. */
public final class RemovedListing implements AutoCloseable {

    private final AuctionRepository repository;
    private AuctionListing listing;
    private boolean committed;

    private RemovedListing(AuctionRepository repository, AuctionListing listing) {
        this.repository = repository;
        this.listing = listing;
    }

    public static RemovedListing take(AuctionRepository repository, long listingId) {
        return new RemovedListing(repository, repository.remove(listingId));
    }

    public Optional<AuctionListing> listing() {
        return Optional.ofNullable(listing);
    }

    public void commit() {
        committed = true;
        listing = null;
    }

    @Override
    public void close() {
        if (!committed && listing != null) {
            repository.putBack(listing);
            listing = null;
        }
    }
}
