package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

/** "Danh mục" - holdings summary. */
public class PortfolioScreen extends BaseScreen {

    public static final String[] SUMMARY_LABELS = {"Tổng giá trị vốn", "Tổng giá trị thị trường", "Lãi/Lỗ"};

    private final By summary = Locators.descStartsWith("Tổng giá trị vốn");

    @Override
    protected By anchor() {
        return summary;
    }

    public String getSummaryText() {
        return getDesc(summary);
    }

    public boolean isChartButtonVisible() {
        return isVisible(Locators.a11y("Xem biểu đồ"));
    }
}
