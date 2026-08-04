package bm.b0b0b0.soulAuction.bootstrap;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

public final class SoulAuctionStartupLog {

    public static final String PREFIX = "\u001B[37m[\u001B[90mSoulAuction\u001B[37m]\u001B[0m ";

    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String GRAY = "\u001B[90m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    private static final ServerPlatformProbe.Platform PLATFORM = ServerPlatformProbe.detect();

    private static final String DIVIDER = PREFIX + "==============================";

    private final ConsoleCommandSender console;
    private final List<String> pending = new ArrayList<>();
    private boolean released;

    public SoulAuctionStartupLog() {
        this.console = Bukkit.getConsoleSender();
    }

    public void bannerStart(String version) {
        pending.add(" ");
        pending.add(DIVIDER);
        pending.add(PREFIX + "Version:" + GRAY + " " + version + " " + RESET + "| Author:" + GRAY + " b0b0b0" + RESET);
        pending.add(PREFIX + CYAN + PLATFORM.bannerDetail() + RESET);
        pending.add(PREFIX + " ");
        pending.add(PREFIX + " Startup:");
    }

    public void release() {
        if (released) {
            return;
        }
        pending.add(DIVIDER);
        pending.add(" ");
        released = true;
        for (String line : pending) {
            console.sendMessage(line);
        }
        pending.clear();
    }

    public void beginFinishSection() {
        console.sendMessage(" ");
        console.sendMessage(DIVIDER);
        console.sendMessage(PREFIX + " Finish:");
    }

    public void finishFailure(String reason) {
        console.sendMessage(PREFIX + RED + reason + RESET);
        console.sendMessage(DIVIDER);
        console.sendMessage(" ");
    }

    public void releaseFailure(String reason) {
        release();
        console.sendMessage(PREFIX + RED + reason + RESET);
        console.sendMessage(DIVIDER);
        console.sendMessage(" ");
    }

    public void bannerSuccess() {
        if (PLATFORM.folia()) {
            emit(PREFIX + GREEN + "SoulAuction enabled successfully" + RESET
                    + GRAY + " · " + RESET + CYAN + "Folia OK" + RESET);
        } else {
            emit(PREFIX + GREEN + "SoulAuction enabled successfully" + RESET);
        }
        emit(DIVIDER);
        emit(" ");
    }

    public void info(String message) {
        emit(PREFIX + message);
    }

    public void stepOk(String message) {
        emit(PREFIX + GREEN + "\u2713 " + RESET + message);
    }

    public void stepFail(String message) {
        emit(PREFIX + RED + "\u274c " + RESET + message);
    }

    public void stepSkipped(String message) {
        emit(PREFIX + GRAY + "\u2014 " + message + RESET);
    }

    public void stepSchedulers() {
        stepOk("Schedulers — " + PLATFORM.schedulersLabel());
    }

    public void unload() {
        console.sendMessage(PREFIX + GRAY + "SoulAuction disabled" + RESET);
    }

    private void emit(String message) {
        if (!released) {
            pending.add(message);
            return;
        }
        console.sendMessage(message);
    }
}
