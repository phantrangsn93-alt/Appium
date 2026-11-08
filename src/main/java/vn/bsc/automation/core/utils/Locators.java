package vn.bsc.automation.core.utils;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import vn.bsc.automation.core.config.Platform;

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
 * <p>
 * <b>iOS (XCUITest):</b> the same Semantics label is exposed as {@code label}; every helper then builds an
 * {@code -ios predicate string} ({@code label MATCHES "..."}, ICU regex, full match). Class names are Android
 * <i>roles</i> translated by {@link Platform#iosTypes}; XPath written for Android goes through {@link #xpath}.
 */
public final class Locators {

    private Locators() {
    }

    /** Exact content-desc match (normalization-insensitive). */
    public static By a11y(String desc) {
        if (Platform.ios()) {
            return predicate(isPlain(desc) ? "label == " + quote(desc) : "label MATCHES " + quote("(?s)" + anyForm(desc)));
        }
        if (isPlain(desc)) {
            return AppiumBy.accessibilityId(desc);
        }
        return descMatches(anyForm(desc));
    }

    public static By descStartsWith(String prefix) {
        if (Platform.ios() && isPlain(prefix)) {
            return predicate("label BEGINSWITH " + quote(prefix));
        }
        if (isPlain(prefix)) {
            return uiSelector("descriptionStartsWith(\"" + escape(prefix) + "\")");
        }
        return descMatches(anyForm(prefix) + ".*");
    }

    public static By descContains(String part) {
        if (Platform.ios() && isPlain(part)) {
            return predicate("label CONTAINS " + quote(part));
        }
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

    /** Element of a class whose content-desc CONTAINS the label (e.g. a tab with an unread badge "6\nThông báo"). */
    public static By classWithDescContaining(String className, String label) {
        if (Platform.ios()) {
            return typedLabel(className, "(?s).*" + anyForm(label) + ".*");
        }
        return uiSelector("className(\"" + className + "\").descriptionMatches(\"(?s).*" + anyForm(label) + ".*\")");
    }

    /** Element of a class whose content-desc equals any of the labels (normalization-insensitive). */
    public static By classWithDescAny(String className, String... labels) {
        StringBuilder sb = new StringBuilder("(?:");
        for (int i = 0; i < labels.length; i++) {
            sb.append(i == 0 ? "" : "|").append(anyForm(labels[i]));
        }
        if (Platform.ios()) {
            return typedLabel(className, "(?s)" + sb.append(')'));
        }
        return uiSelector("className(\"" + className + "\").descriptionMatches(\"(?s)" + sb.append(')') + "\")");
    }

    /** Element of a specific class with an exact content-desc, e.g. bottom navigation ImageView. */
    public static By classWithDesc(String className, String desc) {
        if (Platform.ios()) {
            return typedLabel(className, "(?s)" + anyForm(desc));
        }
        String cls = "className(\"" + className + "\")";
        if (isPlain(desc)) {
            return uiSelector(cls + ".description(\"" + escape(desc) + "\")");
        }
        return uiSelector(cls + ".descriptionMatches(\"(?s)" + anyForm(desc) + "\")");
    }

    /** Flutter TabBar tab: content-desc looks like "&lt;name&gt;\nTab N trong tổng số M". */
    public static By tab(String name) {
        return xpath("//*[starts-with(@content-desc,'" + name + "') and contains(@content-desc,'trong tổng số')]");
    }

    /** N-th (0-based) EditText on screen. Flutter TextFields have no id/desc. */
    public static By editText(int index) {
        if (Platform.ios()) {
            return By.xpath("(//*[self::XCUIElementTypeTextField or self::XCUIElementTypeSecureTextField])[" + (index + 1) + "]");
        }
        return uiSelector("className(\"android.widget.EditText\").instance(" + index + ")");
    }

    /**
     * Labelled field whose content-desc is "&lt;label&gt;\n&lt;value&gt;", e.g. "Tiểu khoản chuyển\nThường - 0101042221".
     * Matches only the exact label: "Tiểu khoản" does not match "Tiểu khoản chuyển" (the character right
     * after the label must be the line break, not a space).
     */
    public static By field(String label) {
        return descMatches(anyForm(label) + "[^ ].*");
    }

    /**
     * Converts app text to the canonical form used in assertions: NFC, without zero-width characters
     * (the app inserts U+200B between every character of sub-account values: "T​h​ư...").
     */
    public static String nfc(String s) {
        return s == null ? null : ZERO_WIDTH.matcher(Normalizer.normalize(s, Normalizer.Form.NFC)).replaceAll("");
    }

    private static final java.util.regex.Pattern ZERO_WIDTH = java.util.regex.Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");

    /**
     * Password / PIN field. Android: EditText password=true. iOS: Flutter exposes obscured fields as a plain
     * TextField labelled with its hint ("Mật khẩu", "Nhập mật khẩu đặt lệnh"), or SecureTextField.
     */
    public static By passwordField() {
        if (Platform.ios()) {
            // the hint label disappears once typed: then only the "•••" value identifies the field
            return predicate("type == \"XCUIElementTypeSecureTextField\" OR (type == \"XCUIElementTypeTextField\" AND "
                    + "(label MATCHES " + quote(PASSWORD_LABEL) + " OR value MATCHES " + quote(BULLETS) + "))");
        }
        return By.xpath("//android.widget.EditText[@password='true']");
    }

    /** Non-password text field (account number, amount, symbol search...). */
    public static By textField() {
        if (Platform.ios()) {
            return predicate("type == \"XCUIElementTypeTextField\" AND NOT (label MATCHES " + quote(PASSWORD_LABEL)
                    + ") AND NOT (value MATCHES " + quote(BULLETS) + ")");
        }
        return By.xpath("//android.widget.EditText[@password='false']");
    }

    /** PIN sheet: the password field, or the only text field when the app does not flag it as password. */
    public static By pinField() {
        if (Platform.ios()) {
            return passwordField();
        }
        return By.xpath("//android.widget.EditText[@password='true'] | //android.widget.EditText");
    }

    private static final String BULLETS = "[•●]+";

    private static final String PASSWORD_LABEL = "(?is).*(?:" + anyForm("mật khẩu") + "|" + anyForm("Mật khẩu")
            + "|" + anyForm("mã PIN") + "|password).*";

    /** All elements of an Android role (e.g. android.widget.RadioButton); translated on iOS. */
    public static By byClass(String className) {
        if (Platform.ios()) {
            String[] types = Platform.iosTypes(className);
            return types == null ? By.xpath("//*") : predicate(typeClause(types));
        }
        return By.className(className);
    }

    /**
     * XPath written against the Android hierarchy, translated for iOS:
     * {@code @content-desc -> @label}, {@code @clickable} -> accessible non-static-text element,
     * EditText[@password] -> (Secure)TextField, Button -> XCUIElementTypeButton, View/ImageView -> any, Toast -> none.
     */
    public static By xpath(String androidXpath) {
        return By.xpath(Platform.ios() ? toIosXpath(androidXpath) : androidXpath);
    }

    static String toIosXpath(String x) {
        String clickable = "(@accessible='true' and not(self::XCUIElementTypeStaticText))";
        return x.replace("android.widget.EditText[@password='true']", "XCUIElementTypeSecureTextField")
                .replace("android.widget.EditText[@password='false']", "XCUIElementTypeTextField")
                .replace("android.widget.EditText", "*[self::XCUIElementTypeTextField or self::XCUIElementTypeSecureTextField]")
                .replace("android.widget.Button", "XCUIElementTypeButton")
                .replace("android.widget.Toast", "XCUIElementTypeNone")
                .replace("android.widget.ImageView",
                        "*[self::XCUIElementTypeImage or self::XCUIElementTypeButton or self::XCUIElementTypeOther]")
                .replace("android.view.View", "*")
                .replace("@clickable='true'", clickable)
                .replace("@clickable='false'", "not" + clickable)
                .replace("@content-desc", "@label");
    }

    // ------------------------------------------------------------------ internals

    private static By predicate(String p) {
        return AppiumBy.iOSNsPredicateString(p);
    }

    /** iOS: element of the role's XCUIElementType(s) whose label fully matches the ICU regex. */
    private static By typedLabel(String className, String regex) {
        String[] types = Platform.iosTypes(className);
        String label = "label MATCHES " + quote(regex);
        return predicate(types == null ? label : typeClause(types) + " AND " + label);
    }

    private static String typeClause(String[] types) {
        StringBuilder sb = new StringBuilder("type IN {");
        for (int i = 0; i < types.length; i++) {
            sb.append(i == 0 ? "" : ",").append(quote(types[i]));
        }
        return sb.append('}').toString();
    }

    /** NSPredicate string literal; locator texts never contain '"' or backslash (see {@link #literal}). */
    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static By descMatches(String regex) {
        if (Platform.ios()) {
            return predicate("label MATCHES " + quote("(?s)" + regex));
        }
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
