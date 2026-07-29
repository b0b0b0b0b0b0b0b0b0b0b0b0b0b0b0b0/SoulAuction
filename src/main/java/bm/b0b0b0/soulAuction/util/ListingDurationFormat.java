package bm.b0b0b0.soulAuction.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ListingDurationFormat {

    private ListingDurationFormat() {
    }

    public static String formatSeconds(int totalSeconds, Locale locale) {
        Locale effective = locale == null ? Locale.ENGLISH : locale;
        boolean ru = "ru".equalsIgnoreCase(effective.getLanguage());
        if (totalSeconds <= 0) {
            return ru ? "без срока" : "no expiry";
        }
        int days = totalSeconds / 86400;
        int hours = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (days > 0) {
            if (hours > 0) {
                return ru ? days + " д " + hours + " ч" : days + "d " + hours + "h";
            }
            return ru ? days + " д" : days + "d";
        }
        if (hours > 0) {
            if (minutes > 0) {
                return ru ? hours + " ч " + minutes + " мин" : hours + "h " + minutes + "m";
            }
            return ru ? hours + " ч" : hours + "h";
        }
        if (minutes > 0) {
            return ru ? minutes + " мин" : minutes + "m";
        }
        return ru ? totalSeconds + " сек" : totalSeconds + "s";
    }

    public static String formatRemainingMillis(long millis, Locale locale) {
        if (millis <= 0L) {
            Locale effective = locale == null ? Locale.ENGLISH : locale;
            return "ru".equalsIgnoreCase(effective.getLanguage()) ? "скоро" : "soon";
        }
        long totalSeconds = millis / 1000L;
        return formatSeconds((int) Math.min(Integer.MAX_VALUE, totalSeconds), locale);
    }

    public static String formatExpiresAtEpochMillis(long epochMillis, Locale locale) {
        Locale effective = locale == null ? Locale.ENGLISH : locale;
        String pattern = "ru".equalsIgnoreCase(effective.getLanguage()) ? "dd.MM.yyyy HH:mm" : "yyyy-MM-dd HH:mm";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                .withLocale(effective)
                .withZone(ZoneId.systemDefault());
        return formatter.format(Instant.ofEpochMilli(epochMillis));
    }
}
