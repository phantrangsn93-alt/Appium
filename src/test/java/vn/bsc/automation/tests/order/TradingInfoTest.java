package vn.bsc.automation.tests.order;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.screens.AssetScreen;
import vn.bsc.automation.screens.OrderBookScreen;
import vn.bsc.automation.screens.PortfolioScreen;

/** Read-only checks of Sổ lệnh / Danh mục / Tài sản. No cancel / modify action is performed. */
@Epic("BSC Mobile Trading")
@Feature("Sổ lệnh - Danh mục - Tài sản (chỉ đọc)")
public class TradingInfoTest extends LoggedInBaseTest {

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_INFO_01 - Sổ lệnh: tổng hợp và bộ lọc trạng thái")
    public void orderBookShowsSummaryAndFilters() {
        OrderBookScreen book = navigator.openTrading().openOrderBook();
        SoftAssertions soft = new SoftAssertions();
        String summary = book.getSummaryText();
        for (String label : OrderBookScreen.SUMMARY_LABELS) {
            soft.assertThat(summary).as("Tổng hợp").contains(label);
        }
        for (String f : OrderBookScreen.FILTERS) {
            soft.assertThat(book.isFilterVisible(f)).as("Bộ lọc " + f).isTrue();
        }
        soft.assertThat(book.isCancelAllButtonVisible()).as("Nút Huỷ tất cả hiển thị (không bấm)").isTrue();
        soft.assertAll();
    }

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_INFO_02 - Danh mục: tổng giá trị vốn / thị trường / lãi lỗ")
    public void portfolioShowsSummary() {
        PortfolioScreen p = navigator.openTrading().openPortfolio();
        SoftAssertions soft = new SoftAssertions();
        String summary = p.getSummaryText();
        for (String label : PortfolioScreen.SUMMARY_LABELS) {
            soft.assertThat(summary).contains(label);
        }
        soft.assertThat(p.isChartButtonVisible()).as("Xem biểu đồ").isTrue();
        soft.assertAll();
    }

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_INFO_03 - Tài sản: NAV, kỳ hiệu suất và cơ cấu tài sản")
    public void assetShowsNavAndBreakdown() {
        AssetScreen a = navigator.openTrading().openAsset();
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(a.getSummaryText()).contains("Giá trị tài sản ròng").contains("Trạng thái tiểu khoản");
        for (String period : AssetScreen.PERIODS) {
            soft.assertThat(a.isPeriodVisible(period)).as("Kỳ " + period).isTrue();
        }
        for (String item : AssetScreen.BREAKDOWN_ITEMS) {
            soft.assertThat(a.isBreakdownItemVisible(item)).as(item).isTrue();
        }
        soft.assertAll();
    }
}
