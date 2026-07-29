package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class GuiGeneralSettings extends YamlSerializable {

    @Comment({@CommentValue("Inventory size, multiple of 9")})
    public int size = 54;

    @Comment({@CommentValue("Slots used for listing items")})
    public List<Integer> listingSlots = List.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    );

    @NewLine
    @Comment({@CommentValue("Control button slots")})
    public int previousPageSlot = 45;
    public int historySlot = 46;
    public int categorySlot = 47;
    public int searchSlot = 48;
    public int refreshSlot = 49;
    public int favoritesSlot = 50;
    public int sortSlot = 51;
    public int priceFilterSlot = 52;
    public int nextPageSlot = 53;

    @NewLine
    @Comment({@CommentValue("Control button materials, Bukkit Material names")})
    @Comment({@CommentValue("Pagination: prev page = LIGHT_GRAY_DYE (back), next page = GRAY_DYE (forward)")})
    public String previousPageMaterial = "LIGHT_GRAY_DYE";
    public String historyMaterial = "BOOK";
    public String categoryMaterial = "CHEST";
    public String searchMaterial = "OAK_SIGN";
    public String refreshMaterial = "SUNFLOWER";
    public String favoritesMaterial = "NETHER_STAR";
    public String sortMaterial = "COMPASS";
    public String priceFilterMaterial = "GOLD_NUGGET";
    public String nextPageMaterial = "GRAY_DYE";

    @Comment({@CommentValue("Sub-menu «Back» — light gray dye only")})
    public String backButtonMaterial = "LIGHT_GRAY_DYE";

    @NewLine
    @Comment({@CommentValue("Custom model data for control buttons, -1 disables")})
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
