package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

/** "Thị trường" - home screen after login. */
public class MarketScreen extends BaseScreen {

    public static final String[] TABS = {"Tổng quan", "Cổ phiếu", "Phái sinh", "Chứng quyền"};
    public static final String[] QUICK_ACTIONS = {"Smart OTP", "Mở tài khoản", "Khuyến nghị", "Nộp tiền"};

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
        return isVisible(Locators.a11y(name));
    }

    /** e.g. "VN INDEX\n1,740.73\n+7.71 (+0.44%)\n15,829.8 (Tỷ VND)" */
    public String getIndexCard(String indexName) {
        return getDesc(Locators.descStartsWith(indexName));
    }
}
