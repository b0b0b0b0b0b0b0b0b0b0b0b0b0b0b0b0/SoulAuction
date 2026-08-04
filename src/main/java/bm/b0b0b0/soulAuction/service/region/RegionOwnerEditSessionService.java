package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.model.AuctionSort;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionOwnerEditSessionService {

    public record ReturnState(UUID sellerFilter, int page, AuctionSort sort) {
    }

    public record Session(long listingId, ReturnState returnState) {
    }

    private final Map<UUID, Session> descriptionSessions = new ConcurrentHashMap<>();

    public void startDescriptionEdit(UUID playerId, long listingId, ReturnState returnState) {
        descriptionSessions.put(playerId, new Session(listingId, returnState));
    }

    public Optional<Session> peekDescriptionEdit(UUID playerId) {
        return Optional.ofNullable(descriptionSessions.get(playerId));
    }

    public void clearDescriptionEdit(UUID playerId) {
        descriptionSessions.remove(playerId);
    }
}
