package bm.b0b0b0.soulAuction.bootstrap;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;

public final class ServerPlatformProbe {

    public record Platform(String brand, boolean folia) {

        public String bannerDetail() {
            if (folia) {
                return brand + " · region threads · entity/global/async";
            }
            return brand;
        }

        public String schedulersLabel() {
            if (folia) {
                return "Folia region threads";
            }
            return brand;
        }
    }

    private ServerPlatformProbe() {
    }

    public static Platform detect() {
        boolean folia = classPresent("io.papermc.paper.threadedregions.RegionizedServer");
        String brand = normalizeBrand(resolveBrand());
        if (folia && ("Paper".equals(brand) || "Purpur".equals(brand))) {
            brand = "Folia";
        }
        return new Platform(brand, folia);
    }

    private static String resolveBrand() {
        String buildInfoBrand = readPaperBuildInfoBrand();
        if (buildInfoBrand != null && !buildInfoBrand.isBlank()) {
            return buildInfoBrand;
        }
        String serverName = Bukkit.getName();
        if (serverName != null && !serverName.isBlank() && !"CraftBukkit".equalsIgnoreCase(serverName)) {
            return serverName;
        }
        return inferFromServerClass();
    }

    private static String readPaperBuildInfoBrand() {
        try {
            Class<?> infoClass = Class.forName("io.papermc.paper.ServerBuildInfo");
            Method buildInfo = infoClass.getMethod("buildInfo");
            Object info = buildInfo.invoke(null);
            Method brandName = info.getClass().getMethod("brandName");
            Object brand = brandName.invoke(info);
            return brand == null ? null : brand.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String inferFromServerClass() {
        String className = Bukkit.getServer().getClass().getName().toLowerCase();
        if (className.contains("purpur")) {
            return "Purpur";
        }
        if (className.contains("pufferfish")) {
            return "Pufferfish";
        }
        if (className.contains("leaf")) {
            return "Leaf";
        }
        if (className.contains("paper")) {
            return "Paper";
        }
        if (className.contains("spigot")) {
            return "Spigot";
        }
        return "Bukkit";
    }

    private static String normalizeBrand(String brand) {
        String trimmed = brand.trim();
        if (trimmed.isEmpty()) {
            return "Bukkit";
        }
        if (trimmed.length() == 1) {
            return trimmed.toUpperCase();
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
