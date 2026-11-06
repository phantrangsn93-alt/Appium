package vn.bsc.automation.data;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Price written in test data (x1000): a base + optional delta, or a plain number.
 * <pre>
 *   REF | CEIL | FLOOR   reference / ceiling / floor price of the symbol
 *   HINT                 suggested price the app shows next to the field ("&gt; 7.53", "≤ 7.53")
 *   &lt;base&gt;+0.5 / &lt;base&gt;-0.5   base plus / minus a delta, e.g. HINT+0.5 (Excel: "Giá gợi ý + 0.5")
 *   7.08                 a fixed price
 *   (empty)              keep what the app filled in
 * </pre>
 */
public record PriceSpec(Base base, BigDecimal delta) {

    public enum Base { REF, CEIL, FLOOR, HINT, FIXED }

    private static final Pattern P = Pattern.compile("^(REF|CEIL|FLOOR|HINT)\\s*(?:([+-])\\s*(\\d+(?:\\.\\d+)?))?$");

    /** Null for an empty spec. */
    public static PriceSpec parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String s = spec.trim().toUpperCase(Locale.ROOT);
        Matcher m = P.matcher(s);
        if (m.matches()) {
            BigDecimal d = m.group(3) == null ? BigDecimal.ZERO : new BigDecimal(m.group(3));
            return new PriceSpec(Base.valueOf(m.group(1)), "-".equals(m.group(2)) ? d.negate() : d);
        }
        try {
            return new PriceSpec(Base.FIXED, new BigDecimal(s));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid price spec '" + spec + "' (REF|CEIL|FLOOR|HINT[+-n] or a number)");
        }
    }

    /** Applies the spec to the base value found on screen (ignored for FIXED). */
    public BigDecimal resolve(BigDecimal baseValue) {
        if (base == Base.FIXED) {
            return delta;
        }
        if (baseValue == null) {
            throw new IllegalStateException("No on-screen value for " + base);
        }
        return baseValue.add(delta);
    }

    public boolean needsHint() {
        return base == Base.HINT;
    }
}
