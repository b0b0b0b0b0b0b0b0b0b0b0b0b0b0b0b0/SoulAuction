package bm.b0b0b0.soulAuction.service.migration;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.StorageMode;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.repository.AuctionRepositoryFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionStorageMigrator {

    public record Result(
            int imported,
            int skipped,
            int failed,
            int sourceTotal,
            boolean dryRun,
            StorageMode sourceMode,
            StorageMode targetMode,
            boolean archived
    ) {
    }

    public Result migrate(
            JavaPlugin plugin,
            AuctionSettings settings,
            AuctionRepository target,
            StorageMode sourceMode,
            boolean dryRun,
            boolean archiveSource
    ) {
        StorageMode targetMode = StorageMode.fromString(settings.storage.mode);
        if (sourceMode == targetMode) {
            throw new IllegalArgumentException("source-equals-target");
        }
        Path dataFolder = plugin.getDataFolder().toPath();
        if (!hasExportableData(dataFolder, settings, sourceMode)) {
            throw new IllegalArgumentException("source-empty");
        }
        AuctionRepository source = AuctionRepositoryFactory.create(plugin, settings, sourceMode);
        try {
            source.load().get(120L, TimeUnit.SECONDS);
            List<AuctionListing> listings = source.listAll();
            int imported = 0;
            int skipped = 0;
            int failed = 0;
            if (dryRun) {
                for (AuctionListing listing : listings) {
                    if (target.findById(listing.listingId()) != null) {
                        skipped++;
                    } else {
                        imported++;
                    }
                }
            } else {
                for (AuctionListing listing : listings) {
                    if (target.findById(listing.listingId()) != null) {
                        skipped++;
                        continue;
                    }
                    if (target.importListing(listing)) {
                        imported++;
                    } else {
                        failed++;
                    }
                }
                try {
                    target.flush().get(60L, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("flush-failed", exception);
                }
            }
            boolean archived = false;
            if (!dryRun && archiveSource && imported > 0 && failed == 0) {
                archived = archiveLegacyStorage(dataFolder, settings, sourceMode);
            }
            return new Result(
                    imported,
                    skipped,
                    failed,
                    listings.size(),
                    dryRun,
                    sourceMode,
                    targetMode,
                    archived
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("migrate-failed", exception);
        } finally {
            try {
                source.close().get(30L, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean hasExportableData(Path dataFolder, AuctionSettings settings, StorageMode mode) {
        return switch (mode) {
            case JSON -> hasFlatFiles(dataFolder.resolve(settings.storage.flatDirectory), ".json", "meta.json");
            case YAML -> hasFlatFiles(dataFolder.resolve(settings.storage.flatDirectory), ".yml", "meta.yml");
            case SQLITE -> {
                Path db = dataFolder.resolve(settings.storage.database.sqliteFile);
                yield Files.isRegularFile(db) && db.toFile().length() > 0L;
            }
            case MYSQL -> false;
        };
    }

    public static StorageMode detectLegacyStorage(Path dataFolder, AuctionSettings settings, StorageMode activeMode) {
        for (StorageMode candidate : StorageMode.values()) {
            if (candidate == activeMode || candidate == StorageMode.MYSQL) {
                continue;
            }
            if (hasExportableData(dataFolder, settings, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasFlatFiles(Path directory, String extension, String metaFileName) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var stream = Files.list(directory)) {
            return stream.anyMatch(path -> {
                String name = path.getFileName().toString();
                return name.endsWith(extension) && !name.equals(metaFileName);
            });
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean archiveLegacyStorage(Path dataFolder, AuctionSettings settings, StorageMode sourceMode) {
        String suffix = ".migrated-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withLocale(Locale.ROOT)
                .format(Instant.now());
        try {
            return switch (sourceMode) {
                case JSON, YAML -> {
                    Path directory = dataFolder.resolve(settings.storage.flatDirectory);
                    if (!Files.isDirectory(directory)) {
                        yield false;
                    }
                    Path target = dataFolder.resolve(settings.storage.flatDirectory + suffix);
                    Files.move(directory, target);
                    yield true;
                }
                case SQLITE -> {
                    Path db = dataFolder.resolve(settings.storage.database.sqliteFile);
                    if (!Files.isRegularFile(db)) {
                        yield false;
                    }
                    Files.move(db, Path.of(db.toString() + suffix));
                    yield true;
                }
                case MYSQL -> false;
            };
        } catch (IOException exception) {
            return false;
        }
    }
}
