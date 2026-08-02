package bm.b0b0b0.soulAuction.util;

public final class SearchSimilarity {

    private SearchSimilarity() {
    }

    public static boolean meetsThreshold(String first, String second, double thresholdPercent) {
        if (second == null) {
            return false;
        }
        if (first.equals(second)) {
            return true;
        }
        int firstLength = first.length();
        int secondLength = second.length();
        int maxLength = Math.max(firstLength, secondLength);
        if (maxLength == 0) {
            return true;
        }
        int maxDistance = maxAllowedDistance(maxLength, thresholdPercent);
        if (Math.abs(firstLength - secondLength) > maxDistance) {
            return false;
        }
        return levenshteinDistance(first, second, maxDistance) <= maxDistance;
    }

    public static int maxAllowedDistance(int maxLength, double thresholdPercent) {
        double clamped = Math.min(100.0, Math.max(1.0, thresholdPercent));
        return (int) Math.floor(maxLength * (1.0 - clamped / 100.0));
    }

    private static int levenshteinDistance(String source, String target, int maxDistance) {
        int sourceLength = source.length();
        int targetLength = target.length();
        if (sourceLength == 0) {
            return targetLength <= maxDistance ? targetLength : maxDistance + 1;
        }
        if (targetLength == 0) {
            return sourceLength <= maxDistance ? sourceLength : maxDistance + 1;
        }
        int[] previous = new int[targetLength + 1];
        int[] current = new int[targetLength + 1];

        for (int column = 0; column <= targetLength; column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= sourceLength; row++) {
            current[0] = row;
            int rowMinimum = current[0];
            char sourceChar = source.charAt(row - 1);
            for (int column = 1; column <= targetLength; column++) {
                int cost = sourceChar == target.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost
                );
                rowMinimum = Math.min(rowMinimum, current[column]);
            }
            if (rowMinimum > maxDistance) {
                return maxDistance + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[targetLength];
    }
}
