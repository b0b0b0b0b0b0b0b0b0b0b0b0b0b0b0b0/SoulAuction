package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.config.settings.MaterialRuleSettings;
import bm.b0b0b0.soulAuction.model.TaxMode;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

public final class TaxPolicyResolver {

    private static final String BYPASS_PERMISSION = "soulauction.tax.bypass";
    private static final String DISCOUNT_PREFIX = "soulauction.tax.discount.";

    public record TaxAmounts(int saleTax, int buyTax) {
        public int buyerCharge(int listingPrice) {
            return listingPrice + buyTax;
        }

        public int sellerPayout(int listingPrice) {
            return Math.max(0, listingPrice - saleTax);
        }
    }

    public TaxAmounts resolve(
            Player buyer,
            Player seller,
            AuctionDefinitionSettings definition,
            AuctionSettings globalSettings,
            int listingPrice,
            ItemStack soldItem
    ) {
        if (buyer != null && buyer.hasPermission(BYPASS_PERMISSION)) {
            return new TaxAmounts(0, 0);
        }
        if (seller != null && seller.hasPermission(BYPASS_PERMISSION)) {
            return new TaxAmounts(0, 0);
        }
        MaterialRuleSettings materialRule = MaterialRuleMatcher.match(
                soldItem,
                globalSettings.materialRules,
                definition.materialRules
        );
        double salePercent = pickPercent(definition.saleTaxPercent, materialRule == null ? -1 : materialRule.saleTaxPercent);
        double buyPercent = pickPercent(definition.buyTaxPercent, materialRule == null ? -1 : materialRule.buyTaxPercent);
        if (seller != null) {
            salePercent = applyDiscount(seller, salePercent);
        }
        TaxMode mode = TaxMode.fromString(definition.taxMode);
        int saleTax = computeTax(listingPrice, salePercent);
        int buyTax = computeTax(listingPrice, buyPercent);
        if (mode == TaxMode.VAT) {
            buyTax = computeTax(listingPrice + saleTax, buyPercent);
        }
        if (mode == TaxMode.CAPITALISM && listingPrice >= definition.capitalismThreshold) {
            saleTax += computeTax(listingPrice, definition.capitalismSurchargePercent);
        }
        return new TaxAmounts(saleTax, buyTax);
    }

    /** Backward-compatible entry when item stack is unavailable. */
    public TaxAmounts resolve(Player buyer, Player seller, AuctionDefinitionSettings definition, int listingPrice) {
        return resolve(buyer, seller, definition, new AuctionSettings(), listingPrice, null);
    }

    private double pickPercent(double base, double override) {
        if (override >= 0.0D) {
            return override;
        }
        return Math.max(0.0D, base);
    }

    private double applyDiscount(Player seller, double salePercent) {
        double bestDiscount = 0.0D;
        Set<PermissionAttachmentInfo> permissions = seller.getEffectivePermissions();
        for (PermissionAttachmentInfo info : permissions) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(DISCOUNT_PREFIX)) {
                continue;
            }
            String suffix = permission.substring(DISCOUNT_PREFIX.length());
            try {
                double discount = Double.parseDouble(suffix);
                if (discount > bestDiscount) {
                    bestDiscount = discount;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (bestDiscount <= 0.0D) {
            return salePercent;
        }
        double multiplier = Math.max(0.0D, 1.0D - (Math.min(100.0D, bestDiscount) / 100.0D));
        return salePercent * multiplier;
    }

    private int computeTax(int price, double taxPercent) {
        if (taxPercent <= 0.0D) {
            return 0;
        }
        double bounded = Math.min(95.0D, taxPercent);
        return (int) Math.floor(price * (bounded / 100.0D));
    }
}
