package vn.bsc.automation.tests.navigation;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.screens.MarketScreen;
import vn.bsc.automation.screens.MenuScreen;
import vn.bsc.automation.screens.NotificationScreen;
import vn.bsc.automation.screens.TradingScreen;
import vn.bsc.automation.screens.TradingScreen.Tab;
import vn.bsc.automation.screens.components.AppHeader;
import vn.bsc.automation.screens.components.BottomNavBar;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("BSC Mobile Trading")
@Feature("Điều hướng sau đăng nhập")
public class NavigationTest extends LoggedInBaseTest {

    @Test(groups = {"smoke", "regression"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_NAV_01 - Bottom navigation mở đúng 5 màn hình chính")
    public void bottomNavigationOpensAllMainScreens() {
        assertThat(navigator.openPriceBoard().isDisplayed()).as("Bảng giá").isTrue();
        assertThat(navigator.openTrading().isDisplayed()).as("Giao dịch").isTrue();
        assertThat(navigator.openSearch().isDisplayed()).as("Tìm kiếm").isTrue();
        assertThat(navigator.openNotifications().isDisplayed()).as("Thông báo").isTrue();
        assertThat(navigator.openMarket().isDisplayed()).as("Thị trường").isTrue();
    }

    @Test(groups = {"smoke", "regression"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_NAV_02 - Header hiển thị tài khoản đang đăng nhập")
    public void headerShowsLoggedInAccount() {
        requireCredentials();
        assertThat(new AppHeader().showsAccount(AppConfig.username())).isTrue();
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_NAV_03 - Màn Thị trường: 4 tab và các tiện ích nhanh")
    public void marketScreenTabsAndQuickActions() {
        MarketScreen market = navigator.openMarket();
        SoftAssertions soft = new SoftAssertions();
        for (String action : MarketScreen.QUICK_ACTIONS) {
            soft.assertThat(market.isQuickActionVisible(action)).as("Tiện ích " + action).isTrue();
        }
        for (String tab : MarketScreen.TABS) {
            market.openTab(tab);
            soft.assertThat(market.isTabSelected(tab)).as("Tab " + tab + " được chọn").isTrue();
        }
        market.openTab(MarketScreen.TABS[0]);
        soft.assertAll();
    }

    @Test(groups = {"smoke", "regression"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_NAV_04 - Màn Giao dịch: chuyển qua 4 tab Đặt lệnh / Sổ lệnh / Danh mục / Tài sản")
    public void tradingTabsSwitchCorrectly() {
        TradingScreen trading = navigator.openTrading();
        SoftAssertions soft = new SoftAssertions();

        soft.assertThat(trading.openOrderBook().isDisplayed()).as("Sổ lệnh").isTrue();
        soft.assertThat(trading.isTabSelected(Tab.ORDER_BOOK)).isTrue();

        soft.assertThat(trading.openPortfolio().isDisplayed()).as("Danh mục").isTrue();
        soft.assertThat(trading.isTabSelected(Tab.PORTFOLIO)).isTrue();

        soft.assertThat(trading.openAsset().isDisplayed()).as("Tài sản").isTrue();
        soft.assertThat(trading.isTabSelected(Tab.ASSET)).isTrue();

        soft.assertThat(trading.openOrderForm().isDisplayed()).as("Đặt lệnh").isTrue();
        soft.assertThat(trading.isTabSelected(Tab.PLACE_ORDER)).isTrue();
        soft.assertAll();
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_NAV_05 - Màn Thông báo: chuyển qua 4 tab")
    public void notificationTabsSwitchCorrectly() {
        NotificationScreen noti = navigator.openNotifications();
        SoftAssertions soft = new SoftAssertions();
        for (String tab : NotificationScreen.TABS) {
            noti.openTab(tab);
            soft.assertThat(noti.isTabSelected(tab)).as("Tab " + tab).isTrue();
        }
        noti.openTab(NotificationScreen.TABS[0]);
        soft.assertAll();
    }

    @Test(groups = {"smoke", "regression"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_NAV_06 - Menu hiển thị thông tin tài khoản, đủ 9 nhóm chức năng và đóng được")
    public void menuShowsProfileAndAllItems() {
        MenuScreen menu = new AppHeader().openMenu();
        SoftAssertions soft = new SoftAssertions();
        if (AppConfig.hasCredentials()) {
            soft.assertThat(menu.showsProfile(AppConfig.username())).as("Profile").isTrue();
        }
        for (String item : MenuScreen.ITEMS) {
            soft.assertThat(menu.isItemVisible(item)).as("Menu item " + item).isTrue();
        }
        menu.close();
        soft.assertThat(new BottomNavBar().isDisplayed()).as("Đóng menu về màn hình chính").isTrue();
        soft.assertAll();
    }
}
