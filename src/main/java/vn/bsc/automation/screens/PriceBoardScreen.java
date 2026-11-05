package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.IndexCard;
import vn.bsc.automation.data.StockRow;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/** "Bảng giá": watch-list selector, index cards, stock rows, news. */
public class PriceBoardScreen extends BaseScreen {

    private final By sortByVolume = Locators.a11y("Khối lượng");
    private final By sortByPrice = Locators.a11y("Giá");
    private final By stockRows = By.xpath("//android.view.View[contains(@content-desc,'|') and "
            + "(contains(@content-desc,'HOSE') or contains(@content-desc,'HNX') or contains(@content-desc,'UPCOM'))]");

    @Override
    protected By anchor() {
        return sortByVolume;
    }

    public boolean isWatchlistSelectorVisible(String name) {
        return isVisible(Locators.a11y(name));
    }

    public IndexCard getIndexCard(String indexName) {
        return IndexCard.parse(getDesc(Locators.descStartsWith(indexName)));
    }

    public List<StockRow> getStockRows() {
        WaitUtils.allPresent(stockRows, Duration.ofSeconds(15));
        return findAll(stockRows).stream()
                .map(this::getDesc)
                .map(StockRow::parse)
                .collect(Collectors.toList());
    }

    public boolean isSortOptionVisible(String label) {
        return isVisible(Locators.a11y(label));
    }

    public void sortByPrice() {
        tap(sortByPrice);
    }

    public void sortByVolume() {
        tap(sortByVolume);
    }

    public boolean isNewsSectionVisible() {
        return isVisible(Locators.descStartsWith("Tin tức"), Duration.ofSeconds(10));
    }
}
