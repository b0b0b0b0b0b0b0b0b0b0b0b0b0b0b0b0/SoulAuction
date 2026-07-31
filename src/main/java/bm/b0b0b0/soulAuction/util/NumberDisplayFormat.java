package bm.b0b0b0.soulAuction.util;

import java.text.NumberFormat;
import java.util.Locale;

public final class NumberDisplayFormat {

    private NumberDisplayFormat() {
    }

    public static String grouped(long value, Locale locale) {
        return NumberFormat.getIntegerInstance(locale == null ? Locale.ROOT : locale).format(value);
    }
}
