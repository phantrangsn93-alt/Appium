package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;

import java.util.List;

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
}
