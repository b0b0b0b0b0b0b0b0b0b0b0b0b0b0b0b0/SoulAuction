package bm.b0b0b0.soulAuction.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ListingDurationFormat {

    private static final DateTimeFormatter EXPIRES_AT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withLocale(Locale.forLanguageTag("ru"))
            .withZone(ZoneId.systemDefault());

    private ListingDurationFormat() {
    }

    public static String formatSeconds(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "без срока";
        }
        int days = totalSeconds / 86400;
        int hours = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (days > 0) {
            if (hours > 0) {
                return days + " д " + hours + " ч";
            }
            return days + " д";
        }
        if (hours > 0) {
            if (minutes > 0) {
                return hours + " ч " + minutes + " мин";
            }
            return hours + " ч";
        }
        if (minutes > 0) {
            return minutes + " мин";
        }
        return totalSeconds + " сек";
    }

    public static String formatRemainingMillis(long millis) {
        if (millis <= 0L) {
            return "скоро";
        }
        long totalSeconds = millis / 1000L;
        return formatSeconds((int) Math.min(Integer.MAX_VALUE, totalSeconds));
    }

    public static String formatExpiresAtEpochMillis(long epochMillis) {
        return EXPIRES_AT.format(Instant.ofEpochMilli(epochMillis));
    }
}
