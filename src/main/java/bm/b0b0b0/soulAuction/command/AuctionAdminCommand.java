package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.gui.PlayerRecordsMenu;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.ClaimEntry;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.model.PlayerHistoryView;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionAdminCommand {

    private static final String PERMISSION_ADMIN = "soulauction.command.admin";

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final AuctionService auctionService;

    public AuctionAdminCommand(JavaPlugin plugin, MessageService messageService, AuctionService auctionService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.auctionService = auctionService;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(messageService.component("error-no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messageService.component("error-admin-usage"));
            return true;
        }
        String sub = args[0].toLowerCase();
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        return switch (sub) {
            case "history" -> history(sender, subArgs);
            case "selling" -> selling(sender, subArgs);
            case "blacklist" -> blacklist(sender, subArgs);
            case "recover" -> recover(sender, subArgs);
            case "audit" -> audit(sender, subArgs);
            default -> {
                sender.sendMessage(messageService.component("error-admin-usage"));
                yield true;
            }
        };
    }

    public boolean purge(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(messageService.component("error-no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messageService.component("error-purge-usage"));
            return true;
        }
        int days;
        try {
            days = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(messageService.component("error-purge-usage"));
            return true;
        }
        int removed = auctionService.purgeHistoryOlderThanDays(days);
        sender.sendMessage(messageService.component("success-purge", Map.of("removed", String.valueOf(removed), "days", String.valueOf(days))));
        if (sender instanceof Player player) {
            auctionService.audit(player.getUniqueId(), player.getName(), "PURGE_HISTORY", "days=" + days + ",removed=" + removed);
        }
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messageService.component("error-admin-history-usage"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        int limit = 15;
        if (args.length >= 2) {
            try {
                limit = Math.min(100, Math.max(1, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage(messageService.component("error-admin-history-usage"));
                return true;
            }
        }
        List<DealHistoryEntry> entries = auctionService.adminHistoryForPlayer(target.getUniqueId(), limit);
        sender.sendMessage(messageService.component(
                "admin-history-header",
                Map.of("player", target.getName() == null ? target.getUniqueId().toString() : target.getName(), "count", String.valueOf(entries.size()))
        ));
        for (DealHistoryEntry entry : entries) {
            sender.sendMessage(messageService.component(
                    "admin-history-line",
                    Map.of(
                            "action", entry.action(),
                            "id", String.valueOf(entry.listingId()),
                            "price", auctionService.formatPrice(entry.price(), entry.economyType()),
                            "auction", entry.auctionId()
                    )
            ));
        }
        return true;
    }

    private boolean selling(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(messageService.component("error-only-player"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messageService.component("error-admin-selling-usage"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String auctionId = args.length > 1 ? args[1] : auctionService.defaultAuctionId();
        if (!auctionService.auctionExists(auctionId)) {
            admin.sendMessage(messageService.component("error-auction-not-found"));
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
            sender.sendMessage(messageService.component("error-admin-blacklist-usage"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        String actorName = sender.getName();
        String action = args[0].toLowerCase();
        if (action.equals("add")) {
            auctionService.adminBlacklistAdd(target.getUniqueId(), actorId, actorName);
            sender.sendMessage(messageService.component("success-blacklist-add", Map.of("player", target.getName() == null ? target.getUniqueId().toString() : target.getName())));
        } else if (action.equals("remove")) {
            auctionService.adminBlacklistRemove(target.getUniqueId(), actorId, actorName);
            sender.sendMessage(messageService.component("success-blacklist-remove", Map.of("player", target.getName() == null ? target.getUniqueId().toString() : target.getName())));
        } else {
            sender.sendMessage(messageService.component("error-admin-blacklist-usage"));
        }
        return true;
    }

    private boolean recover(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(messageService.component("error-only-player"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messageService.component("error-admin-recover-usage"));
            return true;
        }
        long claimId;
        try {
            claimId = Long.parseLong(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(messageService.component("error-admin-recover-usage"));
            return true;
        }
        ClaimEntry claim = auctionService.adminRecoverClaim(claimId);
        if (claim == null) {
            admin.sendMessage(messageService.component("error-claim-not-found"));
            return true;
        }
        ItemStack item = ItemStackCodec.decode(claim.itemBase64());
        var leftovers = admin.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            admin.sendMessage(messageService.component("error-inventory-full"));
            auctionService.restoreClaimEntry(claim);
            return true;
        }
        auctionService.audit(admin.getUniqueId(), admin.getName(), "ADMIN_RECOVER", "claimId=" + claimId);
        admin.sendMessage(messageService.component("success-admin-recover", Map.of("id", String.valueOf(claimId))));
        return true;
    }

    private boolean audit(CommandSender sender, String[] args) {
        int limit = 20;
        if (args.length >= 1) {
            try {
                limit = Math.min(100, Math.max(1, Integer.parseInt(args[0])));
            } catch (NumberFormatException exception) {
                sender.sendMessage(messageService.component("error-admin-audit-usage"));
                return true;
            }
        }
        for (var entry : auctionService.recentAudit(limit)) {
            sender.sendMessage(messageService.component(
                    "admin-audit-line",
                    Map.of(
                            "id", String.valueOf(entry.auditId()),
                            "actor", entry.actorName(),
                            "action", entry.action(),
                            "details", entry.details()
                    )
            ));
        }
        return true;
    }
}
