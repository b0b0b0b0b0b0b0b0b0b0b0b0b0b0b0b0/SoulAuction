package bm.b0b0b0.soulAuction.util.upd;

import bm.b0b0b0.soulAuction.bootstrap.SoulAuctionStartupLog;
import org.bukkit.Bukkit;

final class SoulAuctionConsole {

    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GRAY = "\u001B[90m";
    private static final String RESET = "\u001B[0m";

    private SoulAuctionConsole() {
    }

    static void blank() {
        Bukkit.getConsoleSender().sendMessage(" ");
    }

    static void line(String message) {
        Bukkit.getConsoleSender().sendMessage(SoulAuctionStartupLog.PREFIX + message);
    }

    static void warn(String message) {
        Bukkit.getConsoleSender().sendMessage(SoulAuctionStartupLog.PREFIX + YELLOW + message + RESET);
    }

    static void error(String message) {
        Bukkit.getConsoleSender().sendMessage(SoulAuctionStartupLog.PREFIX + RED + message + RESET);
    }

    static String gray(String message) {
        return GRAY + message + RESET;
    }

    static String green(String message) {
        return GREEN + message + RESET;
    }

    static String border() {
        return GRAY + "==============================================" + RESET;
    }
}
