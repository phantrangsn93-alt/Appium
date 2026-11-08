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
        return vn.bsc.automation.core.utils.Locators.xpath("//android.view.View[starts-with(@content-desc,'" + symbol + "\n') and contains(@content-desc,'|')]");
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

    @Step("Tìm kiếm - nhóm {category}")
    public SearchScreen openCategory(String category) {
        if (!new vn.bsc.automation.screens.components.ContentInspector().tapVisible(category)) {
            throw new AssertionError("Không thấy nhóm " + category);
        }
        return this;
    }

    /** Clears the box and types a new keyword. */
    @Step("Tìm kiếm: {keyword} (xoá từ khoá cũ)")
    public SearchScreen searchFresh(String keyword) {
        org.openqa.selenium.WebElement el = find(input);
        vn.bsc.automation.core.driver.DeviceActions.tap(el);
        vn.bsc.automation.core.driver.DeviceActions.clear(el);
        el.sendKeys(keyword);
        return this;
    }

    /**
     * Labels of the results listed under the category chips, waiting up to {@code timeout} for at least one.
     * Empty list = no result.
     */
    public java.util.List<String> results(Duration timeout) {
        vn.bsc.automation.screens.components.ContentInspector ci = new vn.bsc.automation.screens.components.ContentInspector();
        java.util.List<String> out = new java.util.ArrayList<>();
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> {
            out.clear();
            java.util.List<vn.bsc.automation.core.utils.UiNode> nodes = ci.content();
            int chipsBottom = nodes.stream().filter(n -> java.util.Arrays.asList(CATEGORIES).contains(n.desc()))
                    .mapToInt(n -> n.y() + n.height()).max().orElse(0);
            nodes.stream().filter(n -> n.clickable() && n.y() >= chipsBottom
                            && !java.util.Arrays.asList(CATEGORIES).contains(n.desc()) && !n.desc().startsWith("Tìm kiếm"))
                    .map(vn.bsc.automation.core.utils.UiNode::desc).forEach(out::add);
            return !out.isEmpty();
        }, timeout);
        return out;
    }
}
