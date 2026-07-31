package bm.b0b0b0.soulAuction.bootstrap;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

public final class SoulAuctionStartupLog {

    public static final String PREFIX = "\u001B[37m[\u001B[90mSoulAuction\u001B[37m]\u001B[0m ";

    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GRAY = "\u001B[90m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    private static final boolean FOLIA = detectFolia();

    private final ConsoleCommandSender console;

    public SoulAuctionStartupLog() {
        this.console = Bukkit.getConsoleSender();
    }

    public void bannerStart(String version) {
        console.sendMessage(" ");
        console.sendMessage(PREFIX + "==============================");
        console.sendMessage(PREFIX + "Version:" + GRAY + " " + version + " " + RESET + "| Author:" + GRAY + " b0b0b0" + RESET);
        if (FOLIA) {
            console.sendMessage(PREFIX + CYAN + "Folia" + RESET + GRAY + " · region threads · entity/global/async" + RESET);
        } else {
            console.sendMessage(PREFIX + GRAY + "Paper · Folia-ready (region schedulers)" + RESET);
        }
        console.sendMessage(PREFIX + " ");
        console.sendMessage(PREFIX + " Startup:");
    }

    public void bannerSuccess() {
        if (FOLIA) {
            console.sendMessage(PREFIX + GREEN + "SoulAuction enabled successfully" + RESET
                    + GRAY + " · " + RESET + CYAN + "Folia OK" + RESET);
        } else {
            console.sendMessage(PREFIX + GREEN + "SoulAuction enabled successfully" + RESET);
        }
        console.sendMessage(PREFIX + "==============================");
        console.sendMessage(" ");
    }

    public void bannerFailure(String reason) {
        console.sendMessage(PREFIX + RED + reason + RESET);
        console.sendMessage(PREFIX + "==============================");
        console.sendMessage(" ");
    }

    public void info(String message) {
        console.sendMessage(PREFIX + message);
    }

    public void stepOk(String message) {
        console.sendMessage(PREFIX + GREEN + "\u2713 " + RESET + message);
    }

    public void stepFail(String message) {
        console.sendMessage(PREFIX + RED + "\u274c " + RESET + message);
    }

    public void stepSkipped(String message) {
        console.sendMessage(PREFIX + GRAY + "\u2014 " + message + RESET);
    }

    public void stepSchedulers() {
        if (FOLIA) {
            stepOk("Schedulers — Folia region threads");
            return;
        }
        stepOk("Schedulers — Paper Folia-ready API");
    }

    public void unload() {
        console.sendMessage(PREFIX + GRAY + "SoulAuction disabled" + RESET);
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
