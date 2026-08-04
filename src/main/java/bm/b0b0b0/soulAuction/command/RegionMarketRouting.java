package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RegionMarketRouting {

    public record ParsedPlayerCommand(String label, String[] args) {
    }

    private static final Set<String> RESERVED_STANDALONE = Set.of(
            "ah", "ax", "auction", "soulauction"
    );

    private static final Set<String> SUBCOMMANDS = Set.of("sell", "cancel", "preview", "my", "clear");

    private RegionMarketRouting() {
    }

    public static boolean shouldInterceptWorldGuardRg(String label, String[] args) {
        if (label == null || !"rg".equalsIgnoreCase(label)) {
            return false;
        }
        if (args == null || args.length == 0) {
            return false;
        }
        return SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT));
    }

    public static boolean shouldCompleteWorldGuardRg(String label, String[] args) {
        if (label == null || !"rg".equalsIgnoreCase(label)) {
            return false;
        }
        if (args == null || args.length == 0) {
            return true;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (SUBCOMMANDS.contains(first)) {
            return true;
        }
        for (String subcommand : SUBCOMMANDS) {
            if (subcommand.startsWith(first)) {
                return true;
            }
        }
        return false;
    }

    public static ParsedPlayerCommand parsePlayerCommand(String message) {
        if (message == null || message.isEmpty() || message.charAt(0) != '/') {
            return null;
        }
        String raw = message.substring(1);
        if (raw.isEmpty()) {
            return null;
        }
        boolean trailingToken = raw.endsWith(" ") || raw.endsWith("\t");
        raw = raw.trim();
        if (raw.isEmpty()) {
            return null;
        }
        int spaceIndex = raw.indexOf(' ');
        String labelPart = spaceIndex >= 0 ? raw.substring(0, spaceIndex) : raw;
        int colonIndex = labelPart.indexOf(':');
        if (colonIndex >= 0) {
            labelPart = labelPart.substring(0, colonIndex);
        }
        String label = labelPart.toLowerCase(Locale.ROOT);
        if (label.isEmpty()) {
            return null;
        }
        if (spaceIndex < 0) {
            return new ParsedPlayerCommand(label, new String[0]);
        }
        String remainder = raw.substring(spaceIndex + 1);
        String[] args = remainder.isEmpty() ? new String[0] : remainder.split("\\s+");
        if (trailingToken) {
            args = Arrays.copyOf(args, args.length + 1);
            args[args.length - 1] = "";
        }
        return new ParsedPlayerCommand(label, args);
    }

    public static boolean isAhRegionsSubcommand(String arg, AuctionSettings.RegionMarketSettings settings) {
        if (arg == null || arg.isBlank()) {
            return false;
        }
        if ("regions".equalsIgnoreCase(arg)) {
            return true;
        }
        if (settings == null || settings.ahSubcommandAliases == null) {
            return false;
        }
        for (String alias : settings.ahSubcommandAliases) {
            if (alias != null && alias.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> ahSubcommandSuggestions(AuctionSettings.RegionMarketSettings settings) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        suggestions.add("regions");
        if (settings != null && settings.ahSubcommandAliases != null) {
            for (String alias : settings.ahSubcommandAliases) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                suggestions.add(alias.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(suggestions);
    }

    public static List<String> normalizedStandaloneCommands(
            AuctionSettings.RegionMarketSettings settings,
            boolean worldGuardPresent
    ) {
        if (settings == null || settings.standaloneCommands == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String command : settings.standaloneCommands) {
            String name = normalizeStandalone(command);
            if (name == null) {
                continue;
            }
            if (RESERVED_STANDALONE.contains(name)) {
                continue;
            }
            if (worldGuardPresent && "rg".equals(name)) {
                continue;
            }
            normalized.add(name);
        }
        return List.copyOf(normalized);
    }

    public static String normalizeStandalone(String command) {
        if (command == null) {
            return null;
        }
        String trimmed = command.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || !trimmed.matches("[a-z0-9_-]+")) {
            return null;
        }
        return trimmed;
    }

    public static List<String> skippedStandaloneWarnings(
            AuctionSettings.RegionMarketSettings settings,
            boolean worldGuardPresent
    ) {
        List<String> warnings = new ArrayList<>();
        if (settings == null || settings.standaloneCommands == null) {
            return warnings;
        }
        for (String command : settings.standaloneCommands) {
            String name = normalizeStandalone(command);
            if (name == null) {
                warnings.add("invalid standalone command entry: " + command);
                continue;
            }
            if (RESERVED_STANDALONE.contains(name)) {
                warnings.add("standalone command '" + name + "' is reserved — skipped");
                continue;
            }
            if (worldGuardPresent && "rg".equals(name)) {
                warnings.add("standalone /rg is owned by WorldGuard — /rg sell|cancel|my|clear are intercepted; use /regions or /ah rg to browse");
            }
        }
        return warnings;
    }
}
