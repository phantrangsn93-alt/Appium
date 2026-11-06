package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

import java.time.Duration;

/** "Tìm kiếm": global search with categories Chứng khoán / Tin tức / Chức năng. */
public class SearchScreen extends BaseScreen {

    public static final String[] CATEGORIES = {"Chứng khoán", "Tin tức", "Chức năng"};

    private final By input = Locators.editText(0);

    @Override
    protected By anchor() {
        return Locators.a11y("Chức năng");
    }

    private static By stockResult(String symbol) {
        return By.xpath("//android.view.View[starts-with(@content-desc,'" + symbol + "\n') and contains(@content-desc,'|')]");
    }

    @Step("Tìm kiếm: {keyword}")
    public SearchScreen search(String keyword) {
        type(input, keyword);
        return this;
    }

    public boolean isStockResultVisible(String symbol) {
        return isVisible(stockResult(symbol), Duration.ofSeconds(10));
    }

    public String getStockResultText(String symbol) {
        return getDesc(stockResult(symbol));
    }

    /** Taps a stock result ("MÃ\n | SÀN\nTên") and returns its detail screen. */
    @Step("Mở chi tiết mã {symbol}")
    public StockDetailScreen openStock(String symbol) {
        hideKeyboard();
        tap(stockResult(symbol));
        return new StockDetailScreen().waitUntilLoaded();
    }

    public boolean isCategoryVisible(String category) {
        return isVisible(Locators.a11y(category));
    }

    public String getInputText() {
        return getText(input);
    }
}
