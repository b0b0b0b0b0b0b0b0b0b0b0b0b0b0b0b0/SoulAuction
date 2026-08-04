package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.model.region.RegionRef;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionSellSessionService {

    public enum Step {
        REGION,
        AUCTION,
        PRICE,
        DESCRIPTION
    }

    public record Session(
            Step step,
            RegionRef region,
            String auctionId,
            int price
    ) {
        public Session withRegion(RegionRef value) {
            return new Session(Step.AUCTION, value, auctionId, price);
        }

        public Session withAuction(String value) {
            return new Session(Step.PRICE, region, value, price);
        }

        public Session withPrice(int value) {
            return new Session(Step.DESCRIPTION, region, auctionId, value);
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public void start(UUID playerId) {
        sessions.put(playerId, new Session(Step.REGION, null, null, 0));
    }

    public Optional<Session> peek(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }

    public Session submitRegion(UUID playerId, RegionRef region) {
        Session updated = new Session(Step.AUCTION, region, null, 0);
        sessions.put(playerId, updated);
        return updated;
    }

    public Session submitAuction(UUID playerId, String auctionId) {
        Session current = sessions.get(playerId);
        if (current == null || current.region() == null) {
            return null;
        }
        Session updated = new Session(Step.PRICE, current.region(), auctionId, 0);
        sessions.put(playerId, updated);
        return updated;
    }

    public Session submitPrice(UUID playerId, int price) {
        Session current = sessions.get(playerId);
        if (current == null || current.region() == null || current.auctionId() == null) {
            return null;
        }
        Session updated = new Session(Step.DESCRIPTION, current.region(), current.auctionId(), price);
        sessions.put(playerId, updated);
        return updated;
    }
}
