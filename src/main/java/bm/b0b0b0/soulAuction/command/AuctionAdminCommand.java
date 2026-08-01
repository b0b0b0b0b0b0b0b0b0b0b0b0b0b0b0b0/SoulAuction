package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.gui.admin.AdminGuiAccess;
import bm.b0b0b0.soulAuction.gui.admin.AdminAuctionsMenu;
import bm.b0b0b0.soulAuction.gui.PlayerRecordsMenu;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.ClaimEntry;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.model.PlayerHistoryView;
import bm.b0b0b0.soulAuction.model.StorageMode;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionCreateService;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionCreateService.Step;
import bm.b0b0b0.soulAuction.service.migration.AuctionStorageMigrator;
import bm.b0b0b0.soulAuction.util.ItemInspectionFormatter;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionAdminCommand {

    private static final String PERMISSION_ADMIN = "soulauction.command.admin";

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final MessageService messageService;
    private final AuctionService auctionService;
    private final AdminAuctionCreateService adminAuctionCreateService;

    public AuctionAdminCommand(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            MessageService messageService,
            AuctionService auctionService,
            AdminAuctionCreateService adminAuctionCreateService
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.messageService = messageService;
        this.auctionService = auctionService;
        this.adminAuctionCreateService = adminAuctionCreateService;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 1) {
            if (sender instanceof Player player && AdminGuiAccess.canOpenAdminGui(sender)) {
                return openAdminAuctionsGui(player, 0);
            }
            messageService.send(sender, "error-admin-usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        if (sub.equals("gui")) {
            return adminGui(sender, subArgs);
        }
        if (sub.equals("create")) {
            return adminCreate(sender, subArgs);
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            messageService.send(sender, "error-no-permission");
            return true;
        }
        return switch (sub) {
            case "history" -> history(sender, subArgs);
            case "selling" -> selling(sender, subArgs);
            case "blacklist" -> blacklist(sender, subArgs);
            case "recover" -> recover(sender, subArgs);
            case "audit" -> audit(sender, subArgs);
            case "cache" -> cache(sender, subArgs);
            case "sellfor" -> sellFor(sender, subArgs);
            case "fake" -> fakeListing(sender, subArgs);
            case "migrate" -> migrate(sender, subArgs);
            case "parse" -> parse(sender, subArgs);
            default -> {
                messageService.send(sender, "error-admin-usage");
                yield true;
            }
        };
    }

    public boolean purge(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            messageService.send(sender, "error-no-permission");
            return true;
        }
        if (args.length < 1) {
            messageService.send(sender, "error-purge-usage");
            return true;
        }
        int days;
        try {
            days = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            messageService.send(sender, "error-purge-usage");
            return true;
        }
        int removed = auctionService.purgeHistoryOlderThanDays(days);
        messageService.send(sender, "success-purge", Map.of("removed", String.valueOf(removed), "days", String.valueOf(days)));
        if (sender instanceof Player player) {
            auctionService.audit(player.getUniqueId(), player.getName(), "PURGE_HISTORY", "days=" + days + ",removed=" + removed);
        }
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        if (args.length < 1) {
            messageService.send(sender, "error-admin-history-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        int limit = 15;
        if (args.length >= 2) {
            try {
                limit = Math.min(100, Math.max(1, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                messageService.send(sender, "error-admin-history-usage");
                return true;
            }
        }
        List<DealHistoryEntry> entries = auctionService.adminHistoryForPlayer(target.getUniqueId(), limit);
        messageService.send(sender, 
                "admin-history-header",
                Map.of("player", target.getName() == null ? target.getUniqueId().toString() : target.getName(), "count", String.valueOf(entries.size()))
        );
        for (DealHistoryEntry entry : entries) {
            messageService.send(sender, 
                    "admin-history-line",
                    Map.of(
                            "action", entry.action(),
                            "id", String.valueOf(entry.listingId()),
                            "price", auctionService.formatPrice(entry.price(), entry.auctionId()),
                            "auction", entry.auctionId()
                    )
            );
        }
        return true;
    }

    private boolean selling(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            messageService.send(sender, "error-only-player");
            return true;
        }
        if (args.length < 1) {
            messageService.send(sender, "error-admin-selling-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String auctionId = args.length > 1 ? args[1] : auctionService.defaultAuctionId();
        if (!auctionService.auctionExists(auctionId)) {
            messageService.send(admin, "error-auction-not-found");
            return true;
        }
        PlayerRecordsMenu menu = new PlayerRecordsMenu(
                admin.getUniqueId(),
                target.getUniqueId(),
                auctionId,
                PlayerHistoryView.SELLING,
                auctionService,
                messageService
        );
        PluginSchedulers.run(plugin, admin, () -> admin.openInventory(menu.getInventory()));
        return true;
    }

    private boolean blacklist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageService.send(sender, "error-admin-blacklist-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        String actorName = sender.getName();
        String action = args[0].toLowerCase();
        if (action.equals("add")) {
            auctionService.adminBlacklistAdd(target.getUniqueId(), actorId, actorName);
            messageService.send(sender, "success-blacklist-add", Map.of("player", target.getName() == null ? target.getUniqueId().toString() : target.getName()));
        } else if (action.equals("remove")) {
            auctionService.adminBlacklistRemove(target.getUniqueId(), actorId, actorName);
            messageService.send(sender, "success-blacklist-remove", Map.of("player", target.getName() == null ? target.getUniqueId().toString() : target.getName()));
        } else {
            messageService.send(sender, "error-admin-blacklist-usage");
        }
        return true;
    }

    private boolean recover(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            messageService.send(sender, "error-only-player");
            return true;
        }
        if (args.length < 1) {
            messageService.send(sender, "error-admin-recover-usage");
            return true;
        }
        long claimId;
        try {
            claimId = Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            messageService.send(sender, "error-admin-recover-usage");
            return true;
        }
        ClaimEntry claim = auctionService.adminRecoverClaim(claimId);
        if (claim == null) {
            messageService.send(admin, "error-claim-not-found");
            return true;
        }
        ItemStack item = ItemStackCodec.decode(claim.itemBase64());
        var leftovers = admin.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            messageService.send(admin, "error-inventory-full");
            auctionService.restoreClaimEntry(claim);
            return true;
        }
        auctionService.audit(admin.getUniqueId(), admin.getName(), "ADMIN_RECOVER", "claimId=" + claimId);
        messageService.send(admin, "success-admin-recover", Map.of("id", String.valueOf(claimId)));
        return true;
    }

    private boolean cache(CommandSender sender, String[] args) {
        if (args.length < 1) {
            messageService.send(sender, "error-admin-cache-usage");
            return true;
        }
        String action = args[0].toLowerCase();
        return switch (action) {
            case "stats" -> {
                messageService.send(sender, "admin-cache-stats", Map.of("stats", auctionService.listingCacheStats()));
                yield true;
            }
            case "rebuild", "invalidate" -> {
                auctionService.rebuildListingCache();
                messageService.send(sender, "admin-cache-rebuilt");
                yield true;
            }
            default -> {
                messageService.send(sender, "error-admin-cache-usage");
                yield true;
            }
        };
    }

    private boolean sellFor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            messageService.send(sender, "error-only-player");
            return true;
        }
        if (args.length < 3) {
            messageService.send(sender, "error-admin-sellfor-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        Player online = target.getPlayer();
        if (online == null) {
            messageService.send(sender, "error-player-offline");
            return true;
        }
        String auctionId = args[1];
        int price;
        try {
            price = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            messageService.send(sender, "error-admin-sellfor-usage");
            return true;
        }
        ItemStack source = admin.getInventory().getItemInMainHand();
        if (source == null || source.isEmpty()) {
            messageService.send(sender, "error-main-hand-empty");
            return true;
        }
        ItemStack escrow = source.clone();
        admin.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
        var result = auctionService.createListingFromEscrow(online, auctionId, price, escrow);
        if (!result.success()) {
            Map<Integer, ItemStack> leftover = admin.getInventory().addItem(escrow);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(stack -> admin.getWorld().dropItemNaturally(admin.getLocation(), stack));
            }
            messageService.send(admin, result.failure().messageKey());
            return true;
        }
        messageService.send(admin, 
                "success-admin-sellfor",
                Map.of("player", online.getName(), "id", String.valueOf(result.listing().listingId()))
        );
        auctionService.audit(admin.getUniqueId(), admin.getName(), "ADMIN_SELLFOR", "target=" + online.getUniqueId());
        return true;
    }

    private boolean fakeListing(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            messageService.send(sender, "error-only-player");
            return true;
        }
        if (args.length < 3) {
            messageService.send(sender, "error-admin-fake-usage");
            return true;
        }
        String sellerName = args[0].trim();
        if (sellerName.isEmpty()) {
            messageService.send(sender, "error-admin-fake-name-empty");
            return true;
        }
        if (sellerName.length() > 16) {
            messageService.send(sender, "error-admin-fake-name-too-long");
            return true;
        }
        String auctionId = args[1];
        int price;
        try {
            price = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            messageService.send(sender, "error-admin-fake-usage");
            return true;
        }
        ItemStack source = admin.getInventory().getItemInMainHand();
        if (source == null || source.isEmpty()) {
            messageService.send(sender, "error-main-hand-empty");
            return true;
        }
        ItemStack escrow = source.clone();
        admin.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.AIR));
        var result = auctionService.createAdminFakeListing(sellerName, auctionId, price, escrow);
        if (!result.success()) {
            Map<Integer, ItemStack> leftover = admin.getInventory().addItem(escrow);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(stack -> admin.getWorld().dropItemNaturally(admin.getLocation(), stack));
            }
            messageService.send(admin, result.failure().messageKey());
            return true;
        }
        messageService.send(
                admin,
                "success-admin-fake",
                Map.of("seller", sellerName, "id", String.valueOf(result.listing().listingId()))
        );
        auctionService.audit(admin.getUniqueId(), admin.getName(), "ADMIN_FAKE", "seller=" + sellerName);
        return true;
    }

    private boolean adminGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error-only-player");
            return true;
        }
        if (!AdminGuiAccess.canOpenAdminGui(sender)) {
            messageService.send(sender, "error-admin-gui-denied");
            return true;
        }
        int page = 0;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]) - 1;
            } catch (NumberFormatException ignored) {
                messageService.send(sender, "error-admin-gui-usage");
                return true;
            }
        }
        return openAdminAuctionsGui(player, Math.max(0, page));
    }

    private boolean openAdminAuctionsGui(Player player, int page) {
        if (!auctionService.isLoaded()) {
            messageService.send(player, "error-still-loading");
            return true;
        }
        AdminAuctionsMenu menu = new AdminAuctionsMenu(
                player.getUniqueId(),
                page,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings()
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(menu.getInventory()));
        return true;
    }

    private boolean adminCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error-only-player");
            return true;
        }
        if (!AdminGuiAccess.canOpenAdminGui(sender)) {
            messageService.send(sender, "error-admin-gui-denied");
            return true;
        }
        if (args.length < 1) {
            messageService.send(sender, "error-admin-create-usage");
            return true;
        }
        String flag = args[0].toLowerCase(Locale.ROOT);
        if (flag.equals("cancel")) {
            if (adminAuctionCreateService.peek(player.getUniqueId()).isEmpty()) {
                return true;
            }
            int page = adminAuctionCreateService.cancelAndReturnGuiPage(player.getUniqueId());
            messageService.send(player, "admin-create-cancelled");
            return openAdminAuctionsGui(player, page);
        }
        if (flag.equals("later")) {
            var session = adminAuctionCreateService.peek(player.getUniqueId());
            if (session.isEmpty() || session.get().step() != Step.DISPLAY_NAME) {
                return true;
            }
            int page = session.get().adminGuiPage();
            adminAuctionCreateService.submitDisplayNameLater(player);
            return openAdminAuctionsGui(player, page);
        }
        messageService.send(sender, "error-admin-create-usage");
        return true;
    }

    private boolean migrate(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("from")) {
            messageService.send(sender, "error-admin-migrate-usage");
            return true;
        }
        StorageMode sourceMode;
        try {
            sourceMode = StorageMode.fromString(args[1]);
        } catch (Exception exception) {
            messageService.send(sender, "error-admin-migrate-usage");
            return true;
        }
        boolean dryRun = false;
        boolean archive = false;
        for (int i = 2; i < args.length; i++) {
            String flag = args[i].toLowerCase(Locale.ROOT);
            if (flag.equals("dry-run")) {
                dryRun = true;
            } else if (flag.equals("archive")) {
                archive = true;
            }
        }
        StorageMode source = sourceMode;
        boolean runDry = dryRun;
        boolean runArchive = archive;
        messageService.send(sender, "admin-migrate-started", Map.of("source", source.name()));
        PluginSchedulers.runAsync(plugin, () -> {
            try {
                AuctionStorageMigrator.Result result = auctionService.migrateFromStorage(plugin, source, runDry, runArchive);
                PluginSchedulers.runGlobal(plugin, () -> sendMigrateResult(sender, result));
            } catch (IllegalArgumentException exception) {
                String key = switch (exception.getMessage()) {
                    case "source-equals-target" -> "error-admin-migrate-same-mode";
                    case "source-empty" -> "error-admin-migrate-source-empty";
                    default -> "error-admin-migrate-failed";
                };
                PluginSchedulers.runGlobal(plugin, () -> messageService.send(sender, key));
            } catch (Exception exception) {
                PluginSchedulers.runGlobal(plugin, () -> messageService.send(
                        sender,
                        "error-admin-migrate-failed",
                        Map.of("reason", exception.getMessage() == null ? "unknown" : exception.getMessage())
                ));
            }
        });
        return true;
    }

    private void sendMigrateResult(CommandSender sender, AuctionStorageMigrator.Result result) {
        if (result.dryRun()) {
            messageService.send(sender, "success-admin-migrate-dry-run", Map.of(
                    "source", result.sourceMode().name(),
                    "target", result.targetMode().name(),
                    "import", String.valueOf(result.imported()),
                    "skip", String.valueOf(result.skipped()),
                    "total", String.valueOf(result.sourceTotal())
            ));
            return;
        }
        messageService.send(sender, "success-admin-migrate", Map.of(
                "source", result.sourceMode().name(),
                "target", result.targetMode().name(),
                "import", String.valueOf(result.imported()),
                "skip", String.valueOf(result.skipped()),
                "fail", String.valueOf(result.failed()),
                "total", String.valueOf(result.sourceTotal()),
                "archived", result.archived() ? "yes" : "no"
        ));
    }

    private boolean parse(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "error-only-player");
            return true;
        }
        if (args.length < 1) {
            messageService.send(sender, "error-admin-parse-usage");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.isEmpty()) {
            messageService.send(sender, "error-main-hand-empty");
            return true;
        }
        String mode = args[0].toLowerCase();
        List<String> lines = switch (mode) {
            case "tags" -> ItemInspectionFormatter.formatTags(item);
            case "nbt" -> ItemInspectionFormatter.formatNbt(item);
            default -> List.of();
        };
        if (lines.isEmpty()) {
            messageService.send(sender, "error-admin-parse-usage");
            return true;
        }
        messageService.send(sender, "admin-parse-header", Map.of("mode", mode));
        int limit = Math.min(lines.size(), 40);
        for (int i = 0; i < limit; i++) {
            messageService.send(sender, "admin-parse-line", Map.of("line", lines.get(i)));
        }
        if (lines.size() > limit) {
            messageService.send(sender, "admin-parse-truncated", Map.of("count", String.valueOf(lines.size() - limit)));
        }
        auctionService.audit(player.getUniqueId(), player.getName(), "ADMIN_PARSE_" + mode.toUpperCase(Locale.ROOT), ItemStackCodec.encode(item));
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args, String partial) {
        if (args.length == 0 || args.length == 1) {
            return CommandSuggestions.filter(partial, visibleSubcommands(sender));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("gui") || sub.equals("create")) {
            return tabGuiCreate(sub, args, partial);
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            return List.of();
        }
        return switch (sub) {
            case "fake" -> tabFake(args, partial);
            case "sellfor" -> tabSellFor(args, partial);
            case "selling" -> tabSelling(args, partial);
            case "history" -> tabHistory(args, partial);
            case "blacklist" -> tabBlacklist(args, partial);
            case "cache" -> tabCache(args, partial);
            case "parse" -> tabParse(args, partial);
            case "migrate" -> tabMigrate(args, partial);
            case "recover", "audit" -> List.of();
            default -> List.of();
        };
    }

    private List<String> visibleSubcommands(CommandSender sender) {
        List<String> subcommands = new ArrayList<>();
        if (AdminGuiAccess.canOpenAdminGui(sender)) {
            subcommands.add("gui");
            subcommands.add("create");
        }
        if (sender.hasPermission(PERMISSION_ADMIN)) {
            subcommands.add("history");
            subcommands.add("selling");
            subcommands.add("blacklist");
            subcommands.add("recover");
            subcommands.add("audit");
            subcommands.add("cache");
            subcommands.add("sellfor");
            subcommands.add("fake");
            subcommands.add("migrate");
            subcommands.add("parse");
        }
        return subcommands;
    }

    private List<String> tabFake(String[] args, String partial) {
        if (args.length == 2) {
            LinkedHashSet<String> suggestions = new LinkedHashSet<>(auctionService.knownFakeSellerNames());
            suggestions.addAll(CommandSuggestions.onlinePlayerNames());
            return CommandSuggestions.filter(partial, suggestions);
        }
        if (args.length == 3) {
            return CommandSuggestions.filter(partial, auctionIds());
        }
        return List.of();
    }

    private List<String> tabSellFor(String[] args, String partial) {
        if (args.length == 2) {
            return CommandSuggestions.filter(partial, CommandSuggestions.onlinePlayerNames());
        }
        if (args.length == 3) {
            return CommandSuggestions.filter(partial, auctionIds());
        }
        return List.of();
    }

    private List<String> tabSelling(String[] args, String partial) {
        if (args.length == 2) {
            return CommandSuggestions.filter(partial, CommandSuggestions.onlinePlayerNames());
        }
        if (args.length == 3) {
            return CommandSuggestions.filter(partial, auctionIds());
        }
        return List.of();
    }

    private List<String> tabHistory(String[] args, String partial) {
        if (args.length == 2) {
            return CommandSuggestions.filter(partial, CommandSuggestions.onlinePlayerNames());
        }
        if (args.length == 3) {
            return CommandSuggestions.filter(partial, List.of("15", "30", "50", "100"));
        }
        return List.of();
    }

    private List<String> tabBlacklist(String[] args, String partial) {
        if (args.length == 2) {
            return CommandSuggestions.filter(partial, List.of("add", "remove"));
        }
        if (args.length == 3) {
            return CommandSuggestions.filter(partial, CommandSuggestions.onlinePlayerNames());
        }
        return List.of();
    }

    private List<String> tabCache(String[] args, String partial) {
        if (args.length == 2) {
            return CommandSuggestions.filter(partial, List.of("stats", "rebuild", "invalidate"));
        }
        return List.of();
    }

    private List<String> tabParse(String[] args, String partial) {
        if (args.length == 2) {
            return CommandSuggestions.filter(partial, List.of("tags", "nbt"));
        }
        return List.of();
    }

    private List<String> tabMigrate(String[] args, String partial) {
        if (args.length == 2) {
            return CommandSuggestions.filter(partial, List.of("from"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("from")) {
            return CommandSuggestions.filter(partial, List.of("JSON", "YAML", "SQLITE", "MYSQL"));
        }
        if (args.length >= 3) {
            return CommandSuggestions.filter(partial, List.of("dry-run", "archive"));
        }
        return List.of();
    }

    private List<String> tabGuiCreate(String sub, String[] args, String partial) {
        if (!sub.equals("create") || args.length != 2) {
            return List.of();
        }
        return CommandSuggestions.filter(partial, List.of("cancel", "later"));
    }

    private List<String> auctionIds() {
        return auctionService.sortedAuctionDefinitions().stream()
                .map(definition -> definition.id)
                .toList();
    }

    private boolean audit(CommandSender sender, String[] args) {
        int limit = 20;
        if (args.length >= 1) {
            try {
                limit = Math.min(100, Math.max(1, Integer.parseInt(args[0])));
            } catch (NumberFormatException exception) {
                messageService.send(sender, "error-admin-audit-usage");
                return true;
            }
        }
        for (var entry : auctionService.recentAudit(limit)) {
            messageService.send(sender, 
                    "admin-audit-line",
                    Map.of(
                            "id", String.valueOf(entry.auditId()),
                            "actor", entry.actorName(),
                            "action", entry.action(),
                            "details", entry.details()
                    )
            );
        }
        return true;
    }
}
