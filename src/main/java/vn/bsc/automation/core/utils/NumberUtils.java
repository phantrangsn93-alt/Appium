package vn.bsc.automation.core.utils;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Parses numbers displayed by the app: "1,740.73", "909,600", "-0.09", "+19.95%". */
public final class NumberUtils {

    /** Price / index value e.g. 7.08, 1,740.73 */
    public static final Pattern PRICE = Pattern.compile("^\\d{1,3}(,\\d{3})*(\\.\\d{1,2})?$");
    /** Integer with thousand separators e.g. 909,600 */
    public static final Pattern VOLUME = Pattern.compile("^\\d{1,3}(,\\d{3})*$");
    /** Change e.g. +7.71 (+0.44%) / -1.27 (-0.49%) / 0.00 (0.00%) */
    public static final Pattern CHANGE_WITH_PERCENT = Pattern.compile("^[+-]?\\d+(\\.\\d+)? \\([+-]?\\d+(\\.\\d+)?%\\)$");

    private NumberUtils() {
    }

    public static BigDecimal parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Cannot parse null number");
        }
        String clean = text.replace(",", "").replace("%", "").replace("+", "").trim();
        return new BigDecimal(clean);
    }

    public static long parseLong(String text) {
        return parse(text).longValueExact();
    }
}
