package bm.b0b0b0.soulAuction.service.browse;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.util.KeyboardLayoutSwitch;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record SearchQueryContext(
        SearchQueryAttempt[] attempts,
        Pattern regex,
        boolean fuzzyEnabled,
        int minTokenLength,
        double similarityThreshold
) {

    public record SearchQueryAttempt(String query, String[] tokens) {
    }

    public String query() {
        return attempts.length == 0 ? "" : attempts[0].query();
    }

    public String[] tokens() {
        return attempts.length == 0 ? new String[0] : attempts[0].tokens();
    }

    public boolean isEmpty() {
        return attempts.length == 0 || attempts[0].tokens().length == 0;
    }

    public static SearchQueryContext compile(String query, Pattern regex, AuctionSettings.FeatureSettings features) {
        if (query == null || query.isBlank()) {
            return new SearchQueryContext(new SearchQueryAttempt[0], regex, false, 1, 100.0);
        }
        boolean fuzzyEnabled = features != null && features.searchFuzzyEnabled && regex == null;
        boolean layoutFix = features != null && features.searchKeyboardLayoutFix && regex == null;
        int minTokenLength = features == null ? 3 : Math.max(1, features.searchFuzzyMinTokenLength);
        double threshold = features == null
                ? 87.0
                : Math.min(100.0, Math.max(1.0, features.searchFuzzyMinSimilarityPercent));
        List<SearchQueryAttempt> attempts = new ArrayList<>(3);
        addAttempt(attempts, query);
        if (layoutFix) {
            addAttempt(attempts, KeyboardLayoutSwitch.enToRu(query));
            addAttempt(attempts, KeyboardLayoutSwitch.ruToEn(query));
        }
        return new SearchQueryContext(
                attempts.toArray(SearchQueryAttempt[]::new),
                regex,
                fuzzyEnabled,
                minTokenLength,
                threshold
        );
    }

    private static void addAttempt(List<SearchQueryAttempt> attempts, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        for (SearchQueryAttempt attempt : attempts) {
            if (attempt.query().equals(candidate)) {
                return;
            }
        }
        attempts.add(new SearchQueryAttempt(candidate, splitTokens(candidate)));
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
