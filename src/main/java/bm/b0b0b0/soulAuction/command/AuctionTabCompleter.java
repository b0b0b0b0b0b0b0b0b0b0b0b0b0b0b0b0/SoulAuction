package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.gui.admin.AdminGuiAccess;
import bm.b0b0b0.soulAuction.region.RegionMarketPermissions;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AuctionTabCompleter implements TabCompleter {

    private static final String PERMISSION_AH = "soulauction.command.ah";
    private static final String PERMISSION_SELL = "soulauction.command.sell";
    private static final String PERMISSION_MY = "soulauction.command.my";
    private static final String PERMISSION_CLAIM = "soulauction.command.claim";
    private static final String PERMISSION_VIEW = "soulauction.command.view";
    private static final String PERMISSION_LIMIT = "soulauction.command.limit";
    private static final String PERMISSION_RELOAD = "soulauction.command.reload";
    private static final String PERMISSION_ADMIN = "soulauction.command.admin";

    private final AuctionService auctionService;
    private final AuctionAdminCommand adminCommand;
    private final Supplier<RegionMarketCommandHandler> regionMarketCommandHandler;
    private final Supplier<PluginConfig> configSupplier;

    public AuctionTabCompleter(
            AuctionService auctionService,
            AuctionAdminCommand adminCommand,
            Supplier<RegionMarketCommandHandler> regionMarketCommandHandler,
            Supplier<PluginConfig> configSupplier
    ) {
        this.auctionService = auctionService;
        this.adminCommand = adminCommand;
        this.regionMarketCommandHandler = regionMarketCommandHandler;
        this.configSupplier = configSupplier;
    }

    private RegionMarketCommandHandler activeRegionHandler() {
        return regionMarketCommandHandler == null ? null : regionMarketCommandHandler.get();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return complete(sender, args);
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (args == null) {
            args = new String[0];
        }
        String partial = args.length == 0 ? "" : args[args.length - 1];
        if (args.length <= 1) {
            return CommandSuggestions.filter(partial, rootSuggestions(sender));
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("reload")) {
            return List.of();
        }
        if (root.equals("limit")) {
            return completeLimit(sender, args, partial);
        }
        if (root.equals("purge")) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                return List.of();
            }
            return args.length == 2 ? CommandSuggestions.filter(partial, List.of("7", "14", "30", "90")) : List.of();
        }
        if (root.equals("admin")) {
            if (!sender.hasPermission(PERMISSION_ADMIN) && !AdminGuiAccess.canOpenAdminGui(sender)) {
                return List.of();
            }
            return adminCommand.tabComplete(sender, copyFrom(args, 1), partial);
        }
        if (RegionMarketRouting.isAhRegionsSubcommand(root, regionMarketSettings())) {
            RegionMarketCommandHandler handler = activeRegionHandler();
            if (handler == null) {
                return List.of();
            }
            return handler.tabComplete(sender, copyFrom(args, 1));
        }
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return completePlayer(player, args, partial, root);
    }

    private List<String> completePlayer(Player player, String[] args, String partial, String root) {
        return switch (root) {
            case "sell" -> completeSell(player, args, partial);
            case "my", "selling", "expired", "purchased", "history" -> {
                if (!player.hasPermission(permissionForRoot(root))) {
                    yield List.of();
                }
                yield args.length == 2 ? CommandSuggestions.filter(partial, auctionIds()) : List.of();
            }
            case "claim" -> {
                if (!player.hasPermission(PERMISSION_CLAIM)) {
                    yield List.of();
                }
                yield args.length == 2 ? CommandSuggestions.filter(partial, List.of("all")) : List.of();
            }
            case "cancel" -> List.of();
            case "search" -> args.length == 2
                    ? merge(
                            CommandSuggestions.filter(partial, List.of("cancel", "clear")),
                            CommandSuggestions.filter(partial, auctionIds())
                    )
                    : List.of();
            case "page" -> completePage(args, partial);
            case "view" -> {
                if (!player.hasPermission(PERMISSION_VIEW)) {
                    yield List.of();
                }
                yield completeView(args, partial);
            }
            default -> CommandSuggestions.filter(partial, auctionIds());
        };
    }

    private static String permissionForRoot(String root) {
        return switch (root) {
            case "sell" -> PERMISSION_SELL;
            case "my", "selling", "expired", "purchased", "history" -> PERMISSION_MY;
            default -> PERMISSION_AH;
        };
    }

    private List<String> completeSell(Player player, String[] args, String partial) {
        if (!player.hasPermission(PERMISSION_SELL)) {
            return List.of();
        }
        if (args.length == 3 || args.length == 4) {
            return CommandSuggestions.filter(partial, auctionIds());
        }
        return List.of();
    }

    private List<String> completePage(String[] args, String partial) {
        if (args.length == 2) {
            return List.of();
        }
        if (args.length == 3) {
            return CommandSuggestions.filter(partial, auctionIds());
        }
        return List.of();
    }

    private List<String> completeView(String[] args, String partial) {
        if (args.length == 2) {
            return CommandSuggestions.filter(partial, CommandSuggestions.onlinePlayerNames());
        }
        if (args.length == 3) {
            return CommandSuggestions.filter(partial, auctionIds());
        }
        return List.of();
    }

    private List<String> completeLimit(CommandSender sender, String[] args, String partial) {
        if (!sender.hasPermission(PERMISSION_LIMIT)) {
            return List.of();
        }
        if (args.length == 2) {
            if (sender instanceof Player) {
                return CommandSuggestions.filter(partial, List.of("all", "global"));
            }
            return CommandSuggestions.filter(partial, CommandSuggestions.onlinePlayerNames());
        }
        if (args.length == 3 && sender instanceof Player) {
            return CommandSuggestions.filter(partial, List.of("all", "global"));
        }
        if (args.length == 3 || args.length == 4) {
            List<String> scopes = new ArrayList<>(auctionIds());
            scopes.add("all");
            scopes.add("global");
            return CommandSuggestions.filter(partial, scopes);
        }
        return List.of();
    }

    private List<String> rootSuggestions(CommandSender sender) {
        List<String> suggestions = new ArrayList<>();
        if (sender instanceof Player player) {
            if (player.hasPermission(PERMISSION_AH)) {
                suggestions.addAll(auctionIds());
                if (player.hasPermission(PERMISSION_SELL)) {
                    suggestions.add("sell");
                }
                if (player.hasPermission(PERMISSION_MY)) {
                    suggestions.add("my");
                    suggestions.add("selling");
                    suggestions.add("expired");
                    suggestions.add("purchased");
                    suggestions.add("history");
                }
                if (player.hasPermission(PERMISSION_CLAIM)) {
                    suggestions.add("claim");
                }
                suggestions.add("cancel");
                suggestions.add("search");
                suggestions.add("page");
                if (player.hasPermission(PERMISSION_VIEW)) {
                    suggestions.add("view");
                }
                if (activeRegionHandler() != null && player.hasPermission(RegionMarketPermissions.COMMAND)) {
                    suggestions.addAll(RegionMarketRouting.ahSubcommandSuggestions(regionMarketSettings()));
                }
            }
        } else {
            suggestions.addAll(auctionIds());
        }
        if (sender.hasPermission(PERMISSION_RELOAD)) {
            suggestions.add("reload");
        }
        if (sender.hasPermission(PERMISSION_LIMIT)) {
            suggestions.add("limit");
        }
        if (sender.hasPermission(PERMISSION_ADMIN)) {
            suggestions.add("admin");
            suggestions.add("purge");
        } else if (AdminGuiAccess.canOpenAdminGui(sender)) {
            suggestions.add("admin");
        }
        return suggestions;
    }

    private List<String> auctionIds() {
        if (!auctionService.isLoaded()) {
            return List.of();
        }
        return auctionService.sortedAuctionDefinitions().stream()
                .map(definition -> definition.id)
                .toList();
    }

    private AuctionSettings.RegionMarketSettings regionMarketSettings() {
        PluginConfig config = configSupplier.get();
        if (config == null) {
            return new AuctionSettings().regionMarket;
        }
        return config.auctionSettings().regionMarket;
    }

    private static String[] copyFrom(String[] args, int from) {
        String[] copy = new String[args.length - from];
        System.arraycopy(args, from, copy, 0, copy.length);
        return copy;
    }

    private static List<String> merge(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first);
        for (String value : second) {
            if (!merged.contains(value)) {
                merged.add(value);
            }
        }
        merged.sort(String.CASE_INSENSITIVE_ORDER);
        return merged;
    }
}
