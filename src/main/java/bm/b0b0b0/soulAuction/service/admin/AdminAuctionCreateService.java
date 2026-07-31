package bm.b0b0b0.soulAuction.service.admin;

import bm.b0b0b0.soulAuction.config.AuctionDefinitionWriter;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

public final class AdminAuctionCreateService {

    private static final Pattern AUCTION_ID_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9_-]{0,31})?$");
    private static final int DISPLAY_NAME_MAX_LENGTH = 64;

    private final Supplier<PluginConfig> configSupplier;
    private final AuctionDefinitionWriter definitionWriter;
    private final Runnable reloadConfig;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public AdminAuctionCreateService(
            Supplier<PluginConfig> configSupplier,
            AuctionDefinitionWriter definitionWriter,
            Runnable reloadConfig,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.configSupplier = configSupplier;
        this.definitionWriter = definitionWriter;
        this.reloadConfig = reloadConfig;
        this.auctionService = auctionService;
        this.messageService = messageService;
    }

    public enum Step {
        ID,
        DISPLAY_NAME
    }

    public record Session(Step step, int adminGuiPage, String auctionId) {
    }

    public enum IdValidation {
        OK,
        EMPTY,
        INVALID_FORMAT,
        DUPLICATE,
        TOO_LONG
    }

    public Optional<Session> peek(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void cancel(UUID playerId) {
        sessions.remove(playerId);
    }

    public void begin(Player player, int adminGuiPage) {
        auctionService.cancelPendingChatSearch(player.getUniqueId());
        sessions.put(player.getUniqueId(), new Session(Step.ID, adminGuiPage, null));
        messageService.send(player, "admin-create-id-prompt");
    }

    public void submitId(Player player, String rawInput) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.step() != Step.ID) {
            return;
        }
        String normalized = normalizeId(rawInput);
        IdValidation validation = validateId(normalized);
        if (validation != IdValidation.OK) {
            sendIdError(player, validation);
            messageService.send(player, "admin-create-id-prompt");
            return;
        }
        sessions.put(player.getUniqueId(), new Session(Step.DISPLAY_NAME, session.adminGuiPage(), normalized));
        messageService.send(player, "admin-create-name-prompt", Map.of("id", normalized));
    }

    public void submitDisplayNameLater(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.step() != Step.DISPLAY_NAME) {
            return;
        }
        finishCreate(player, session, "");
    }

    public void submitDisplayName(Player player, String rawInput) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.step() != Step.DISPLAY_NAME) {
            return;
        }
        String name = rawInput == null ? "" : rawInput.trim();
        if (name.length() > DISPLAY_NAME_MAX_LENGTH) {
            messageService.send(player, "error-admin-create-name-too-long");
            messageService.send(player, "admin-create-name-prompt", Map.of("id", session.auctionId()));
            return;
        }
        finishCreate(player, session, name);
    }

    public int cancelAndReturnGuiPage(UUID playerId) {
        Session session = sessions.remove(playerId);
        return session == null ? 0 : session.adminGuiPage();
    }

    private void finishCreate(Player player, Session session, String displayName) {
        sessions.remove(player.getUniqueId());
        var settings = configSupplier.get().auctionSettings();
        try {
            definitionWriter.writeNewDefinition(settings, session.auctionId(), displayName);
        } catch (IOException exception) {
            messageService.send(player, "error-admin-create-failed");
            return;
        }
        reloadConfig.run();
        messageService.send(player,
                "success-admin-create-auction",
                Map.of("id", session.auctionId())
        );
        auctionService.audit(
                player.getUniqueId(),
                player.getName(),
                "ADMIN_CREATE_AUCTION",
                "id=" + session.auctionId()
        );
    }

    private IdValidation validateId(String id) {
        if (id == null || id.isEmpty()) {
            return IdValidation.EMPTY;
        }
        if (id.length() > 32) {
            return IdValidation.TOO_LONG;
        }
        if (!AUCTION_ID_PATTERN.matcher(id).matches()) {
            return IdValidation.INVALID_FORMAT;
        }
        if (auctionService.auctionExists(id)) {
            return IdValidation.DUPLICATE;
        }
        if (definitionWriter.definitionFileExists(configSupplier.get().auctionSettings(), id)) {
            return IdValidation.DUPLICATE;
        }
        return IdValidation.OK;
    }

    private static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private void sendIdError(Player player, IdValidation validation) {
        String key = switch (validation) {
            case EMPTY -> "error-admin-create-id-empty";
            case INVALID_FORMAT -> "error-admin-create-id-invalid";
            case DUPLICATE -> "error-admin-create-id-duplicate";
            case TOO_LONG -> "error-admin-create-id-too-long";
            case OK -> throw new IllegalStateException();
        };
        messageService.send(player, key);
    }
}
