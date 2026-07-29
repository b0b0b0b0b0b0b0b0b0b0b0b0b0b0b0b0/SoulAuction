package bm.b0b0b0.soulAuction.service.economy;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.util.PlaceholderApiBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public final class AuctionCurrencyDisplay {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private AuctionCurrencyDisplay() {
    }

    public static String apply(int amount, AuctionDefinitionSettings definition, String nativeFormatted, Player viewer) {
        return MINI_MESSAGE.serialize(toComponent(amount, definition, nativeFormatted, viewer));
    }

    public static Component toComponent(int amount, AuctionDefinitionSettings definition, String nativeFormatted, Player viewer) {
        if (definition == null || definition.currencySymbol == null || definition.currencySymbol.isBlank()) {
            return Component.text(nativeFormatted);
        }
        Component amountComponent = Component.text(String.valueOf(amount));
        Component symbolComponent = parseSymbol(definition, viewer);
        if ("BEFORE".equalsIgnoreCase(definition.currencySymbolPosition)) {
            return symbolComponent.append(amountComponent);
        }
        return amountComponent.append(Component.space()).append(symbolComponent);
    }

    private static Component parseSymbol(AuctionDefinitionSettings definition, Player viewer) {
        String symbol = definition.currencySymbol.trim();
        if (definition.currencySymbolPlaceholderApi) {
            symbol = PlaceholderApiBridge.apply(viewer, symbol);
        }
        if (symbol.indexOf('<') >= 0) {
            return MINI_MESSAGE.deserialize(symbol);
        }
        return Component.text(symbol);
    }
}
