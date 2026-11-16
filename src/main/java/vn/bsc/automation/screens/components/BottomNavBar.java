package vn.bsc.automation.screens.components;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.driver.DeviceActions;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.WaitUtils;

import java.time.Duration;

/** Bottom navigation bar shown on every main screen after login. */
public class BottomNavBar extends BaseScreen {

    public enum Item {
        MARKET("Thị trường"),
        PRICE_BOARD("Bảng giá"),
        TRADING("Giao dịch"),
        SEARCH("Tìm kiếm"),
        NOTIFICATION("Thông báo");

        private final String label;

        Item(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static By item(Item item) {
        // an unread badge may precede the label ("6\nThông báo"); exact label otherwise - "contains" also matched
        // "Giao dịch qua Tổng đài" on sub-screens (iOS: any element type)
        return Locators.classWithDescBadged("android.widget.ImageView", item.label());
    }

    @Override
    protected By anchor() {
        return item(Item.TRADING);
    }

    public void open(Item item) {
        hideKeyboard();
        if (!WaitUtils.isVisible(item(item), Duration.ofSeconds(1))) {
            // an auto-focused field (Tìm kiếm) opens the keyboard a moment after the screen loads and covers the bar
            WaitUtils.condition(DeviceActions::isKeyboardShown, Duration.ofSeconds(2));
            hideKeyboard();
        }
        tap(item(item));
    }

    public boolean isItemVisible(Item item) {
        return isVisible(item(item));
    }
}
