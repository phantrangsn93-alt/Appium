package vn.bsc.automation.tests.market;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.TestDataReader;
import vn.bsc.automation.screens.MarketScreen;
import vn.bsc.automation.screens.components.ContentInspector.State;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Thị trường: every tab / exchange filter / section must show data (empty => FAIL). Data: market_data.json. */
@Epic("BSC Mobile Trading")
@Feature("Thị trường")
public class MarketDataTest extends LoggedInBaseTest {

    /** Minimum share of coloured pixels for a chart to count as "has data" (blank plot ~0%, bars > 1%). */
    private static final double MIN_CHART_RATIO = 0.003;

    /** One row per tab (its exchange filters walked inside the test, without leaving the tab). */
    @DataProvider(name = "tabs")
    public Object[][] tabs() {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode t : TestDataReader.read("market_data.json").get("market")) {
            List<String> sections = new ArrayList<>();
            t.path("sections").forEach(s -> sections.add(s.asText()));
            List<String> filters = new ArrayList<>();
            t.path("filters").forEach(f -> filters.add(f.asText()));
            rows.add(new Object[]{t.get("tab").asText(), filters, sections, t.path("chart").asText("")});
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(groups = {"regression"}, dataProvider = "tabs")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_MK_01 - Thị trường: mỗi tab / bộ lọc sàn / mục phải có dữ liệu (trống hoặc \"Không có dữ liệu\" => FAIL). Mở tab 1 lần, đổi lần lượt từng sàn")
    public void everyMarketTabHasData(String tab, List<String> filters, List<String> sections, String chart) {
        MarketScreen m = navigator.openMarket();
        m.selectTab(tab);
        SoftAssertions soft = new SoftAssertions();
        List<String> runs = filters.isEmpty() ? List.of("") : filters;
        if (!chart.isEmpty()) {
            soft.assertThat(m.indexCards()).as(tab + " - thẻ chỉ số thị trường").isNotEmpty();
            m.chartColouredRatio(chart); // scrolls the chart and its filter row into view ONCE
        }
        for (String filter : runs) {
            String label = tab + (filter.isEmpty() ? "" : " / " + filter);
            try {
                if (!chart.isEmpty()) {
                    if (!filter.isEmpty()) {
                        m.selectFilter(filter);
                        WaitUtils.pause(Duration.ofSeconds(3)); // chart re-draw (no UI signal to wait on)
                    }
                    double ratio = m.chartColouredRatio(chart);
                    AllureUtils.attachScreenshot("Thị trường - " + label + " - biểu đồ (" + String.format("%.2f%%", ratio * 100) + ")");
                    soft.assertThat(ratio).as(label + " - biểu đồ \"" + chart + "\" trống").isGreaterThanOrEqualTo(MIN_CHART_RATIO);
                } else {
                    if (!filter.isEmpty()) {
                        m.selectFilter(filter);
                    }
                    if (sections.isEmpty()) {
                        soft.assertThat(m.listRows()).as(label + " - danh sách trống").isNotEmpty();
                        AllureUtils.attachScreenshot("Thị trường - " + label);
                    } else {
                        AllureUtils.attachScreenshot("Thị trường - " + label);
                        Map<String, State> states = m.sectionStates(sections);
                        AllureUtils.attachScreenshot("Thị trường - " + label + " (cuối trang)");
                        states.forEach((section, state) ->
                                soft.assertThat(state).as(label + " - mục \"" + section + "\"").isEqualTo(State.DATA));
                    }
                }
            } catch (AssertionError e) {
                soft.fail(label + ": " + e.getMessage()); // keep checking the other exchanges
            }
        }
        soft.assertAll();
    }

}
