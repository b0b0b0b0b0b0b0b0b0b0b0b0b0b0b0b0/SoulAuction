package bm.b0b0b0.soulAuction.util;

import bm.b0b0b0.soulAuction.service.SkinTexture;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

public final class PlayerSkullTextures {

    private PlayerSkullTextures() {
    }

    public static void apply(SkullMeta skullMeta, java.util.UUID profileId, String profileName, SkinTexture texture) {
        if (texture == null || texture.value() == null || texture.value().isBlank()) {
            return;
        }
        PlayerProfile profile = Bukkit.createProfile(profileId, profileName);
        profile.setProperty(new ProfileProperty("textures", texture.value(), texture.signature()));
        skullMeta.setPlayerProfile(profile);
    }
}
