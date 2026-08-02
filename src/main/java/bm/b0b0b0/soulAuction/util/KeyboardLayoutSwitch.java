package bm.b0b0b0.soulAuction.util;

public final class KeyboardLayoutSwitch {

    private static final String EN = "`qwertyuiop[]asdfghjkl;'zxcvbnm,./";
    private static final String RU = "ёйцукенгшщзхъфывапролджэячсмитьбю.";

    private KeyboardLayoutSwitch() {
    }

    public static String enToRu(String input) {
        return convert(input, EN, RU);
    }

    public static String ruToEn(String input) {
        return convert(input, RU, EN);
    }

    private static String convert(String input, String from, String to) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        StringBuilder result = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            int fromIndex = from.indexOf(character);
            if (fromIndex < 0) {
                result.append(character);
                continue;
            }
            result.append(to.charAt(fromIndex));
        }
        return result.toString();
    }
}
