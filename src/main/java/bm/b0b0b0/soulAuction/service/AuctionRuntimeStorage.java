package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.model.ClaimEntry;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.LimitOverrideEntry;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class AuctionRuntimeStorage {

    private final Path claimsFile;
    private final Path historyFile;
    private final Path limitsFile;
    private final Path notificationsFile;
    private final Gson gson;
    private final ExecutorService ioExecutor;
    private final List<ClaimEntry> claims;
    private final List<DealHistoryEntry> history;
    private final List<LimitOverrideEntry> limitOverrides;
    private final List<PendingSaleNotification> notifications;
    private final AtomicLong nextClaimId;
    private final AtomicLong nextHistoryId;

    public AuctionRuntimeStorage(Path dataFolder) {
        Path dataDirectory = dataFolder.resolve("data");
        this.claimsFile = dataDirectory.resolve("claims.json");
        this.historyFile = dataDirectory.resolve("history.json");
        this.limitsFile = dataDirectory.resolve("limits.json");
        this.notificationsFile = dataDirectory.resolve("notifications.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.ioExecutor = Executors.newSingleThreadExecutor();
        this.claims = new ArrayList<>();
        this.history = new ArrayList<>();
        this.limitOverrides = new ArrayList<>();
        this.notifications = new ArrayList<>();
        this.nextClaimId = new AtomicLong(1L);
        this.nextHistoryId = new AtomicLong(1L);
    }

    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(claimsFile.getParent());
                loadClaimsSync();
                loadHistorySync();
                loadLimitsSync();
                loadNotificationsSync();
                flushSync();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, ioExecutor);
    }

    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> {
            try {
                flushSync();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, ioExecutor);
    }

    public CompletableFuture<Void> close() {
        return flush().whenComplete((unused, throwable) -> ioExecutor.shutdown());
    }

    public ClaimEntry addClaim(UUID ownerId, String auctionId, long sourceListingId, String itemBase64, String reason) {
        ClaimEntry entry = new ClaimEntry(
                nextClaimId.getAndIncrement(),
                ownerId,
                auctionId,
                sourceListingId,
                itemBase64,
                System.currentTimeMillis(),
                reason
        );
        synchronized (claims) {
            claims.add(entry);
        }
        flush();
        return entry;
    }

    public List<ClaimEntry> listClaims(UUID ownerId) {
        List<ClaimEntry> output = new ArrayList<>();
        synchronized (claims) {
            for (ClaimEntry claim : claims) {
                if (claim.ownerId().equals(ownerId)) {
                    output.add(claim);
                }
            }
        }
        output.sort(Comparator.comparingLong(ClaimEntry::claimId));
        return output;
    }

    public ClaimEntry removeClaim(long claimId, UUID ownerId) {
        synchronized (claims) {
            for (int i = 0; i < claims.size(); i++) {
                ClaimEntry entry = claims.get(i);
                if (entry.claimId() == claimId && entry.ownerId().equals(ownerId)) {
                    claims.remove(i);
                    flush();
                    return entry;
                }
            }
        }
        return null;
    }

    public void addHistory(String action, String auctionId, long listingId, UUID sellerId, UUID buyerId, int price, int tax, AuctionEconomyType economyType) {
        DealHistoryEntry entry = new DealHistoryEntry(
                nextHistoryId.getAndIncrement(),
                action,
                auctionId,
                listingId,
                sellerId,
                buyerId,
                price,
                tax,
                economyType,
                System.currentTimeMillis()
        );
        synchronized (history) {
            history.add(entry);
            if (history.size() > 5000) {
                history.remove(0);
            }
        }
        flush();
    }

    public List<DealHistoryEntry> recentHistory(int limit) {
        List<DealHistoryEntry> output;
        synchronized (history) {
            output = new ArrayList<>(history);
        }
        output.sort(Comparator.comparingLong(DealHistoryEntry::historyId).reversed());
        if (output.size() > limit) {
            return output.subList(0, limit);
        }
        return output;
    }

    public void setLimitOverride(UUID playerId, String scope, int limit) {
        String normalizedScope = scope.toLowerCase();
        synchronized (limitOverrides) {
            limitOverrides.removeIf(entry -> entry.playerId().equals(playerId) && entry.scope().equalsIgnoreCase(normalizedScope));
            if (limit > 0) {
                limitOverrides.add(new LimitOverrideEntry(playerId, normalizedScope, limit));
            }
        }
        flush();
    }

    public int getLimitOverride(UUID playerId, String scope) {
        String normalizedScope = scope.toLowerCase();
        synchronized (limitOverrides) {
            for (LimitOverrideEntry entry : limitOverrides) {
                if (entry.playerId().equals(playerId) && entry.scope().equalsIgnoreCase(normalizedScope)) {
                    return Math.max(0, entry.limit());
                }
            }
        }
        return 0;
    }

    public void addPendingSaleNotification(PendingSaleNotification notification) {
        synchronized (notifications) {
            notifications.add(notification);
        }
        flush();
    }

    public List<PendingSaleNotification> takePendingSaleNotifications(UUID playerId) {
        List<PendingSaleNotification> output = new ArrayList<>();
        synchronized (notifications) {
            notifications.removeIf(notification -> {
                if (notification.playerId().equals(playerId)) {
                    output.add(notification);
                    return true;
                }
                return false;
            });
        }
        if (!output.isEmpty()) {
            flush();
        }
        return output;
    }

    private void loadClaimsSync() throws Exception {
        synchronized (claims) {
            claims.clear();
            if (!Files.exists(claimsFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(claimsFile)) {
                ClaimsPayload payload = gson.fromJson(reader, ClaimsPayload.class);
                long maxId = 0L;
                if (payload != null && payload.claims != null) {
                    claims.addAll(payload.claims);
                    for (ClaimEntry entry : payload.claims) {
                        if (entry.claimId() > maxId) {
                            maxId = entry.claimId();
                        }
                    }
                }
                long suggested = payload != null ? payload.nextClaimId : 1L;
                nextClaimId.set(Math.max(maxId + 1L, Math.max(1L, suggested)));
            }
        }
    }

    private void loadHistorySync() throws Exception {
        synchronized (history) {
            history.clear();
            if (!Files.exists(historyFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(historyFile)) {
                HistoryPayload payload = gson.fromJson(reader, HistoryPayload.class);
                long maxId = 0L;
                if (payload != null && payload.entries != null) {
                    history.addAll(payload.entries);
                    for (DealHistoryEntry entry : payload.entries) {
                        if (entry.historyId() > maxId) {
                            maxId = entry.historyId();
                        }
                    }
                }
                long suggested = payload != null ? payload.nextHistoryId : 1L;
                nextHistoryId.set(Math.max(maxId + 1L, Math.max(1L, suggested)));
            }
        }
    }

    private void loadLimitsSync() throws Exception {
        synchronized (limitOverrides) {
            limitOverrides.clear();
            if (!Files.exists(limitsFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(limitsFile)) {
                LimitsPayload payload = gson.fromJson(reader, LimitsPayload.class);
                if (payload != null && payload.entries != null) {
                    limitOverrides.addAll(payload.entries);
                }
            }
        }
    }

    private void loadNotificationsSync() throws Exception {
        synchronized (notifications) {
            notifications.clear();
            if (!Files.exists(notificationsFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(notificationsFile)) {
                NotificationsPayload payload = gson.fromJson(reader, NotificationsPayload.class);
                if (payload != null && payload.entries != null) {
                    notifications.addAll(payload.entries);
                }
            }
        }
    }

    private void flushSync() throws Exception {
        synchronized (claims) {
            ClaimsPayload claimsPayload = new ClaimsPayload();
            claimsPayload.nextClaimId = nextClaimId.get();
            claimsPayload.claims = new ArrayList<>(claims);
            try (Writer writer = Files.newBufferedWriter(claimsFile)) {
                gson.toJson(claimsPayload, writer);
            }
        }
        synchronized (history) {
            HistoryPayload historyPayload = new HistoryPayload();
            historyPayload.nextHistoryId = nextHistoryId.get();
            historyPayload.entries = new ArrayList<>(history);
            try (Writer writer = Files.newBufferedWriter(historyFile)) {
                gson.toJson(historyPayload, writer);
            }
        }
        synchronized (limitOverrides) {
            LimitsPayload limitsPayload = new LimitsPayload();
            limitsPayload.entries = new ArrayList<>(limitOverrides);
            try (Writer writer = Files.newBufferedWriter(limitsFile)) {
                gson.toJson(limitsPayload, writer);
            }
        }
        synchronized (notifications) {
            NotificationsPayload notificationsPayload = new NotificationsPayload();
            notificationsPayload.entries = new ArrayList<>(notifications);
            try (Writer writer = Files.newBufferedWriter(notificationsFile)) {
                gson.toJson(notificationsPayload, writer);
            }
        }
    }

    private static final class ClaimsPayload {
        private long nextClaimId;
        private List<ClaimEntry> claims;
    }

    private static final class HistoryPayload {
        private long nextHistoryId;
        private List<DealHistoryEntry> entries;
    }

    private static final class LimitsPayload {
        private List<LimitOverrideEntry> entries;
    }

    private static final class NotificationsPayload {
        private List<PendingSaleNotification> entries;
    }
}
