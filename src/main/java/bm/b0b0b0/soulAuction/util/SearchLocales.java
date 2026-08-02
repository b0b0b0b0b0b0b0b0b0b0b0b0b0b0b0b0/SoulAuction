package bm.b0b0b0.soulAuction.util;

import java.util.Locale;

public final class SearchLocales {

    private static final Locale[] DEFAULT = {
            Locale.of("ru", "RU"),
            Locale.ENGLISH
    };

    private SearchLocales() {
    }

    public static Locale[] defaults() {
        return DEFAULT;
    }

    public static Locale normalize(Locale locale) {
        if (locale == null || locale.getLanguage().isBlank()) {
            return Locale.ENGLISH;
        }
        return locale;
    }

    public static Locale parseTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return Locale.ENGLISH;
        }
        String tag = raw.trim().replace('_', '-');
        Locale parsed = Locale.forLanguageTag(tag);
        if (parsed.getLanguage().isBlank()) {
            return Locale.ENGLISH;
        }
        if (parsed.getCountry().isBlank()) {
            return defaultRegionForLanguage(parsed.getLanguage());
        }
        return parsed;
    }

    private static Locale defaultRegionForLanguage(String language) {
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "ru" -> Locale.of("ru", "RU");
            case "en" -> Locale.of("en", "US");
            case "uk" -> Locale.of("uk", "UA");
            case "de" -> Locale.of("de", "DE");
            case "pl" -> Locale.of("pl", "PL");
            default -> Locale.of(language, language.toUpperCase(Locale.ROOT));
        };
    }
}
