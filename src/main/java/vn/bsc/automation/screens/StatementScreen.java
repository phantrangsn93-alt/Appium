package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.DatePicker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.Comparator;

/**
 * Statement-like screens of "Quản lý tài khoản" (Sao kê tiền / Sao kê chứng khoán / histories): sub-account,
 * "Từ ngày" / "Đến ngày" (date picker), totals and rows. Surveyed on iOS 14/11/2026:
 * <pre>
 *   Tiểu khoản | MR90_BSC - 0001090627
 *   Từ ngày            Đến ngày
 *   16/11/2026         16/11/2026
 *   Đầu kỳ:  +2,366,958 ... Cuối kỳ: +2,366,958
 *   rows: "11/11/2026 | Chuyển khoản nội bộ: CHUYEN TIEN | -10,000"
 * </pre>
 */
public class StatementScreen extends MenuTargetScreen {

    public static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern DATE = Pattern.compile("[0-3][0-9]/[01][0-9]/[0-9]{4}");

    public StatementScreen(MenuItem item) {
        super(item);
    }

    /** The two date value nodes: [Từ ngày, Đến ngày] (left, right) on the filter row. */
    private List<UiNode> dateFields() {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        int labelsY = nodes.stream().filter(n -> "Từ ngày".equals(n.desc())).mapToInt(UiNode::y).findFirst()
                .orElseThrow(() -> new AssertionError("Không thấy ô Từ ngày"));
        List<UiNode> dates = nodes.stream()
                .filter(n -> DATE.matcher(n.desc()).matches() && n.y() > labelsY && n.y() < labelsY + 120)
                .sorted(Comparator.comparingInt(UiNode::x)).toList();
        if (dates.size() < 2) {
            throw new AssertionError("Không đọc được Từ ngày / Đến ngày: " + dates);
        }
        return dates.subList(0, 2);
    }

    public LocalDate fromDate() {
        return LocalDate.parse(dateFields().get(0).desc(), DMY);
    }

    public LocalDate toDate() {
        return LocalDate.parse(dateFields().get(1).desc(), DMY);
    }

    @Step("Từ ngày = {date}")
    public void setFrom(LocalDate date) {
        pick(dateFields().get(0), date);
    }

    @Step("Đến ngày = {date}")
    public void setTo(LocalDate date) {
        pick(dateFields().get(1), date);
    }

    private void pick(UiNode field, LocalDate date) {
        GestureUtils.tap(field);
        DatePicker picker = new DatePicker();
        picker.waitUntilLoaded();
        picker.choose(date);
        WaitUtils.pause(Duration.ofSeconds(2)); // list reloads
    }

    /** Message of a dialog shown after a filter change (validation), or empty. */
    public Optional<String> validationMessage() {
        AlertDialog d = new AlertDialog();
        if (d.isShown(Duration.ofSeconds(2))) {
            String m = d.getMessage();
            if (d.hasButton(AlertDialog.BTN_CLOSE)) {
                d.close();
            } else {
                pressBack();
            }
            return Optional.of(m);
        }
        return Optional.empty();
    }

    /** Value right after a "label:" line (Đầu kỳ / Phát sinh tăng / Phát sinh giảm / Cuối kỳ). */
    public BigDecimal total(String label) {
        List<String> l = lines();
        for (int i = 0; i < l.size() - 1; i++) {
            if (l.get(i).equals(label) || l.get(i).startsWith(label + ":")) {
                String rest = l.get(i).contains(":") ? l.get(i).substring(l.get(i).indexOf(':') + 1).trim() : "";
                return amount(rest.isEmpty() ? l.get(i + 1) : rest);
            }
        }
        throw new AssertionError("Không thấy " + label);
    }

    /** Statement rows (first line = date) visible on the screen. */
    public List<List<String>> rowsOnScreen() {
        List<List<String>> out = new ArrayList<>();
        for (UiNode n : UiNode.snapshot(driver().getPageSource())) {
            String[] parts = n.desc().split("\n");
            if (parts.length >= 2 && DATE.matcher(parts[0].trim()).matches() && n.y() > dateFields().get(0).y() + 80) {
                List<String> r = new ArrayList<>();
                for (String p : parts) {
                    r.add(p.trim());
                }
                out.add(r);
            }
        }
        return out;
    }

    public static BigDecimal amount(String text) {
        String t = text == null ? "" : text.replaceAll("[^0-9\\-]", "");
        if (t.isEmpty() || "-".equals(t)) {
            throw new AssertionError("Không đọc được số tiền: '" + text + "'");
        }
        return new BigDecimal(t);
    }

    /** Selects another sub-account than the current one; returns the new value, or null when there is only one. */
    @Step("Đổi sang tiểu khoản khác")
    public String switchSubAccount() {
        String current = fieldValue("Tiểu khoản");
        tapField("Tiểu khoản");
        WaitUtils.pause(Duration.ofSeconds(2));
        List<UiNode> options = UiNode.snapshot(driver().getPageSource()).stream()
                .filter(n -> n.clickable() && n.desc().matches("(?s).* - [0-9]{6,}.*") && !n.desc().startsWith("Tiểu khoản")
                        && !n.desc().contains(current.replaceAll(".*-\\s*", "")))
                .toList();
        if (options.isEmpty()) {
            pressBack();
            return null;
        }
        UiNode o = options.get(0);
        GestureUtils.tap(o);
        WaitUtils.condition(() -> !fieldValue("Tiểu khoản").equals(current), Duration.ofSeconds(5));
        return fieldValue("Tiểu khoản");
    }
}
