package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.model.AuditLogEntry;
import bm.b0b0b0.soulAuction.model.ClaimEntry;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.LimitOverrideEntry;
import bm.b0b0b0.soulAuction.model.PendingExpiredListingNotification;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AuctionRuntimeStorage {

    private final Path claimsFile;
    private final Path historyFile;
    private final Path limitsFile;
    private final Path notificationsFile;
    private final Path favoritesFile;
    private final Path favoriteListingsFile;
    private final Path cooldownsFile;
    private final Path runtimeBlacklistFile;
    private final Path auditFile;
    private final Gson gson;
    private final ExecutorService ioExecutor;
    private final List<ClaimEntry> claims;
    private final List<DealHistoryEntry> history;
    private final List<LimitOverrideEntry> limitOverrides;
    private final List<PendingSaleNotification> notifications;
    private final List<PendingExpiredListingNotification> expiredListingNotifications;
    private final Map<UUID, List<UUID>> favoriteSellersByViewer;
    private final Map<UUID, List<Long>> favoriteListingsByViewer;
    private final Map<UUID, Long> lastSellEpochMillis;
    private final List<UUID> runtimeBlacklist;
    private final List<AuditLogEntry> auditLog;
    private final AtomicLong nextClaimId;
    private final AtomicLong nextHistoryId;
    private final AtomicLong nextAuditId;
    private final AtomicBoolean flushScheduled;

    public AuctionRuntimeStorage(Path dataFolder) {
        Path dataDirectory = dataFolder.resolve("data");
        this.claimsFile = dataDirectory.resolve("claims.json");
        this.historyFile = dataDirectory.resolve("history.json");
        this.limitsFile = dataDirectory.resolve("limits.json");
        this.notificationsFile = dataDirectory.resolve("notifications.json");
        this.favoritesFile = dataDirectory.resolve("favorites.json");
        this.favoriteListingsFile = dataDirectory.resolve("favorite-listings.json");
        this.cooldownsFile = dataDirectory.resolve("sell-cooldowns.json");
        this.runtimeBlacklistFile = dataDirectory.resolve("runtime-blacklist.json");
        this.auditFile = dataDirectory.resolve("audit.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.ioExecutor = Executors.newSingleThreadExecutor();
        this.claims = new ArrayList<>();
        this.history = new ArrayList<>();
        this.limitOverrides = new ArrayList<>();
        this.notifications = new ArrayList<>();
        this.expiredListingNotifications = new ArrayList<>();
        this.favoriteSellersByViewer = new HashMap<>();
        this.favoriteListingsByViewer = new HashMap<>();
        this.lastSellEpochMillis = new HashMap<>();
        this.runtimeBlacklist = new ArrayList<>();
        this.auditLog = new ArrayList<>();
        this.nextClaimId = new AtomicLong(1L);
        this.nextHistoryId = new AtomicLong(1L);
        this.nextAuditId = new AtomicLong(1L);
        this.flushScheduled = new AtomicBoolean(false);
    }

    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(claimsFile.getParent());
                loadClaimsSync();
                loadHistorySync();
                loadLimitsSync();
                loadNotificationsSync();
                loadFavoritesSync();
                loadFavoriteListingsSync();
                loadCooldownsSync();
                loadRuntimeBlacklistSync();
                loadAuditSync();
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
        scheduleDebouncedFlush();
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
                    scheduleDebouncedFlush();
                    return entry;
                }
            }
        }
        return null;
    }

    public void restoreClaim(ClaimEntry entry) {
        if (entry == null) {
            return;
        }
        synchronized (claims) {
            for (ClaimEntry existing : claims) {
                if (existing.claimId() == entry.claimId()) {
                    return;
                }
            }
            claims.add(entry);
        }
        scheduleDebouncedFlush();
    }

    public void addHistory(
            String action,
            String auctionId,
            long listingId,
            UUID sellerId,
            UUID buyerId,
            int price,
            int tax,
            AuctionEconomyType economyType,
            int buyTax
    ) {
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
                System.currentTimeMillis(),
                buyTax
        );
        synchronized (history) {
            history.add(entry);
            if (history.size() > 5000) {
                history.remove(0);
            }
        }
        scheduleDebouncedFlush();
    }

    public List<DealHistoryEntry> recentHistory(int limit) {
        synchronized (history) {
            int size = history.size();
            if (size == 0) {
                return List.of();
            }
            int take = Math.min(limit, size);
            List<DealHistoryEntry> output = new ArrayList<>(take);
            for (int i = size - 1; i >= 0 && output.size() < take; i--) {
                output.add(history.get(i));
            }
            return output;
        }
    }

    public List<DealHistoryEntry> playerHistory(
            UUID playerId,
            String action,
            boolean asBuyer,
            boolean asSeller,
            String auctionId,
            int limit
    ) {
        List<DealHistoryEntry> snapshot;
        synchronized (history) {
            snapshot = new ArrayList<>(history);
        }
        List<DealHistoryEntry> output = new ArrayList<>(Math.min(limit, 64));
        for (int i = snapshot.size() - 1; i >= 0 && output.size() < limit; i--) {
            DealHistoryEntry entry = snapshot.get(i);
            if (action != null && !action.isBlank() && !action.equalsIgnoreCase(entry.action())) {
                continue;
            }
            if (auctionId != null && !auctionId.isBlank() && !entry.auctionId().equalsIgnoreCase(auctionId)) {
                continue;
            }
            if (asBuyer && entry.buyerId() != null && entry.buyerId().equals(playerId)) {
                output.add(entry);
                continue;
            }
            if (asSeller && entry.sellerId().equals(playerId)) {
                output.add(entry);
            }
        }
        return output;
    }

    public List<ClaimEntry> claimsByReasons(UUID ownerId, List<String> reasons) {
        List<ClaimEntry> output = new ArrayList<>();
        synchronized (claims) {
            for (ClaimEntry claim : claims) {
                if (!claim.ownerId().equals(ownerId)) {
                    continue;
                }
                if (reasons == null || reasons.isEmpty()) {
                    output.add(claim);
                    continue;
                }
                for (String reason : reasons) {
                    if (reason != null && reason.equalsIgnoreCase(claim.reason())) {
                        output.add(claim);
                        break;
                    }
                }
            }
        }
        output.sort(Comparator.comparingLong(ClaimEntry::claimId).reversed());
        return output;
    }

    public boolean toggleFavoriteSeller(UUID viewerId, UUID sellerId) {
        synchronized (favoriteSellersByViewer) {
            List<UUID> favorites = favoriteSellersByViewer.computeIfAbsent(viewerId, ignored -> new ArrayList<>());
            if (favorites.contains(sellerId)) {
                favorites.remove(sellerId);
                scheduleDebouncedFlush();
                return false;
            }
            favorites.add(sellerId);
            scheduleDebouncedFlush();
            return true;
        }
    }

    public boolean isFavoriteSeller(UUID viewerId, UUID sellerId) {
        synchronized (favoriteSellersByViewer) {
            List<UUID> favorites = favoriteSellersByViewer.get(viewerId);
            return favorites != null && favorites.contains(sellerId);
        }
    }

    public List<UUID> favoriteSellers(UUID viewerId) {
        synchronized (favoriteSellersByViewer) {
            List<UUID> favorites = favoriteSellersByViewer.get(viewerId);
            if (favorites == null || favorites.isEmpty()) {
                return List.of();
            }
            return List.copyOf(favorites);
        }
    }

    public boolean toggleFavoriteListing(UUID viewerId, long listingId) {
        synchronized (favoriteListingsByViewer) {
            List<Long> favorites = favoriteListingsByViewer.computeIfAbsent(viewerId, ignored -> new ArrayList<>());
            if (favorites.contains(listingId)) {
                favorites.remove(listingId);
                scheduleDebouncedFlush();
                return false;
            }
            favorites.add(listingId);
            scheduleDebouncedFlush();
            return true;
        }
    }

    public boolean isFavoriteListing(UUID viewerId, long listingId) {
        synchronized (favoriteListingsByViewer) {
            List<Long> favorites = favoriteListingsByViewer.get(viewerId);
            return favorites != null && favorites.contains(listingId);
        }
    }

    public Set<Long> favoriteListings(UUID viewerId) {
        synchronized (favoriteListingsByViewer) {
            List<Long> favorites = favoriteListingsByViewer.get(viewerId);
            if (favorites == null || favorites.isEmpty()) {
                return Set.of();
            }
            return Set.copyOf(favorites);
        }
    }

    private void loadFavoriteListingsSync() throws Exception {
        synchronized (favoriteListingsByViewer) {
            favoriteListingsByViewer.clear();
            if (!Files.exists(favoriteListingsFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(favoriteListingsFile)) {
                FavoriteListingsPayload payload = gson.fromJson(reader, FavoriteListingsPayload.class);
                if (payload != null && payload.entries != null) {
                    for (FavoriteListingEntry entry : payload.entries) {
                        if (entry.viewerId != null && entry.listingIds != null) {
                            favoriteListingsByViewer.put(entry.viewerId, new ArrayList<>(entry.listingIds));
                        }
                    }
                }
            }
        }
    }

    public void recordSell(UUID sellerId) {
        synchronized (lastSellEpochMillis) {
            lastSellEpochMillis.put(sellerId, System.currentTimeMillis());
        }
        scheduleDebouncedFlush();
    }

    public long lastSellEpochMillis(UUID sellerId) {
        synchronized (lastSellEpochMillis) {
            return lastSellEpochMillis.getOrDefault(sellerId, 0L);
        }
    }

    public void addRuntimeBlacklist(UUID playerId) {
        synchronized (runtimeBlacklist) {
            if (!runtimeBlacklist.contains(playerId)) {
                runtimeBlacklist.add(playerId);
            }
        }
        scheduleDebouncedFlush();
    }

    public void removeRuntimeBlacklist(UUID playerId) {
        synchronized (runtimeBlacklist) {
            runtimeBlacklist.remove(playerId);
        }
        scheduleDebouncedFlush();
    }

    public boolean isRuntimeBlacklisted(UUID playerId) {
        synchronized (runtimeBlacklist) {
            return runtimeBlacklist.contains(playerId);
        }
    }

    public void addAudit(UUID actorId, String actorName, String action, String details) {
        AuditLogEntry entry = new AuditLogEntry(
                nextAuditId.getAndIncrement(),
                System.currentTimeMillis(),
                actorId,
                actorName == null ? "-" : actorName,
                action,
                details == null ? "" : details
        );
        synchronized (auditLog) {
            auditLog.add(entry);
            if (auditLog.size() > 3000) {
                auditLog.remove(0);
            }
        }
        scheduleDebouncedFlush();
    }

    public List<AuditLogEntry> recentAudit(int limit) {
        synchronized (auditLog) {
            int size = auditLog.size();
            if (size == 0) {
                return List.of();
            }
            int take = Math.min(limit, size);
            List<AuditLogEntry> output = new ArrayList<>(take);
            for (int i = size - 1; i >= 0 && output.size() < take; i--) {
                output.add(auditLog.get(i));
            }
            return output;
        }
    }

    public int purgeHistoryOlderThan(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - Math.max(0L, maxAgeMillis);
        int removed;
        synchronized (history) {
            int before = history.size();
            history.removeIf(entry -> entry.createdAtEpochMillis() < cutoff);
            removed = before - history.size();
        }
        if (removed > 0) {
            scheduleDebouncedFlush();
        }
        return removed;
    }

    public ClaimEntry removeClaimById(long claimId) {
        synchronized (claims) {
            for (int i = 0; i < claims.size(); i++) {
                ClaimEntry entry = claims.get(i);
                if (entry.claimId() == claimId) {
                    claims.remove(i);
                    scheduleDebouncedFlush();
                    return entry;
                }
            }
        }
        return null;
    }

    private void scheduleDebouncedFlush() {
        if (!flushScheduled.compareAndSet(false, true)) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                Thread.sleep(2000L);
                flushSync();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            } finally {
                flushScheduled.set(false);
            }
        });
    }

    public void setLimitOverride(UUID playerId, String scope, int limit) {
        String normalizedScope = scope.toLowerCase();
        synchronized (limitOverrides) {
            limitOverrides.removeIf(entry -> entry.playerId().equals(playerId) && entry.scope().equalsIgnoreCase(normalizedScope));
            if (limit > 0) {
                limitOverrides.add(new LimitOverrideEntry(playerId, normalizedScope, limit));
            }
        }
        scheduleDebouncedFlush();
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
        scheduleDebouncedFlush();
    }

    public void addPendingExpiredListingNotification(PendingExpiredListingNotification notification) {
        synchronized (notifications) {
            expiredListingNotifications.add(notification);
        }
        scheduleDebouncedFlush();
    }

    public List<PendingExpiredListingNotification> takePendingExpiredListingNotifications(UUID playerId) {
        List<PendingExpiredListingNotification> output = new ArrayList<>();
        synchronized (notifications) {
            expiredListingNotifications.removeIf(notification -> {
                if (notification.playerId().equals(playerId)) {
                    output.add(notification);
                    return true;
                }
                return false;
            });
        }
        if (!output.isEmpty()) {
            scheduleDebouncedFlush();
        }
        return output;
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
            scheduleDebouncedFlush();
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
            expiredListingNotifications.clear();
            if (!Files.exists(notificationsFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(notificationsFile)) {
                NotificationsPayload payload = gson.fromJson(reader, NotificationsPayload.class);
                if (payload != null && payload.entries != null) {
                    notifications.addAll(payload.entries);
                }
                if (payload != null && payload.expiredEntries != null) {
                    expiredListingNotifications.addAll(payload.expiredEntries);
                }
            }
        }
    }

    private void loadFavoritesSync() throws Exception {
        synchronized (favoriteSellersByViewer) {
            favoriteSellersByViewer.clear();
            if (!Files.exists(favoritesFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(favoritesFile)) {
                FavoritesPayload payload = gson.fromJson(reader, FavoritesPayload.class);
                if (payload != null && payload.entries != null) {
                    for (FavoriteEntry entry : payload.entries) {
                        favoriteSellersByViewer.put(entry.viewerId, new ArrayList<>(entry.sellerIds));
                    }
                }
            }
        }
    }

    private void loadCooldownsSync() throws Exception {
        synchronized (lastSellEpochMillis) {
            lastSellEpochMillis.clear();
            if (!Files.exists(cooldownsFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(cooldownsFile)) {
                CooldownsPayload payload = gson.fromJson(reader, CooldownsPayload.class);
                if (payload != null && payload.entries != null) {
                    for (CooldownEntry entry : payload.entries) {
                        lastSellEpochMillis.put(entry.playerId, entry.lastSellEpochMillis);
                    }
                }
            }
        }
    }

    private void loadRuntimeBlacklistSync() throws Exception {
        synchronized (runtimeBlacklist) {
            runtimeBlacklist.clear();
            if (!Files.exists(runtimeBlacklistFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(runtimeBlacklistFile)) {
                BlacklistPayload payload = gson.fromJson(reader, BlacklistPayload.class);
                if (payload != null && payload.playerIds != null) {
                    runtimeBlacklist.addAll(payload.playerIds);
                }
            }
        }
    }

    private void loadAuditSync() throws Exception {
        synchronized (auditLog) {
            auditLog.clear();
            if (!Files.exists(auditFile)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(auditFile)) {
                AuditPayload payload = gson.fromJson(reader, AuditPayload.class);
                long maxId = 0L;
                if (payload != null && payload.entries != null) {
                    auditLog.addAll(payload.entries);
                    for (AuditLogEntry entry : payload.entries) {
                        if (entry.auditId() > maxId) {
                            maxId = entry.auditId();
                        }
                    }
                }
                long suggested = payload != null ? payload.nextAuditId : 1L;
                nextAuditId.set(Math.max(maxId + 1L, Math.max(1L, suggested)));
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
            notificationsPayload.expiredEntries = new ArrayList<>(expiredListingNotifications);
            try (Writer writer = Files.newBufferedWriter(notificationsFile)) {
                gson.toJson(notificationsPayload, writer);
            }
        }
        synchronized (favoriteSellersByViewer) {
            FavoritesPayload favoritesPayload = new FavoritesPayload();
            favoritesPayload.entries = new ArrayList<>();
            for (Map.Entry<UUID, List<UUID>> entry : favoriteSellersByViewer.entrySet()) {
                favoritesPayload.entries.add(new FavoriteEntry(entry.getKey(), new ArrayList<>(entry.getValue())));
            }
            try (Writer writer = Files.newBufferedWriter(favoritesFile)) {
                gson.toJson(favoritesPayload, writer);
            }
        }
        synchronized (favoriteListingsByViewer) {
            FavoriteListingsPayload favoriteListingsPayload = new FavoriteListingsPayload();
            favoriteListingsPayload.entries = new ArrayList<>();
            for (Map.Entry<UUID, List<Long>> entry : favoriteListingsByViewer.entrySet()) {
                favoriteListingsPayload.entries.add(new FavoriteListingEntry(entry.getKey(), new ArrayList<>(entry.getValue())));
            }
            try (Writer writer = Files.newBufferedWriter(favoriteListingsFile)) {
                gson.toJson(favoriteListingsPayload, writer);
            }
        }
        synchronized (lastSellEpochMillis) {
            CooldownsPayload cooldownsPayload = new CooldownsPayload();
            cooldownsPayload.entries = new ArrayList<>();
            for (Map.Entry<UUID, Long> entry : lastSellEpochMillis.entrySet()) {
                cooldownsPayload.entries.add(new CooldownEntry(entry.getKey(), entry.getValue()));
            }
            try (Writer writer = Files.newBufferedWriter(cooldownsFile)) {
                gson.toJson(cooldownsPayload, writer);
            }
        }
        synchronized (runtimeBlacklist) {
            BlacklistPayload blacklistPayload = new BlacklistPayload();
            blacklistPayload.playerIds = new ArrayList<>(runtimeBlacklist);
            try (Writer writer = Files.newBufferedWriter(runtimeBlacklistFile)) {
                gson.toJson(blacklistPayload, writer);
            }
        }
        synchronized (auditLog) {
            AuditPayload auditPayload = new AuditPayload();
            auditPayload.nextAuditId = nextAuditId.get();
            auditPayload.entries = new ArrayList<>(auditLog);
            try (Writer writer = Files.newBufferedWriter(auditFile)) {
                gson.toJson(auditPayload, writer);
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
        private List<PendingExpiredListingNotification> expiredEntries;
    }

    private static final class FavoriteEntry {
        private UUID viewerId;
        private List<UUID> sellerIds;

        private FavoriteEntry(UUID viewerId, List<UUID> sellerIds) {
            this.viewerId = viewerId;
            this.sellerIds = sellerIds;
        }
    }

    private static final class FavoritesPayload {
        private List<FavoriteEntry> entries;
    }

    private static final class FavoriteListingEntry {
        private UUID viewerId;
        private List<Long> listingIds;

        private FavoriteListingEntry(UUID viewerId, List<Long> listingIds) {
            this.viewerId = viewerId;
            this.listingIds = listingIds;
        }
    }

    private static final class FavoriteListingsPayload {
        private List<FavoriteListingEntry> entries;
    }

    private static final class CooldownEntry {
        private UUID playerId;
        private long lastSellEpochMillis;

        private CooldownEntry(UUID playerId, long lastSellEpochMillis) {
            this.playerId = playerId;
            this.lastSellEpochMillis = lastSellEpochMillis;
        }
    }

    private static final class CooldownsPayload {
        private List<CooldownEntry> entries;
    }

    private static final class BlacklistPayload {
        private List<UUID> playerIds;
    }

    private static final class AuditPayload {
        private long nextAuditId;
        private List<AuditLogEntry> entries;
    }
}
