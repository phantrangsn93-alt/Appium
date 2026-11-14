package vn.bsc.automation.screens.components;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.NumberUtils;
import vn.bsc.automation.core.utils.UiNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bottom sheet "TP/SL" (take profit / stop loss), opened by tapping a holding row in Giao dịch > Danh mục.
 * Surveyed 06/11/2026 (MR10+ BSC, AAA):
 * <pre>
 * TP/SL                        (title)
 * Mã CK            AAA
 * Giá vốn          8.45
 * Khối lượng       [ - 0 + ]   ≤ 852,800      (hint = quantity held)
 * Chốt lời         [ - 0 + ]   ≥ 7.53         Lãi/Lỗ dự kiến 0 (0.00%)
 * Cắt lỗ           [ - 0 + ]   ≤ 7.53         Lãi/Lỗ dự kiến 0 (0.00%)
 * [Xác nhận]                                  ("Scrim" above the sheet closes it)
 * </pre>
 * The 3 stepper values ("0") have no label; they are the clickable nodes on the same row as their label.
 * WARNING: {@link #confirm()} creates a REAL TP/SL order.
 */
public class TpSlSheet extends BaseScreen {

    private final By title = Locators.a11y("TP/SL");
    private final By confirmButton = Locators.classWithDesc("android.widget.Button", "Xác nhận");

    @Override
    protected By anchor() {
        return title;
    }

    public String symbol() {
        return valueRightOf("Mã CK");
    }

    public BigDecimal costPrice() {
        return NumberUtils.parse(valueRightOf("Giá vốn"));
    }

    /** Max quantity from the hint "≤ 852,800". */
    public long maxQuantity() {
        return NumberUtils.parseLong(hintRightOf("Khối lượng").replaceAll("[^0-9,]", ""));
    }

    /** Hint of "Chốt lời", e.g. "≥ 7.53". */
    public String takeProfitHint() {
        return hintRightOf("Chốt lời");
    }

    /** Hint of "Cắt lỗ", e.g. "≤ 7.53". */
    public String stopLossHint() {
        return hintRightOf("Cắt lỗ");
    }

    @Step("Đóng TP/SL")
    public void close() {
        // the Scrim covers the area above the sheet; tap near the top of the screen
        org.openqa.selenium.Dimension s = driver().manage().window().getSize();
        GestureUtils.tap(s.getWidth() / 2, s.getHeight() / 5);
    }

    @Step("TP/SL - Khối lượng = {quantity}")
    public TpSlSheet setQuantity(long quantity) {
        enterOnRow("Khối lượng", String.valueOf(quantity));
        return this;
    }

    @Step("TP/SL - Chốt lời = {price}")
    public TpSlSheet setTakeProfit(BigDecimal price) {
        enterOnRow("Chốt lời", price.toPlainString());
        return this;
    }

    @Step("TP/SL - Cắt lỗ = {price}")
    public TpSlSheet setStopLoss(BigDecimal price) {
        enterOnRow("Cắt lỗ", price.toPlainString());
        return this;
    }

    /** Number of a hint like "≥ 7.53" / "≤ 852,800". */
    public static BigDecimal hintValue(String hint) {
        return NumberUtils.parse(hint.replaceAll("[^0-9.,]", ""));
    }

    /**
     * The stepper value of a row ("0" between "-" and "+") has no label: it is the clickable numeric node on the
     * label's row. Tapping it opens the app keypad.
     */
    private void enterOnRow(String label, String value) {
        UiNode field = field(label);
        GestureUtils.tap(field.x() + field.width() / 2, field.y() + field.height() / 2);
        NumericKeypad keypad = new NumericKeypad().waitUntilLoaded();
        keypad.clear();
        keypad.enter(value);
        keypad.close();
    }

    /** Value node of a stepper row (content-desc on Android, label or value on iOS). */
    private UiNode field(String label) {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        UiNode l = label(nodes, label);
        return nodes.stream()
                .filter(n -> n.clickable() && sameRow(n, l) && n.x() > l.x() + l.width() / 2
                        && text(n).matches("[0-9][0-9.,]*"))
                .min(java.util.Comparator.comparingInt(UiNode::x))
                .orElseThrow(() -> new AssertionError("TP/SL: không thấy ô nhập của " + label));
    }

    private static String text(UiNode n) {
        return n.desc().isBlank() ? (n.text() == null ? "" : n.text().trim()) : n.desc().trim();
    }

    /** Current value typed in a row ("Khối lượng" / "Chốt lời" / "Cắt lỗ"). */
    public BigDecimal value(String label) {
        return NumberUtils.parse(text(field(label)));
    }

    /**
     * SAFETY: the sheet must show exactly the quantity / prices the test computed before the REAL order is confirmed.
     */
    @Step("Kiểm tra TP/SL trước khi xác nhận: KL {quantity}, chốt lời {tp}, cắt lỗ {sl}")
    public void verify(long quantity, BigDecimal tp, BigDecimal sl) {
        BigDecimal q = value("Khối lượng"), t = value("Chốt lời"), c = value("Cắt lỗ");
        if (q.compareTo(BigDecimal.valueOf(quantity)) != 0 || t.compareTo(tp) != 0 || c.compareTo(sl) != 0) {
            close();
            throw new AssertionError("SAFETY: form TP/SL khác dữ liệu test - KHÔNG gửi lệnh. Form: KL " + q
                    + ", chốt lời " + t + ", cắt lỗ " + c + " | Cần: KL " + quantity + ", chốt lời " + tp + ", cắt lỗ " + sl);
        }
    }

    /** Creates a REAL take-profit / stop-loss order. */
    @Step("Xác nhận TP/SL")
    public void confirm() {
        tap(confirmButton);
    }

    // ------------------------------------------------------------------ helpers

    private UiNode label(List<UiNode> nodes, String text) {
        String want = Locators.nfc(text);
        return nodes.stream().filter(n -> want.equals(n.desc())).findFirst()
                .orElseThrow(() -> new AssertionError("TP/SL label not found: " + text));
    }

    /** First non-clickable node on the label's row, right of it (e.g. "AAA", "8.45"). */
    private String valueRightOf(String text) {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        UiNode l = label(nodes, text);
        return nodes.stream()
                .filter(n -> !n.desc().isBlank() && n.x() > l.x() + l.width() / 2 && sameRow(n, l))
                .min(java.util.Comparator.comparingInt(UiNode::x))
                .map(UiNode::desc).orElseThrow(() -> new AssertionError("No value for " + text));
    }

    /** Right-most node on the label's row (the "≤ / ≥" hint). */
    private String hintRightOf(String text) {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        UiNode l = label(nodes, text);
        return nodes.stream()
                .filter(n -> !n.desc().isBlank() && n.x() > l.x() + l.width() / 2 && sameRow(n, l))
                .max(java.util.Comparator.comparingInt(UiNode::x))
                .map(UiNode::desc).orElseThrow(() -> new AssertionError("No hint for " + text));
    }

    private static boolean sameRow(UiNode a, UiNode b) {
        int ca = a.y() + a.height() / 2, cb = b.y() + b.height() / 2;
        return Math.abs(ca - cb) < Math.max(a.height(), b.height());
    }
}
