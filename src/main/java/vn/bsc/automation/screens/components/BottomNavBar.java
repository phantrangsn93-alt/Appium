package vn.bsc.automation.screens.components;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

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
        // an unread badge is prepended to the label: "6\nThông báo"
        return Locators.classWithDescContaining("android.widget.ImageView", item.label());
    }

    @Override
    protected By anchor() {
        return item(Item.TRADING);
    }

    public void open(Item item) {
        hideKeyboard();
        tap(item(item));
    }

    public boolean isItemVisible(Item item) {
        return isVisible(item(item));
    }
}
