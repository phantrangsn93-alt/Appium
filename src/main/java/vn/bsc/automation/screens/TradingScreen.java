package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

/** "Giao dịch" container with 4 tabs: Đặt lệnh / Sổ lệnh / Danh mục / Tài sản. */
public class TradingScreen extends BaseScreen {

    public enum Tab {
        PLACE_ORDER("Đặt lệnh"),
        ORDER_BOOK("Sổ lệnh"),
        PORTFOLIO("Danh mục"),
        ASSET("Tài sản");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Override
    protected By anchor() {
        return Locators.tab(Tab.PLACE_ORDER.label());
    }

    /**
     * Opening "Giao dịch" may raise an error for the current sub-account (seen during back-end batch:
     * "Lỗi :ACCOUNTID_NOT_EXIT"). It is recorded in the report and closed so the flow can switch sub-account.
     */
    public void dismissTransientError() {
        vn.bsc.automation.screens.components.AlertDialog d = new vn.bsc.automation.screens.components.AlertDialog();
        if (d.isShown(java.time.Duration.ofSeconds(1)) && d.hasButton(vn.bsc.automation.screens.components.AlertDialog.BTN_CLOSE)) {
            String msg = d.getMessage();
            log.warn("App error on Giao dịch: {}", msg);
            vn.bsc.automation.core.utils.AllureUtils.attachText("Cảnh báo: lỗi khi mở Giao dịch", msg);
            d.close();
        }
    }

    public void openTab(Tab tab) {
        hideKeyboard();
        tapTab(Locators.tab(tab.label()));
    }

    public boolean isTabSelected(Tab tab) {
        return isSelected(Locators.tab(tab.label()));
    }

    public OrderFormScreen openOrderForm() {
        dismissTransientError();
        openTab(Tab.PLACE_ORDER);
        // The app keeps the form's scroll position between visits -> always start from the top
        return new OrderFormScreen().exitModifyMode().scrollToTop().waitUntilLoaded();
    }

    public OrderBookScreen openOrderBook() {
        openTab(Tab.ORDER_BOOK);
        return new OrderBookScreen().waitUntilLoaded();
    }

    public PortfolioScreen openPortfolio() {
        openTab(Tab.PORTFOLIO);
        return new PortfolioScreen().waitUntilLoaded();
    }

    public AssetScreen openAsset() {
        openTab(Tab.ASSET);
        return new AssetScreen().waitUntilLoaded();
    }
}
