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
            "Bảo mật", "Cài đặt", "Hỗ trợ", "Ý kiến đóng góp", "Thông tin phần mềm"
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
