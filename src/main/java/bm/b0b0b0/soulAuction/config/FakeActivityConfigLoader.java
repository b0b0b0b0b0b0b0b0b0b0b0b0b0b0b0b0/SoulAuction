package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivityItemSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivityItemsSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivityItemsSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivitySellersSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivitySettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class FakeActivityConfigLoader {

    private final Path dataFolderPath;

    public FakeActivityConfigLoader(JavaPlugin plugin) {
        this.dataFolderPath = plugin.getDataFolder().toPath();
    }

    public FakeActivityConfig load(AuctionSettings auctionSettings) {
        Path root = dataFolderPath.resolve(auctionSettings.fakeActivity.directory);
        try {
            Files.createDirectories(root);
            FakeActivitySettings settings = SerializedConfigReloader.reload(
                    new FakeActivitySettings(),
                    root.resolve("settings.yml")
            );
            FakeActivitySellersSettings sellers = SerializedConfigReloader.reload(
                    new FakeActivitySellersSettings(),
                    root.resolve("sellers.yml")
            );
            List<FakeActivityItemSettings> items = loadItems(root);
            return new FakeActivityConfig(settings, sellers, List.copyOf(items));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load fake activity config", exception);
        }
    }

    public boolean registerSellerIfAbsent(AuctionSettings auctionSettings, String sellerName) throws IOException {
        String trimmed = sellerName == null ? "" : sellerName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 16) {
            return false;
        }
        Path root = dataFolderPath.resolve(auctionSettings.fakeActivity.directory);
        Files.createDirectories(root);
        Path sellersFile = root.resolve("sellers.yml");
        FakeActivitySellersSettings sellers = SerializedConfigReloader.reload(
                new FakeActivitySellersSettings(),
                sellersFile
        );
        List<String> names = sellers.names == null ? new ArrayList<>() : new ArrayList<>(sellers.names);
        for (String existing : names) {
            if (existing != null && existing.equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        names.add(trimmed);
        sellers.names = names;
        SerializedConfigReloader.reload(sellers, sellersFile);
        return true;
    }

    public boolean registerItemIfAbsent(AuctionSettings auctionSettings, ItemStack stack, String auctionId) throws IOException {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Path root = dataFolderPath.resolve(auctionSettings.fakeActivity.directory);
        Files.createDirectories(root);
        Path itemsFile = root.resolve("items.yml");
        FakeActivityItemsSettings itemsSettings = SerializedConfigReloader.reload(
                new FakeActivityItemsSettings(),
                itemsFile
        );
        List<FakeActivityItemSettings> items = itemsSettings.items == null
                ? new ArrayList<>()
                : new ArrayList<>(itemsSettings.items);
        FakeActivityItemSettings candidate = FakeActivityItemEntryFactory.build(
                stack,
                auctionId,
                FakeActivityItemEntryFactory.collectIds(items)
        );
        for (FakeActivityItemSettings existing : items) {
            if (FakeActivityItemEntryFactory.matches(existing, candidate)) {
                return false;
            }
        }
        items.add(candidate);
        itemsSettings.items = items;
        SerializedConfigReloader.reload(itemsSettings, itemsFile);
        return true;
    }

    private List<FakeActivityItemSettings> loadItems(Path root) throws IOException {
        Path itemsFile = root.resolve("items.yml");
        Path legacyDirectory = root.resolve("items");
        if (!Files.exists(itemsFile) && hasLegacyItemFiles(legacyDirectory)) {
            FakeActivityItemsSettings migrated = new FakeActivityItemsSettings();
            migrated.items = loadLegacyItems(legacyDirectory);
            SerializedConfigReloader.reload(migrated, itemsFile);
            return normalizeItems(migrated.items);
        }
        FakeActivityItemsSettings itemsSettings = SerializedConfigReloader.reload(
                new FakeActivityItemsSettings(),
                itemsFile
        );
        List<FakeActivityItemSettings> items = normalizeItems(itemsSettings.items);
        if (items.isEmpty() && hasLegacyItemFiles(legacyDirectory)) {
            items = normalizeItems(loadLegacyItems(legacyDirectory));
        }
        if (items.isEmpty()) {
            items = FakeActivityDefaults.defaultItems();
        }
        return items;
    }

    private static boolean hasLegacyItemFiles(Path legacyDirectory) throws IOException {
        if (!Files.isDirectory(legacyDirectory)) {
            return false;
        }
        try (var stream = Files.list(legacyDirectory)) {
            return stream.anyMatch(path -> path.getFileName().toString().endsWith(".yml"));
        }
    }

    private static List<FakeActivityItemSettings> loadLegacyItems(Path legacyDirectory) throws IOException {
        List<FakeActivityItemSettings> items = new ArrayList<>();
        try (var stream = Files.list(legacyDirectory)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                FakeActivityItemSettings item = SerializedConfigReloader.reload(new FakeActivityItemSettings(), file);
                String fileId = fileNameId(file);
                if (item.id == null || item.id.isBlank() || item.id.equals("item")) {
                    item.id = fileId;
                }
                items.add(item);
            }
        }
        return items;
    }

    private static List<FakeActivityItemSettings> normalizeItems(List<FakeActivityItemSettings> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<FakeActivityItemSettings> items = new ArrayList<>(raw.size());
        int index = 0;
        for (FakeActivityItemSettings item : raw) {
            if (item == null) {
                continue;
            }
            if (item.id == null || item.id.isBlank() || item.id.equals("item")) {
                if (item.material != null && !item.material.isBlank()) {
                    item.id = item.material.toLowerCase();
                } else {
                    item.id = "item-" + index;
                }
            }
            items.add(item);
            index++;
        }
        return items;
    }

    private static String fileNameId(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".yml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
}
