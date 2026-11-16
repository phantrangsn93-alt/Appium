package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import io.qameta.allure.Step;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.screens.components.ContentInspector;

/** "Thị trường" - home screen after login. */
public class MarketScreen extends BaseScreen {

    /** Tabs visible without scrolling. A 5th tab "ETFs" exists off-screen (swipe the tab bar left). */
    public static final String[] TABS = {"Tổng quan", "Cổ phiếu", "Phái sinh", "Chứng quyền"};
    public static final String TAB_ETFS = "ETFs";
    /** Quick actions visible without scrolling (label "SmartOTP" has no space - UAT.1-master-UAT5). */
    public static final String[] QUICK_ACTIONS = {"SmartOTP", "Mở tài khoản", "Khuyến nghị", "Nộp tiền"};
    /** More quick actions after swiping the row left; "Tuỳ chỉnh" opens "Cài đặt chức năng nổi bật". */
    public static final String[] MORE_QUICK_ACTIONS = {"Lịch sự kiện", "Danh mục", "Đồ thị", "Tuỳ chỉnh"};

    @Override
    protected By anchor() {
        return Locators.tab("Tổng quan");
    }

    public void openTab(String name) {
        tapTab(Locators.tab(name));
    }

    public boolean isTabSelected(String name) {
        return isSelected(Locators.tab(name));
    }

    public boolean isQuickActionVisible(String name) {
        quickRowToStart();
        return isVisible(Locators.a11y(name));
    }

    /** Quick actions shown when the row is scrolled to its end - used to find the row on screen. */
    private static final String[] ROW_MARKERS = {"SmartOTP", "Mở tài khoản", "Khuyến nghị", "Nộp tiền", "Lịch sự kiện",
            "Danh mục", "Đồ thị", "Tuỳ chỉnh"};

    /** Scrolls the horizontal quick-action row back to its first item (a previous test may have left it scrolled). */
    public void quickRowToStart() {
        Optional<UiNode> any = inspector.content().stream()
                .filter(n -> Arrays.asList(ROW_MARKERS).contains(n.desc())).findFirst();
        if (any.isEmpty() || inspector.content().stream().anyMatch(n -> "SmartOTP".equals(n.desc()))) {
            return;
        }
        int y = any.get().y() + any.get().height() / 2, w = window()[0];
        for (int i = 0; i < 2; i++) {
            GestureUtils.swipe(new Point((int) (w * 0.15), y),
                    new Point((int) (w * 0.85), y), Duration.ofMillis(400));
        }
    }

    /** e.g. "VN INDEX\n1,740.73\n+7.71 (+0.44%)\n15,829.8 (Tỷ VND)" */
    public String getIndexCard(String indexName) {
        return getDesc(Locators.descStartsWith(indexName));
    }

    // ---------------------------------------------------------------- data checks (tab / filter / section / chart)

    private final ContentInspector inspector =
            new ContentInspector();

    /** Opens a tab; the tab bar scrolls, so an off-screen tab (ETFs) is revealed by swiping the bar left. */
    @Step("Thị trường - tab {name}")
    public void selectTab(String name) {
        By tab = Locators.tab(name);
        for (int i = 0; i < 3 && !visibleTab(name); i++) {
            List<UiNode> tabs = inspector.content().stream()
                    .filter(n -> n.desc().contains("trong tổng số")).collect(Collectors.toList());
            int y = tabs.isEmpty() ? 296 : tabs.get(0).y() + tabs.get(0).height() / 2;
            Dimension w = driver().manage().window().getSize();
            GestureUtils.swipe(new Point((int) (w.width * 0.85), y),
                    new Point((int) (w.width * 0.15), y), Duration.ofMillis(400));
        }
        tapTab(tab);
    }

    private boolean visibleTab(String name) {
        return inspector.content().stream().anyMatch(n -> n.desc().startsWith(name + "\n") && n.desc().contains("trong tổng số"));
    }

