package bm.b0b0b0.soulAuction.service.browse;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record SearchQueryContext(
        String query,
        String[] tokens,
        Pattern regex,
        boolean fuzzyEnabled,
        int minTokenLength,
        double similarityThreshold
) {

    public static SearchQueryContext compile(String query, Pattern regex, AuctionSettings.FeatureSettings features) {
        if (query == null || query.isBlank()) {
            return new SearchQueryContext("", new String[0], regex, false, 1, 100.0);
        }
        boolean fuzzyEnabled = features != null && features.searchFuzzyEnabled && regex == null;
        int minTokenLength = features == null ? 3 : Math.max(1, features.searchFuzzyMinTokenLength);
        double threshold = features == null
                ? 87.0
                : Math.min(100.0, Math.max(1.0, features.searchFuzzyMinSimilarityPercent));
        return new SearchQueryContext(query, splitTokens(query), regex, fuzzyEnabled, minTokenLength, threshold);
    }

    public boolean isEmpty() {
        return tokens.length == 0;
    }

    private static String[] splitTokens(String query) {
        List<String> tokens = new ArrayList<>(4);
        int start = -1;
        for (int index = 0; index <= query.length(); index++) {
            boolean separator = index == query.length() || query.charAt(index) == ' ';
            if (separator) {
                if (start >= 0) {
                    tokens.add(query.substring(start, index));
                    start = -1;
                }
                continue;
            }
            if (start < 0) {
                start = index;
            }
        }
        return tokens.toArray(String[]::new);
    }
}
