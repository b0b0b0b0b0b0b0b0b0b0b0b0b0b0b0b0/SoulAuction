package bm.b0b0b0.soulAuction.util.upd;

import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoulAuctionUpdateChecker {

    private static final String VERSION_URL = "https://b0b0b0.dev/pl/souls/soulauction.txt";
    private static final String RESOURCE_URL = "https://github.com/b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0/SoulAuction/releases";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private SoulAuctionUpdateChecker() {
    }

    public static void schedule(JavaPlugin plugin, String currentVersion) {
        PluginSchedulers.runAsyncLater(plugin, 60L, () -> check(currentVersion));
    }

    public static void check(String currentVersion) {
        try {
            String latestVersion = fetchLatestVersion();
            if (latestVersion == null) {
                SoulAuctionConsole.error("Update check failed: could not read latest version.");
                return;
            }
            if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                printOutdated(currentVersion, latestVersion);
                return;
            }
            SoulAuctionConsole.line(SoulAuctionConsole.green("\u2713 ")
                    + "Update check: running latest version "
                    + SoulAuctionConsole.green(currentVersion) + ".");
        } catch (Exception exception) {
            SoulAuctionConsole.error("Update check error: " + exception.getMessage());
        }
    }

    private static void printOutdated(String currentVersion, String latestVersion) {
        SoulAuctionConsole.blank();
        SoulAuctionConsole.line(SoulAuctionConsole.border());
        SoulAuctionConsole.warn("A new SoulAuction version is available!");
        SoulAuctionConsole.line("  Current: " + SoulAuctionConsole.gray(currentVersion));
        SoulAuctionConsole.line("  Latest: " + SoulAuctionConsole.green(latestVersion));
        SoulAuctionConsole.line("  Download: " + SoulAuctionConsole.gray(RESOURCE_URL));
        SoulAuctionConsole.line(SoulAuctionConsole.border());
        SoulAuctionConsole.blank();
    }

    private static String fetchLatestVersion() {
        try {
            URL url = URI.create(VERSION_URL).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line = reader.readLine();
                return line == null ? null : line.trim();
            }
        } catch (IOException exception) {
            SoulAuctionConsole.error("Connection error to " + VERSION_URL + ": " + exception.getMessage());
            return null;
        }
    }
}
