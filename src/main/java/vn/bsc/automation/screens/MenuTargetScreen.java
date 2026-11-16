package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;

import java.util.List;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.WaitUtils;

/**
 * Any screen opened from the menu, identified by its title (content-desc of the app-bar title).
 * Common locators of these screens (surveyed 06/11/2026):
 * <ul>
 *   <li>Sub-account selector: {@code Locators.field("Tiểu khoản")} - desc "Tiểu khoản\n&lt;tên&gt; - &lt;số TK&gt;"</li>
 *   <li>Date range: label Views "Từ ngày" / "Đến ngày" + clickable date Views "dd/MM/yyyy" below them</li>
 *   <li>Filters: {@code Locators.field("Mã CK")}, {@code field("M/B")}, {@code field("Trạng thái")},
 *       {@code field("Loại giao dịch")}; symbol search box = first EditText (hint "Tất cả")</li>
 *   <li>Tabs: {@code Locators.tab(name)} - desc "&lt;name&gt;\nTab N trong tổng số M"</li>
 * </ul>
 */
public class MenuTargetScreen extends BaseScreen {

    public static final By SUB_ACCOUNT = Locators.field("Tiểu khoản");
    public static final By SYMBOL_SEARCH = Locators.editText(0);

    private final String title;

    public String title() {
        return title;
    }

    public MenuTargetScreen(String title) {
        this.title = title;
    }

    public MenuTargetScreen(MenuItem item) {
        this(item.title);
    }

    @Override
    protected By anchor() {
        return Locators.a11y(title);
    }

    /** Value of a "label\nvalue" field, e.g. fieldValue("Tiểu khoản") -> "Thường - 0101042221". */
    public String fieldValue(String label) {
        String desc = getDesc(Locators.field(label));
        return desc.substring(desc.indexOf('\n') + 1).trim();
    }

    public void tapField(String label) {
        tap(Locators.field(label));
    }

    /**
     * Value shown right below a label (separate View), e.g. valueBelow("Số dư khả dụng") -> "4,514,141".
     * Read from one page-source snapshot (balances refresh in real time).
     */
    public String valueBelow(String label) {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        String want = Locators.nfc(label);
        for (int i = 0; i < nodes.size(); i++) {
            if (want.equals(nodes.get(i).desc())) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    if (!nodes.get(j).desc().isBlank()) {
                        return nodes.get(j).desc();
                    }
                }
            }
        }
        throw new AssertionError("Label not found: " + label);
    }

    public boolean hasText(String desc) {
        return isVisible(Locators.a11y(desc));
    }

    /** Like {@link #hasText} but scrolls down first (long forms, e.g. "Chuyển tiền ra ngoài"). */
    public boolean hasTextWithScroll(String desc) {
        return isVisibleWithScroll(Locators.a11y(desc));
    }

    public void openTab(String name) {
        tapTab(Locators.tab(name));
    }

    /**
     * Every text line of the screen (labels and values, NFC, without zero-width chars), collected while scrolling
     * down up to {@code maxScrolls} times. Multi-line nodes are split into lines.
     */
    public List<String> collectTexts(int maxScrolls) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String previous = null;
        for (int i = 0; i <= maxScrolls; i++) {
            List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
            StringBuilder sig = new StringBuilder();
            for (UiNode n : nodes) {
                for (String line : (n.desc() + "\n" + n.text()).split("\n")) {
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        out.add(t);
                        sig.append(t);
                    }
                }
            }
            if (sig.toString().equals(previous)) {
                break; // end of the screen
            }
            previous = sig.toString();
            if (i < maxScrolls) {
                GestureUtils.scrollBy(0.5);
            }
        }
        return new ArrayList<>(out);
    }

    /** Data rows: clickable multi-line nodes (statement lines, order history rows...). */
    public List<String> rows() {
        return UiNode.snapshot(driver().getPageSource()).stream()
                .filter(n -> n.clickable() && n.desc().contains("\n") && !n.desc().contains("Tab "))
                .map(UiNode::desc).toList();
    }

    /** Opens a dropdown field and picks the option containing {@code text} (e.g. a sub-account number). */
    public void selectFieldOption(String label, String text) {
        tapField(label);
        String want = Locators.nfc(text);
        UiNode[] hit = new UiNode[1];
        WaitUtils.condition(() -> {
            hit[0] = UiNode.snapshot(driver().getPageSource()).stream()
                    .filter(n -> n.clickable() && n.desc().replace("\n", " ").contains(want)
                            && !n.desc().startsWith(Locators.nfc(label)))
                    .findFirst().orElse(null);
            return hit[0] != null;
        }, Duration.ofSeconds(10));
        if (hit[0] == null) {
            throw new AssertionError("Không có lựa chọn \"" + text + "\" cho " + label);
        }
        GestureUtils.tap(hit[0]);
    }

    /** Text lines of the current screen in order, duplicates KEPT (equal values such as Đầu kỳ = Cuối kỳ). */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (UiNode n : UiNode.snapshot(driver().getPageSource())) {
            for (String line : n.desc().split("\n")) {
                if (!line.trim().isEmpty()) {
                    out.add(line.trim());
                }
            }
        }
        return out;
    }
}
