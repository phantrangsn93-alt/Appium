package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

/** "Tài sản" - NAV, performance, asset breakdown. */
public class AssetScreen extends BaseScreen {

    public static final String[] BREAKDOWN_ITEMS = {"Tiền tại BSC", "Phải thu", "Chứng khoán", "Phải trả", "Thặng dư ký quỹ"};
    public static final String[] PERIODS = {"1M", "3M", "6M", "1Y", "YTD"};

    private final By summary = Locators.descStartsWith("Giá trị tài sản ròng");

    @Override
    protected By anchor() {
        return summary;
    }

    public String getSummaryText() {
        return getDesc(summary);
    }

    public boolean isBreakdownItemVisible(String item) {
        return isVisible(Locators.descStartsWith(item));
    }

    public boolean isPeriodVisible(String period) {
        return isVisible(Locators.a11y(period));
    }
}
