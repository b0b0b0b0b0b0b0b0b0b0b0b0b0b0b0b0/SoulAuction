package bm.b0b0b0.soulAuction.service.economy;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.service.CoinsEngineBridge;
import bm.b0b0b0.soulAuction.service.EconomyBridge;
import bm.b0b0b0.soulAuction.service.ExperienceEconomyBridge;
import bm.b0b0b0.soulAuction.service.PlayerPointsBridge;
import org.bukkit.entity.Player;
import java.util.UUID;

public final class AuctionEconomyService {

    private final EconomyBridge vaultBridge;
    private final PlayerPointsBridge playerPointsBridge;
    private final ExperienceEconomyBridge experienceBridge;
    private final CoinsEngineBridge coinsEngineBridge;
    private final ItemCurrencyService itemCurrencyService;

    public AuctionEconomyService(
            EconomyBridge vaultBridge,
            PlayerPointsBridge playerPointsBridge,
            ExperienceEconomyBridge experienceBridge,
            CoinsEngineBridge coinsEngineBridge,
            ItemCurrencyService itemCurrencyService
    ) {
        this.vaultBridge = vaultBridge;
        this.playerPointsBridge = playerPointsBridge;
        this.experienceBridge = experienceBridge;
        this.coinsEngineBridge = coinsEngineBridge;
        this.itemCurrencyService = itemCurrencyService;
    }

    public boolean isAvailable(AuctionEconomyType type, AuctionDefinitionSettings definition) {
        return switch (type) {
            case VAULT -> vaultBridge.available();
            case PLAYER_POINTS -> playerPointsBridge.available();
            case EXPERIENCE -> experienceBridge.available();
            case COINS_ENGINE -> coinsEngineBridge.available();
            case ITEM -> definition != null && itemCurrencyService.available(definition);
        };
    }

    public boolean has(UUID playerId, int amount, AuctionEconomyType type, AuctionDefinitionSettings definition) {
        return switch (type) {
            case VAULT -> vaultBridge.has(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.has(playerId, amount);
            case EXPERIENCE -> experienceBridge.has(playerId, amount);
            case COINS_ENGINE -> coinsEngineBridge.has(playerId, amount, coinsCurrency(definition));
            case ITEM -> itemCurrencyService.has(playerId, amount, definition);
        };
    }

    public boolean withdraw(UUID playerId, int amount, AuctionEconomyType type, AuctionDefinitionSettings definition) {
        return switch (type) {
            case VAULT -> vaultBridge.withdraw(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.withdraw(playerId, amount);
            case EXPERIENCE -> experienceBridge.withdraw(playerId, amount);
            case COINS_ENGINE -> coinsEngineBridge.withdraw(playerId, amount, coinsCurrency(definition));
            case ITEM -> itemCurrencyService.withdraw(playerId, amount, definition);
        };
    }

    public boolean deposit(UUID playerId, int amount, AuctionEconomyType type, AuctionDefinitionSettings definition) {
        return switch (type) {
            case VAULT -> vaultBridge.deposit(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.deposit(playerId, amount);
            case EXPERIENCE -> experienceBridge.deposit(playerId, amount);
            case COINS_ENGINE -> coinsEngineBridge.deposit(playerId, amount, coinsCurrency(definition));
            case ITEM -> itemCurrencyService.deposit(playerId, amount, definition);
        };
    }

    public String format(int price, AuctionEconomyType type, AuctionDefinitionSettings definition) {
        return format(price, type, definition, null);
    }

    public String format(int price, AuctionEconomyType type, AuctionDefinitionSettings definition, Player viewer) {
        String nativeFormatted = switch (type) {
            case VAULT -> vaultBridge.format(price);
            case PLAYER_POINTS -> playerPointsBridge.format(price);
            case EXPERIENCE -> experienceBridge.format(price);
            case COINS_ENGINE -> coinsEngineBridge.format(price, coinsCurrency(definition));
            case ITEM -> itemCurrencyService.format(price, definition);
        };
        return AuctionCurrencyDisplay.apply(price, definition, nativeFormatted, viewer);
    }

    private String coinsCurrency(AuctionDefinitionSettings definition) {
        if (definition != null && definition.coinsEngineCurrency != null && !definition.coinsEngineCurrency.isBlank()) {
            return definition.coinsEngineCurrency.trim();
        }
        return coinsEngineBridge.defaultCurrencyId();
    }

    public boolean hasVault() {
        return vaultBridge.available();
    }

    public boolean hasPlayerPoints() {
        return playerPointsBridge.available();
    }
}
