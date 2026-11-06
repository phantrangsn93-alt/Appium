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
