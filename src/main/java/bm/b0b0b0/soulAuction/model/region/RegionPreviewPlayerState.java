package bm.b0b0b0.soulAuction.model.region;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public record RegionPreviewPlayerState(
        Location returnLocation,
        GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        float flySpeed,
        float walkSpeed
) {

    public static RegionPreviewPlayerState capture(Player player) {
        Location location = player.getLocation();
        return new RegionPreviewPlayerState(
                new Location(location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()),
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getFlySpeed(),
                player.getWalkSpeed()
        );
    }
}
