package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class GuiGeneralSettings extends YamlSerializable {

    @Comment({
            @CommentValue("Main auction browser inventory size (rows × 9). Must be a multiple of 9."),
            @CommentValue("54 = six rows (slots 0–53). Slot index 0 is top-left, +1 goes right, +9 next row."),
            @CommentValue("Default layout: top row controls + border; listings 9–35; bottom controls 45–53."),
    })
    public int size = 54;

    @Comment({
            @CommentValue("Decorative border slots (glass panes). Not used for listings or controls."),
            @CommentValue("Default: top row except refresh-slot (4), and row above controls 36–44."),
    })
    public List<Integer> borderSlots = List.of(
            0, 1, 2, 3, 5, 6, 7, 8,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    );

    @Comment({
            @CommentValue("Chest slots where active listings are drawn (page size = this list length)."),
            @CommentValue("Do not reuse border or control slots — keep them out of this list."),
    })
    public List<Integer> listingSlots = List.of(
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    );

    @NewLine
    @Comment({
            @CommentValue("Default filler item for border slots and empty prev/next slots (gui/general.yml)."),
            @CommentValue("Override per auction in auctions/<id>.yml → gui-filler-material."),
    })
    public String fillerMaterial = "BLACK_STAINED_GLASS_PANE";

    @Comment({
            @CommentValue("Custom Model Data on filler panes (-1 = do not set)."),
    })
    public int fillerCustomModelData = -1;

    @NewLine
    @Comment({
            @CommentValue("Bottom-bar control buttons in the main /ah browser (slot index in this inventory)."),
            @CommentValue("Each slot must be unique and between 0 and size − 1."),
            @CommentValue(""),
            @CommentValue("previous-page-slot — older listings page"),
            @CommentValue("next-page-slot     — newer listings page"),
            @CommentValue("history-slot       — your buy/sell history menu"),
            @CommentValue("category-slot      — filter by item category"),
            @CommentValue("search-slot        — search by name (chat prompt)"),
            @CommentValue("refresh-slot       — reload listings + live stats (default: top row, slot 4)"),
            @CommentValue("favorites-slot     — favorite sellers menu"),
            @CommentValue("sort-slot          — change sort order (price, date, …)"),
            @CommentValue("price-filter-slot  — min/max price filter menu"),
    })
    public int previousPageSlot = 45;
    public int historySlot = 46;
    public int categorySlot = 47;
    public int searchSlot = 48;
    public int refreshSlot = 4;
    public int favoritesSlot = 50;
    public int sortSlot = 51;
    public int priceFilterSlot = 52;
    public int nextPageSlot = 53;

    @NewLine
    @Comment({
            @CommentValue("Vanilla item type for each control (Bukkit Material name)."),
            @CommentValue("Button labels and lore come from lang/messages_*.yml — not from this file."),
            @CommentValue(""),
            @CommentValue("previous-page-material — icon for previous page (default light gray dye = «back»)"),
            @CommentValue("next-page-material     — icon for next page (default gray dye = «forward»)"),
            @CommentValue("history-material       — purchase / sale history"),
            @CommentValue("category-material      — category picker"),
            @CommentValue("search-material        — search"),
            @CommentValue("refresh-material       — refresh list"),
            @CommentValue("favorites-material     — favorite sellers"),
            @CommentValue("sort-material          — sorting"),
            @CommentValue("price-filter-material  — price filter"),
            @CommentValue("back-button-material   — «Back» in sub-menus (category, sort, favorites, …)"),
            @CommentValue(""),
            @CommentValue("ItemsAdder / Oraxen: use a vanilla material here and set custom-model-data below."),
    })
    public String previousPageMaterial = "LIGHT_GRAY_DYE";
    public String historyMaterial = "BOOK";
    public String categoryMaterial = "CHEST";
    public String searchMaterial = "OAK_SIGN";
    public String refreshMaterial = "CLOCK";
    public String favoritesMaterial = "NETHER_STAR";
    public String sortMaterial = "COMPASS";
    public String priceFilterMaterial = "GOLD_NUGGET";
    public String nextPageMaterial = "GRAY_DYE";

    public String backButtonMaterial = "LIGHT_GRAY_DYE";

    @NewLine
    @Comment({
            @CommentValue("Custom Model Data on control items (1.14+ resource packs, ItemsAdder, Oraxen, Nexo, …)."),
            @CommentValue("-1 = do not set CMD (plain vanilla material texture)."),
            @CommentValue("Must match the CMD on your resource pack / custom item for that button."),
            @CommentValue("Pairs with *-material above (same order as slot list). back-button has no CMD field."),
            @CommentValue(""),
            @CommentValue("previous-page-custom-model-data"),
            @CommentValue("next-page-custom-model-data"),
            @CommentValue("history-custom-model-data"),
            @CommentValue("category-custom-model-data"),
            @CommentValue("sort-custom-model-data"),
            @CommentValue("refresh-custom-model-data"),
            @CommentValue("search-custom-model-data"),
            @CommentValue("favorites-custom-model-data"),
            @CommentValue("price-filter-custom-model-data"),
    })
    public int previousPageCustomModelData = -1;
    public int nextPageCustomModelData = -1;
    public int historyCustomModelData = -1;
    public int categoryCustomModelData = -1;
    public int sortCustomModelData = -1;
    public int refreshCustomModelData = -1;
    public int searchCustomModelData = -1;
    public int favoritesCustomModelData = -1;
    public int priceFilterCustomModelData = -1;

    public GuiGeneralSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
