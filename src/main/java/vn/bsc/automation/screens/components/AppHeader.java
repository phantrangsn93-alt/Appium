package vn.bsc.automation.screens.components;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.screens.MenuScreen;

/** Top header: "<Customer name> - <custody code>" / "<sub-account>" + hamburger menu. */
public class AppHeader extends BaseScreen {

    private final By menuButton = Locators.a11y("Mở menu di chuyển");

    @Override
    protected By anchor() {
        return menuButton;
    }

    /** True when the header shows the given custody code (username), e.g. 002C123456. */
    public boolean showsAccount(String username) {
        return isVisible(Locators.descContains(username));
    }

    /** "Nguyễn Văn A - 002C123456\nMR90_BSC - 0001000007" (logged in) or "BIDV Securities" (guest). */
    public String getAccountText() {
        return getDesc(org.openqa.selenium.By.xpath(
                "(//*[@content-desc='Mở menu di chuyển']/preceding::*[string-length(@content-desc)>0])[1]"));
    }

    public MenuScreen openMenu() {
        hideKeyboard();
        tap(menuButton);
        return new MenuScreen().waitUntilLoaded();
    }
}