    /** Exchange filter chip of the current tab (Tất cả / HOSE / HNX / UPCOM). */
    @Step("Thị trường - lọc {label}")
    public void selectFilter(String label) {
        if (!inspector.tapVisible(label)) {
            throw new AssertionError("Không thấy bộ lọc " + label);
        }
    }

    /** DATA / EMPTY / NOT_FOUND per section of the current tab (scrolls down as needed). */
    public Map<String, ContentInspector.State> sectionStates(
            List<String> sections) {
        // scroll to the top only when the first section is not on screen already (no needless swiping)
        boolean firstVisible = inspector.content().stream()
                .anyMatch(n -> n.desc().equals(sections.get(0)) || n.desc().startsWith(sections.get(0) + "\n"));
        if (!firstVisible) {
            toTop();
        }
        return inspector.sections(sections, Duration.ofSeconds(10));
    }

    /** Rows of a list tab without section headers (ETFs). */
    public List<UiNode> listRows() {
        int below = inspector.content().stream().filter(n -> n.desc().contains("trong tổng số"))
                .mapToInt(n -> n.y() + n.height()).max().orElse(0);
        return inspector.waitForRows(below, Duration.ofSeconds(10));
    }

    /**
     * Brings the chart titled {@code title} under its filter row and returns the share of coloured pixels in the
     * plot area (0 = blank chart). Charts are canvas-drawn: values are not in the accessibility tree.
     */
    public double chartColouredRatio(String title) {
        for (int i = 0; i < 6; i++) {
            Optional<UiNode> filters = inspector.content().stream()
                    .filter(n -> "Tất cả".equals(n.desc())).findFirst();
            Optional<UiNode> heading = inspector.content().stream()
                    .filter(n -> n.desc().contains(title)).findFirst();
            Dimension w = driver().manage().window().getSize();
            if (filters.isPresent() && heading.isPresent() && filters.get().y() < w.height * 0.45) {
                int top = filters.get().y() + filters.get().height() * 3; // skip the legend row
                int bottom = Math.min(top + (int) (w.height * 0.35), (int) (w.height * 0.86));
                return inspector.colouredPixelRatio(top, bottom);
            }
            GestureUtils.scrollBy(0.3);
        }
        throw new AssertionError("Không thấy biểu đồ \"" + title + "\"");
    }

