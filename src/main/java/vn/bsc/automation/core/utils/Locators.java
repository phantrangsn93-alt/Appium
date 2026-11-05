package vn.bsc.automation.core.utils;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

import java.text.Normalizer;

/**
 * Locator helpers for a Flutter app driven by UiAutomator2.
 * <p>
 * Flutter exposes widgets through the Semantics tree, which UiAutomator2 maps to {@code content-desc}.
 * Many nodes merge several texts into one multi-line content-desc (e.g. "Sổ lệnh\nTab 2 trong tổng số 4"),
 * so beside exact accessibility id we need "starts with"/"contains" matchers.
 * <p>
 * <b>Vietnamese Unicode:</b> this app mixes precomposed (NFC), decomposed (NFD, "o" + U+0301) and partially
 * composed ("â" + U+0323) strings. Accessibility-id matching is byte-exact, so for any text containing
 * accented characters the helpers build a regex accepting every canonically-equivalent encoding.
 * <p>
 * Priority: {@link #a11y} (exact) &gt; {@link #descStartsWith} / {@link #descContains} &gt; XPath (last resort).
 */
public final class Locators {

    private Locators() {
    }

    /** Exact content-desc match (normalization-insensitive). */
    public static By a11y(String desc) {
        if (isPlain(desc)) {
            return AppiumBy.accessibilityId(desc);
        }
        return descMatches(anyForm(desc));
    }

    public static By descStartsWith(String prefix) {
        if (isPlain(prefix)) {
            return uiSelector("descriptionStartsWith(\"" + escape(prefix) + "\")");
        }
        return descMatches(anyForm(prefix) + ".*");
    }

    public static By descContains(String part) {
        if (isPlain(part)) {
            return uiSelector("descriptionContains(\"" + escape(part) + "\")");
        }
        return descMatches(".*" + anyForm(part) + ".*");
    }

    /**
     * Element whose content-desc fully matches a regex. Build literal parts with {@link #anyForm(String)}
     * and never use backslashes (UiSelector does not unescape them), e.g.
     * {@code descRegex(anyForm("Huỷ ") + "[0-9]+" + anyForm(" mã"))}.
     */
    public static By descRegex(String regex) {
        return descMatches(regex);
    }

    /** Element whose content-desc equals any of the given labels (normalization-insensitive). */
    public static By descMatchesAny(String... labels) {
        StringBuilder sb = new StringBuilder("(?:");
        for (int i = 0; i < labels.length; i++) {
            sb.append(i == 0 ? "" : "|").append(anyForm(labels[i]));
        }
        return descMatches(sb.append(')').toString());
    }

    /** Element of a specific class with an exact content-desc, e.g. bottom navigation ImageView. */
    public static By classWithDesc(String className, String desc) {
        String cls = "className(\"" + className + "\")";
        if (isPlain(desc)) {
            return uiSelector(cls + ".description(\"" + escape(desc) + "\")");
        }
        return uiSelector(cls + ".descriptionMatches(\"(?s)" + anyForm(desc) + "\")");
    }

    /** Flutter TabBar tab: content-desc looks like "&lt;name&gt;\nTab N trong tổng số M". */
    public static By tab(String name) {
        return By.xpath("//*[starts-with(@content-desc,'" + name + "') and contains(@content-desc,'trong tổng số')]");
    }

    /** N-th (0-based) EditText on screen. Flutter TextFields have no id/desc. */
    public static By editText(int index) {
        return uiSelector("className(\"android.widget.EditText\").instance(" + index + ")");
    }

    /** Converts app text to the canonical form used in assertions. */
    public static String nfc(String s) {
        return s == null ? null : Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    // ------------------------------------------------------------------ internals

    private static By descMatches(String regex) {
        return uiSelector("descriptionMatches(\"(?s)" + regex + "\")");
    }

    private static By uiSelector(String body) {
        return AppiumBy.androidUIAutomator("new UiSelector()." + body);
    }

    /** True when the text has a single encoding (no accented characters). */
    private static boolean isPlain(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).equals(Normalizer.normalize(s, Normalizer.Form.NFC));
    }

    /**
     * Regex matching the text with every accented character in ANY canonically-equivalent encoding.
     * The app mixes NFC ("ậ"), NFD ("a"+U+0302+U+0323) and partially composed forms ("â"+U+0323).
     * NOTE: the UiAutomator2 UiSelector parser passes string literals through WITHOUT un-escaping
     * backslashes, so the regex is built without any backslash: meta characters go in a [class].
     */
    public static String anyForm(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder();
        nfc.codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            java.util.Set<String> variants = encodings(c);
            if (variants.size() == 1) {
                sb.append(literal(c));
            } else {
                sb.append("(?:").append(String.join("|", variants)).append(')');
            }
        });
        return sb.toString();
    }

    /**
     * All encodings of one character: for each subset of its combining marks, the base composed with that
     * subset (when it composes to a single code point) followed by the remaining marks; plus every order
     * of the remaining marks. E.g. "ậ" -&gt; {ậ, â+U+0323, ạ+U+0302, a+U+0302+U+0323, a+U+0323+U+0302}.
     */
    static java.util.Set<String> encodings(String c) {
        String d = Normalizer.normalize(c, Normalizer.Form.NFD);
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        out.add(c);
        if (d.equals(c)) {
            return out;
        }
        int baseLen = Character.charCount(d.codePointAt(0));
        String base = d.substring(0, baseLen);
        int[] marks = d.substring(baseLen).codePoints().toArray();
        int n = marks.length;
        for (int mask = 0; mask < (1 << n); mask++) {
            StringBuilder in = new StringBuilder(base);
            java.util.List<Integer> rest = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    in.appendCodePoint(marks[i]);
                } else {
                    rest.add(marks[i]);
                }
            }
            String head = Normalizer.normalize(in.toString(), Normalizer.Form.NFC);
            if (head.codePointCount(0, head.length()) != 1) {
                continue; // this subset does not compose to a single character
            }
            for (java.util.List<Integer> perm : permutations(rest)) {
                StringBuilder v = new StringBuilder(head);
                perm.forEach(v::appendCodePoint);
                out.add(v.toString());
            }
        }
        return out;
    }

    private static java.util.List<java.util.List<Integer>> permutations(java.util.List<Integer> items) {
        java.util.List<java.util.List<Integer>> res = new java.util.ArrayList<>();
        if (items.isEmpty()) {
            res.add(new java.util.ArrayList<>());
            return res;
        }
        for (int i = 0; i < items.size(); i++) {
            java.util.List<Integer> rest = new java.util.ArrayList<>(items);
            Integer first = rest.remove(i);
            for (java.util.List<Integer> p : permutations(rest)) {
                p.add(0, first);
                res.add(p);
            }
        }
        return res;
    }

    private static String literal(String c) {
        if ("\"".equals(c) || "\\".equals(c) || "]".equals(c) || "^".equals(c)) {
            throw new IllegalArgumentException("Unsupported character in locator text: " + c);
        }
        return ".$|()[{}*+?".contains(c) ? "[" + c + "]" : c;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
