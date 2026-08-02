package bm.b0b0b0.soulAuction.service.browse;

import bm.b0b0b0.soulAuction.util.SearchSimilarity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ListingSearchMatcher {

    private ListingSearchMatcher() {
    }

    public static boolean matches(String haystack, SearchQueryContext context) {
        if (context.isEmpty()) {
            return true;
        }
        Pattern regex = context.regex();
        if (regex != null) {
            return regex.matcher(haystack).find();
        }
        for (SearchQueryContext.SearchQueryAttempt attempt : context.attempts()) {
            if (matchesAttempt(haystack, context, attempt)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAttempt(
            String haystack,
            SearchQueryContext context,
            SearchQueryContext.SearchQueryAttempt attempt
    ) {
        String query = attempt.query();
        if (haystack.contains(query)) {
            return true;
        }
        String[] tokens = attempt.tokens();
        if (matchesTokensInWords(haystack, tokens, context.minTokenLength())) {
            return true;
        }
        if (!context.fuzzyEnabled()) {
            return false;
        }
        if (SearchSimilarity.meetsThreshold(query, haystack, context.similarityThreshold())) {
            return true;
        }
        String[] words = splitWords(haystack);
        if (words.length == 0) {
            return false;
        }
        if (tokens.length == 1) {
            return tokenMatches(haystack, words, tokens[0], context);
        }
        for (String token : tokens) {
            if (!tokenMatches(haystack, words, token, context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesTokensInWords(String haystack, String[] tokens, int minTokenLength) {
        if (tokens.length == 0) {
            return false;
        }
        String[] words = splitWords(haystack);
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (token.length() < minTokenLength) {
                if (!haystack.contains(token)) {
                    return false;
                }
                continue;
            }
            if (haystack.contains(token)) {
                continue;
            }
            boolean found = false;
            for (String word : words) {
                if (word.contains(token)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean tokenMatches(String haystack, String[] words, String token, SearchQueryContext context) {
        if (haystack.contains(token)) {
            return true;
        }
        if (token.length() < context.minTokenLength()) {
            return false;
        }
        double threshold = context.similarityThreshold();
        for (String word : words) {
            if (word.length() < context.minTokenLength()) {
                continue;
            }
            if (word.contains(token)) {
                return true;
            }
            if (SearchSimilarity.meetsThreshold(token, word, threshold)) {
                return true;
            }
        }
        for (int index = 0; index < words.length - 1; index++) {
            String joined = words[index] + words[index + 1];
            if (joined.length() < context.minTokenLength()) {
                continue;
            }
            if (SearchSimilarity.meetsThreshold(token, joined, threshold)) {
                return true;
            }
        }
        return false;
    }

    private static String[] splitWords(String haystack) {
        if (haystack.isEmpty()) {
            return new String[0];
        }
        List<String> words = new ArrayList<>(8);
        int start = -1;
        for (int index = 0; index <= haystack.length(); index++) {
            boolean separator = index == haystack.length() || haystack.charAt(index) == ' ';
            if (separator) {
                if (start >= 0) {
                    words.add(haystack.substring(start, index));
                    start = -1;
                }
                continue;
            }
            if (start < 0) {
                start = index;
            }
        }
        return words.toArray(String[]::new);
    }
}