    /** Index cards of "Chỉ số thị trường" (Tổng quan): label lines "VN INDEX\n1,725.36\n...". */
    public List<String> indexCards() {
        return inspector.content().stream().map(UiNode::desc)
                .filter(d -> d.matches("(?s)[A-Z0-9 ]+INDEX\n.*|VN30\n.*")).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- Tổng quan features

    private int[] window() {
        Dimension w = driver().manage().window().getSize();
        return new int[]{w.width, w.height};
    }

    /** Scrolls "Tổng quan" from the top until a node whose label starts with {@code prefix} is comfortably on screen. */
    public UiNode scrollOverviewTo(String prefix) {
        int h = window()[1];
        Optional<UiNode> here = findOnScreen(prefix, h);
        if (here.isPresent()) {
            return here.get(); // already visible: no scrolling (avoids pull-to-refresh at the top)
        }
        toTop();
        for (int i = 0; i < 14; i++) {
            Optional<UiNode> n = findOnScreen(prefix, h);
            if (n.isPresent()) {
                return settled(prefix, h, n.get());
            }
            GestureUtils.scrollBy(0.3);
        }
        throw new AssertionError("Tổng quan: không thấy \"" + prefix + "\"");
    }

    /**
     * After a scroll the list may still be gliding: a tap then only stops the scroll instead of opening the item.
     * Re-reads until the node keeps its position.
     */
    private UiNode settled(String prefix, int h, UiNode n) {
        for (int i = 0; i < 4; i++) {
            WaitUtils.pause(Duration.ofMillis(400));
            Optional<UiNode> again = findOnScreen(prefix, h);
            if (again.isEmpty()) {
                return n;
            }
            if (again.get().y() == n.y()) {
                return again.get();
            }
            n = again.get();
        }
        return n;
    }

    /** True on the home "Thị trường" screen (tab bar Tổng quan / Cổ phiếu ... visible). */
    public boolean isHome() {
        return WaitUtils.isVisible(Locators.tab("Tổng quan"), Duration.ofSeconds(1));
    }

    /**
     * Leaves a screen opened from "Thị trường". Never presses Back on the home screen itself: there it shows
     * "Bấm nút quay lại lần nữa để thoát" and a second Back closes the app.
     */
    public void backToHome() {
        if (!isHome()) {
            pressBack();
        }
    }

    private Optional<UiNode> findOnScreen(String prefix, int h) {
        return inspector.content().stream()
                .filter(x -> x.desc().startsWith(prefix) && x.height() < h / 2
                        && x.y() + x.height() / 2 > h * 0.2 && x.y() + x.height() / 2 < h * 0.8)
                .findFirst();
    }

    private void tapNode(UiNode n) {
        GestureUtils.tap(n);
    }

    /** Quick-action row ("chức năng nổi bật") scrolls horizontally: SmartOTP ... Đồ thị, Tuỳ chỉnh. */
    @Step("Thị trường - chức năng nổi bật: {label}")
    public void tapQuickAction(String label) {
        selectTab("Tổng quan");
        UiNode first = scrollOverviewTo("Tài sản ròng"); // just above the row
        quickRowToStart();
        first = scrollOverviewTo("SmartOTP");
        int y = first.y() + first.height() / 2, w = window()[0];
        for (int i = 0; i < 3; i++) {
            Optional<UiNode> n = inspector.content().stream()
                    .filter(x -> label.equals(x.desc()) && x.x() >= 0 && x.x() + x.width() <= w + 5 && x.width() > w / 6)
                    .findFirst();
            if (n.isPresent()) {
                tapNode(n.get());
                return;
            }
            GestureUtils.swipe(new Point((int) (w * 0.85), y),
                    new Point((int) (w * 0.15), y), Duration.ofMillis(400));
        }
        throw new AssertionError("Không thấy chức năng nổi bật \"" + label + "\"");
    }

    /**
     * "Cài đặt chỉ số thị trường": the "+" at the right end of the "Chỉ số thị trường" header has no accessibility
     * label; it sits just above the index cards. The header is scrolled to the upper half first, away from the
     * floating promo banner (bottom-right) that can cover it.
     */
    @Step("Thị trường - mở Cài đặt chỉ số thị trường (+)")
    public void openIndexSettings() {
        selectTab("Tổng quan");
        int[] w = window();
        UiNode card = scrollOverviewTo("VN INDEX");
        for (int i = 0; i < 3 && card.y() > w[1] * 0.5; i++) {
            GestureUtils.scrollBy(0.2);
            card = inspector.content().stream().filter(x -> x.desc().startsWith("VN INDEX")).findFirst().orElse(card);
        }
        GestureUtils.tap((int) (w[0] * 0.92), card.y() - (int) (w[1] * 0.05));
    }

    /** Opens a section's "Xem thêm" ("Tin tức\nXem thêm"), or a plain item ("Xem bản đồ nhiệt"). */
    public void openSection(String label, boolean xemThem) {
        selectTab("Tổng quan");
        tapNode(scrollOverviewTo(xemThem ? label + "\nXem thêm" : label));
    }

    /**
     * Scrolls to the very top of the current tab: swipes up until the visible content stops changing
     * (a fixed number of swipes is not enough on the long "Tổng quan" page).
     */
    private void toTop() {
        String previous = null;
        for (int i = 0; i < 10; i++) { // stops as soon as the content no longer changes
            // compare labels without digits: live prices / a refresh must not look like "still scrolling"
            String now = inspector.content().stream().map(n -> n.desc().replaceAll("[0-9.,:%+-]", ""))
                    .reduce("", String::concat);
            if (now.equals(previous)) {
                return;
            }
            previous = now;
            Dimension w = driver().manage().window().getSize();
            GestureUtils.swipe(new Point(w.width / 2, (int) (w.height * 0.3)),
                    new Point(w.width / 2, (int) (w.height * 0.8)), Duration.ofMillis(250));
        }
    }
}
