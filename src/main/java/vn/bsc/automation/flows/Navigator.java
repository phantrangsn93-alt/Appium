package vn.bsc.automation.flows;

import io.qameta.allure.Step;
import vn.bsc.automation.screens.MarketScreen;
import vn.bsc.automation.screens.NotificationScreen;
import vn.bsc.automation.screens.PriceBoardScreen;
import vn.bsc.automation.screens.SearchScreen;
import vn.bsc.automation.screens.TradingScreen;
import vn.bsc.automation.screens.components.BottomNavBar;
import vn.bsc.automation.screens.components.BottomNavBar.Item;

/** Navigation through bottom bar; returns loaded screen objects. */
public class Navigator {

    private final BottomNavBar bottomNav = new BottomNavBar();

    @Step("Mở tab Thị trường")
    public MarketScreen openMarket() {
        bottomNav.open(Item.MARKET);
        return new MarketScreen().waitUntilLoaded();
    }

    @Step("Mở tab Bảng giá")
    public PriceBoardScreen openPriceBoard() {
        bottomNav.open(Item.PRICE_BOARD);
        return new PriceBoardScreen().waitUntilLoaded();
    }

    @Step("Mở tab Giao dịch")
    public TradingScreen openTrading() {
        bottomNav.open(Item.TRADING);
        return new TradingScreen().waitUntilLoaded();
    }

    @Step("Mở tab Tìm kiếm")
    public SearchScreen openSearch() {
        bottomNav.open(Item.SEARCH);
        return new SearchScreen().waitUntilLoaded();
    }

    @Step("Mở tab Thông báo")
    public NotificationScreen openNotifications() {
        bottomNav.open(Item.NOTIFICATION);
        return new NotificationScreen().waitUntilLoaded();
    }
}
