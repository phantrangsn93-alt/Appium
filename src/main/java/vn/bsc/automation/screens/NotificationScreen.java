package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

/** "Thông báo": tabs Giao dịch / Khuyến nghị / Cảnh báo / Khác. */
public class NotificationScreen extends BaseScreen {

    public static final String[] TABS = {"Giao dịch", "Khuyến nghị", "Cảnh báo", "Khác"};

    @Override
    protected By anchor() {
        return Locators.tab("Cảnh báo");
    }

    public void openTab(String name) {
        tapTab(Locators.tab(name));
    }

    public boolean isTabSelected(String name) {
        return isSelected(Locators.tab(name));
    }

    public boolean isTabVisible(String name) {
        return isVisible(Locators.tab(name));
    }
}
