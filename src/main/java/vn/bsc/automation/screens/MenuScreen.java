package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.screens.components.AlertDialog;

import java.util.Comparator;

/** Hamburger menu: profile + logout icon + feature groups. */
public class MenuScreen extends BaseScreen {

    public static final String[] ITEMS = {
            "Giao dịch tiền", "Giao dịch chứng khoán", "Tiện ích", "Quản lý tài khoản",
            "Bảo mật", "Cài đặt", "Hỗ trợ", "Ý kiến đóng góp", "Thông tin phần mềm", "BSC Mtrader"
    };

    private final By titleBar = Locators.classWithDesc("android.widget.Button", "Menu");
    private final By descLessImages = By.xpath("//android.widget.ImageView[@clickable='true' and not(@content-desc)]"
            + " | //android.widget.ImageView[@clickable='true' and @content-desc='']");

    @Override
    protected By anchor() {
        return titleBar;
    }

    /** Guest mode: big "Đăng nhập" button at the top of the menu. */
    @Step("Chạm vào nút Đăng nhập (Menu)")
    public void tapLogin() {
        tap(Locators.classWithDesc("android.widget.Button", "Đăng nhập"));
    }

    public boolean isGuestMenu() {
        return isVisible(Locators.classWithDesc("android.widget.Button", "Đăng nhập"));
    }

    public boolean isItemVisible(String item) {
        return isVisibleWithScroll(Locators.a11y(item));
    }

    /** True when any child row of the group is shown (groups are accordions: one tap expands, next collapses). */
    public boolean isGroupExpanded(MenuItem.Group group) {
        String[] children = java.util.Arrays.stream(MenuItem.values())
                .filter(i -> i.group == group).map(i -> i.label).toArray(String[]::new);
        return isVisible(Locators.descMatchesAny(children), java.time.Duration.ofMillis(800));
    }

    /**
     * Opens a menu entry: expands its group if needed, scrolls it fully into view and taps it.
     * Returns nothing - callers continue with the screen object matching {@link MenuItem#kind}
     * ({@link vn.bsc.automation.screens.components.ActionSheet} for SHEET, {@link TransactionFormScreen} /
     * {@link MenuTargetScreen} for SCREEN).
     */
    @Step("Menu > {item}")
    public void open(MenuItem item) {
        if (item == MenuItem.TONG_DAI) {
            throw new IllegalArgumentException("'Tổng đài' places a real phone call - not automated");
        }
        if (item.group != null && !isGroupExpanded(item.group)) {
            tap(scrollFullyIntoView(Locators.a11y(item.group.label)));
        }
        By target = scrollFullyIntoView(Locators.a11y(item.label));
        waitUntilStill(target); // a tap during the expand animation is ignored by Flutter
        tap(target);
    }

    /** Waits until the element stops moving (two consecutive reads 300 ms apart give the same position). */
    private void waitUntilStill(By by) {
        Rectangle[] last = {null};
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> {
            Rectangle now = find(by).getRect();
            boolean still = now.equals(last[0]);
            last[0] = now;
            return still;
        }, java.time.Duration.ofSeconds(5));
    }

    /**
     * Scrolls until the element is visible AND its centre lies in the upper 85% of the window: a menu row cut by
     * the bottom edge reports its centre under the Android navigation bar, where a tap presses system HOME.
     */
    private By scrollFullyIntoView(By by) {
        org.openqa.selenium.Dimension win = driver().manage().window().getSize();
        for (int i = 0; i < 6; i++) {
            if (isVisible(by, java.time.Duration.ofSeconds(1))) {
                Rectangle r = find(by).getRect();
                if (r.getY() + r.getHeight() / 2 < win.getHeight() * 0.85) {
                    return by;
                }
            }
            GestureUtils.scrollDown();
        }
        throw new AssertionError("Menu entry not reachable: " + by);
    }

    public boolean showsProfile(String username) {
        return isVisible(Locators.descContains(username));
    }

    /** The "X" icon is drawn at the right end of the title bar (no own semantics node). */
    @Step("Đóng menu")
    public void close() {
        Rectangle r = find(titleBar).getRect();
        // X icon sits half a bar-height from the right edge (relative -> works on any density)
        GestureUtils.tap(r.getX() + r.getWidth() - r.getHeight() / 2, r.getY() + r.getHeight() / 2);
    }

    /**
     * Logout icon has no content-desc: it is the description-less ImageView on the profile row
     * (right-most clickable image directly below the title bar).
     */
    private WebElement logoutIcon() {
        org.openqa.selenium.Rectangle title = find(titleBar).getRect();
        int titleBottom = title.getY() + title.getHeight();
        int rowLimit = titleBottom + 3 * title.getHeight(); // relative to title height -> density independent
        return findAll(descLessImages).stream()
                .filter(e -> e.getRect().getY() > titleBottom && e.getRect().getY() < rowLimit)
                .max(Comparator.comparingInt(e -> e.getRect().getX()))
                .orElseThrow(() -> new AssertionError("Logout icon not found on menu"));
    }

    @Step("Bấm icon Đăng xuất")
    public AlertDialog tapLogout() {
        GestureUtils.tapCenter(logoutIcon());
        return new AlertDialog().waitUntilLoaded();
    }
}
