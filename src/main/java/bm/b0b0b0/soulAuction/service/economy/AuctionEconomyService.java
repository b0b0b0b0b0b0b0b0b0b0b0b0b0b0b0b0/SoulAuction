package bm.b0b0b0.soulAuction.service.economy;

import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.service.CoinsEngineBridge;
import bm.b0b0b0.soulAuction.service.EconomyBridge;
import bm.b0b0b0.soulAuction.service.ExperienceEconomyBridge;
import bm.b0b0b0.soulAuction.service.PlayerPointsBridge;
import java.util.UUID;

public final class AuctionEconomyService {

    private final EconomyBridge vaultBridge;
    private final PlayerPointsBridge playerPointsBridge;
    private final ExperienceEconomyBridge experienceBridge;
    private final CoinsEngineBridge coinsEngineBridge;

    public AuctionEconomyService(
            EconomyBridge vaultBridge,
            PlayerPointsBridge playerPointsBridge,
            ExperienceEconomyBridge experienceBridge,
            CoinsEngineBridge coinsEngineBridge
    ) {
        this.vaultBridge = vaultBridge;
        this.playerPointsBridge = playerPointsBridge;
        this.experienceBridge = experienceBridge;
        this.coinsEngineBridge = coinsEngineBridge;
    }

    public boolean isAvailable(AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.available();
            case PLAYER_POINTS -> playerPointsBridge.available();
            case EXPERIENCE -> experienceBridge.available();
            case COINS_ENGINE -> coinsEngineBridge.available();
        };
    }

    public boolean has(UUID playerId, int amount, AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.has(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.has(playerId, amount);
            case EXPERIENCE -> experienceBridge.has(playerId, amount);
            case COINS_ENGINE -> coinsEngineBridge.has(playerId, amount);
        };
    }

    public boolean withdraw(UUID playerId, int amount, AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.withdraw(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.withdraw(playerId, amount);
            case EXPERIENCE -> experienceBridge.withdraw(playerId, amount);
            case COINS_ENGINE -> coinsEngineBridge.withdraw(playerId, amount);
        };
    }

    public boolean deposit(UUID playerId, int amount, AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.deposit(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.deposit(playerId, amount);
            case EXPERIENCE -> experienceBridge.deposit(playerId, amount);
            case COINS_ENGINE -> coinsEngineBridge.deposit(playerId, amount);
        };
    }

    public String format(int price, AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.format(price);
            case PLAYER_POINTS -> playerPointsBridge.format(price);
            case EXPERIENCE -> experienceBridge.format(price);
            case COINS_ENGINE -> coinsEngineBridge.format(price);
        };
    }

    public boolean hasVault() {
        return vaultBridge.available();
    }

    public boolean hasPlayerPoints() {
        return playerPointsBridge.available();
    }
}
