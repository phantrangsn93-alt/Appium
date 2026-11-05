package vn.bsc.automation.screens.components;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;

import java.util.Comparator;
import java.util.List;

/**
 * The app's own numeric keypad (opened by tapping the price / quantity value on the order form):
 * <pre>
 *                                     [Đóng]
 *   7   8   9   -0.5  +0.5
 *   4   5   6   -0.1  +0.1
 *   1   2   3   -0.05 +0.05
 *   .   0   00  &lt;ref&gt; [⌫]
 * </pre>
 * Keys expose their label as content-desc; the backspace key has none (bottom-right key).
 */
public class NumericKeypad extends BaseScreen {

    private final By closeKey = Locators.a11y("Đóng");
    private final By digitKey = Locators.a11y("5");

    @Override
    protected By anchor() {
        return digitKey;
    }

    /** Clears the current value then types {@code value} (e.g. "7.08", "150"), then closes the keypad. */
    public void enter(String value) {
        clear();
        for (char ch : value.toCharArray()) {
            tap(Locators.a11y(String.valueOf(ch)));
        }
        close();
    }

    public void clear() {
        UiNode backspace = backspace(); // locate once (1 page-source call), then tap repeatedly
        for (int i = 0; i < 12; i++) {
            tapCenterOf(backspace);
        }
    }

    /** Closes the keypad and waits until it is gone (while closing it still hides the form's semantics). */
    public void close() {
        tap(closeKey);
        vn.bsc.automation.core.utils.WaitUtils.waitInvisible(digitKey, java.time.Duration.ofSeconds(5));
    }

    /** Backspace = right-most key of the lowest key row (no content-desc). */
    private UiNode backspace() {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        int digit0Y = nodes.stream().filter(n -> "0".equals(n.desc()) && n.clickable())
                .mapToInt(UiNode::y).max().orElseThrow(() -> new AssertionError("Keypad not shown"));
        return nodes.stream()
                .filter(n -> n.clickable() && Math.abs(n.y() - digit0Y) < 20 && n.desc().isEmpty())
                .max(Comparator.comparingInt(UiNode::x))
                .orElseThrow(() -> new AssertionError("Backspace key not found"));
    }

    private void tapCenterOf(UiNode n) {
        vn.bsc.automation.core.utils.GestureUtils.tap(n.x() + n.width() / 2, n.y() + n.height() / 2);
    }
}
