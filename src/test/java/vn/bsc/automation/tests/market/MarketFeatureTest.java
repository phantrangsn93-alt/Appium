package vn.bsc.automation.tests.market;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.openqa.selenium.By;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.TestDataReader;
import vn.bsc.automation.screens.MarketScreen;
import vn.bsc.automation.screens.PriceBoardScreen;
import vn.bsc.automation.screens.TradingScreen;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.ContentInspector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thị trường > Tổng quan features: quick actions, featured-function settings, market-index settings, heat map,
 * news / analysis / event lists. READ-ONLY: settings screens are opened and checked, never saved.
 */
@Epic("BSC Mobile Trading")
@Feature("Thị trường – tính năng")
public class MarketFeatureTest extends LoggedInBaseTest {

    private static final Duration LOAD = Duration.ofSeconds(10);
    private static final double MIN_IMAGE_RATIO = 0.003;

    private static JsonNode features() {
        return TestDataReader.read("market_data.json").get("marketFeatures");
    }

    private final ContentInspector inspector = new ContentInspector();

    private boolean visible(String label) {
        return WaitUtils.isVisible(Locators.a11y(label), LOAD);
    }

    @DataProvider(name = "quickActions")
    public Object[][] quickActions() {
        List<Object[]> rows = new ArrayList<>();
        features().get("quickActions").forEach(q -> rows.add(new Object[]{q.get("label").asText(), q.get("expect").asText()}));
        return rows.toArray(new Object[0][]);
    }

    @Test(groups = {"regression"}, dataProvider = "quickActions")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_MK_02 - Chức năng nổi bật: bấm mở đúng màn hình / chức năng")
    public void quickActionOpensTarget(String label, String expect) {
        MarketScreen m = navigator.openMarket();
        m.tapQuickAction(label);
        String kind = expect.substring(0, expect.indexOf('|'));
        String target = expect.substring(expect.indexOf('|') + 1);
        try {
            switch (kind) {
                case "title" -> assertThat(visible(target)).as(label + " -> màn \"" + target + "\"").isTrue();
                case "dialog" -> {
                    // device without screen lock: permission dialog; otherwise the SmartOTP screen itself
                    boolean dialog = visible("Cấp quyền");
                    boolean screen = !dialog && WaitUtils.isVisible(Locators.descContains(target), Duration.ofSeconds(3));
                    AllureUtils.attachScreenshot(label);
                    assertThat(dialog || screen).as(label + " -> hộp \"Cấp quyền\" hoặc màn " + target).isTrue();
                    if (dialog) {
                        new AlertDialog().tapButton("Bỏ qua");
                    }
                }
                case "priceboard" -> {
                    PriceBoardScreen board = new PriceBoardScreen();
                    assertThat(board.isDisplayed(LOAD)).as(label + " -> Bảng giá").isTrue();
                    assertThat(board.selectorLabel(0)).as(label + " -> danh mục đang chọn").isEqualTo(target);
                }
                case "sheet" -> {
                    assertThat(visible(target)).as(label + " -> \"" + target + "\"").isTrue();
                    List<UiNode> accounts = inspector.content().stream()
                            .filter(n -> n.clickable() && n.desc().matches("BSC[0-9]+")).toList();
                    assertThat(accounts).as(target + " - danh sách tài khoản nộp tiền").isNotEmpty();
                }
                case "tab" -> assertThat(new TradingScreen().isDisplayed(LOAD)).as(label + " -> Giao dịch / tab " + target).isTrue();
                case "chart" -> {
                    WaitUtils.condition(() -> false, Duration.ofSeconds(5)); // WebView chart: no UI signal to wait on
                    int h = vn.bsc.automation.core.driver.DriverManager.getDriver().manage().window().getSize().getHeight();
                    double r = inspector.colouredPixelRatio((int) (h * 0.12), (int) (h * 0.6));
                    AllureUtils.attachScreenshot(label + String.format(" (%.2f%%)", r * 100));
                    assertThat(r).as(label + " - biểu đồ nến trống").isGreaterThanOrEqualTo(MIN_IMAGE_RATIO);
                }
                default -> throw new IllegalArgumentException("Unknown expect: " + expect);
            }
            if (!"dialog".equals(kind) && !"chart".equals(kind)) {
                AllureUtils.attachScreenshot(label);
            }
        } finally {
            if (!"tab".equals(kind) && !"priceboard".equals(kind) && !"dialog".equals(kind)) {
                m.pressBack(); // leave without saving anything
            }
        }
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_MK_02b - Cài đặt chức năng nổi bật: hiển thị đủ danh sách chức năng (không lưu)")
    public void featuredFunctionSettingsListsAllItems() {
        MarketScreen m = navigator.openMarket();
        m.tapQuickAction("Tuỳ chỉnh");
        assertThat(visible("Cài đặt chức năng nổi bật")).as("Màn Cài đặt chức năng nổi bật").isTrue();
        SoftAssertions soft = new SoftAssertions();
        features().get("featuredSettingsItems").forEach(i ->
                soft.assertThat(WaitUtils.isVisible(Locators.a11y(i.asText()), Duration.ofSeconds(2))).as(i.asText()).isTrue());
        AllureUtils.attachScreenshot("Cài đặt chức năng nổi bật");
        m.pressBack();
        soft.assertAll();
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_MK_03 - Cài đặt chỉ số thị trường (+): danh sách chỉ số có dữ liệu (đóng, không bấm Xác nhận)")
    public void marketIndexSettingsListsIndices() {
        MarketScreen m = navigator.openMarket();
        m.openIndexSettings();
        boolean opened = WaitUtils.isVisible(Locators.a11y("Xác nhận"), LOAD);
        AllureUtils.attachScreenshot("Cài đặt chỉ số thị trường");
        assertThat(opened).as("Sheet chọn chỉ số (nút Xác nhận)").isTrue();
        List<UiNode> indices = inspector.content().stream()
                .filter(n -> n.clickable() && n.desc().matches("(?s)[A-Za-z0-9]+\\n.+")).toList();
        m.pressBack(); // close without confirming
        assertThat(indices).as("Danh sách chỉ số").isNotEmpty();
    }

    @DataProvider(name = "heatmapFilters")
    public Object[][] heatmapFilters() {
        List<Object[]> rows = new ArrayList<>();
        features().get("heatmap").get("filters").forEach(f -> rows.add(new Object[]{f.asText()}));
        return rows.toArray(new Object[0][]);
    }

    @Test(groups = {"regression"}, dataProvider = "heatmapFilters")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_MK_04 - Xem bản đồ nhiệt: mở Biểu đồ nhiệt khối ngoại, mỗi sàn có dữ liệu")
    public void heatmapHasData(String filter) {
        JsonNode hm = features().get("heatmap");
        MarketScreen m = navigator.openMarket();
        m.openSection(hm.get("button").asText(), false);
        try {
            assertThat(visible(hm.get("title").asText())).as("Màn " + hm.get("title").asText()).isTrue();
            assertThat(inspector.tapVisible(filter)).as("Bộ lọc " + filter).isTrue();
            WaitUtils.condition(() -> false, Duration.ofSeconds(3)); // canvas re-draw
            int h = vn.bsc.automation.core.driver.DriverManager.getDriver().manage().window().getSize().getHeight();
            double r = inspector.colouredPixelRatio((int) (h * 0.25), (int) (h * 0.85));
            AllureUtils.attachScreenshot("Bản đồ nhiệt " + filter + String.format(" (%.2f%%)", r * 100));
            assertThat(r).as("Bản đồ nhiệt " + filter + " trống").isGreaterThanOrEqualTo(MIN_IMAGE_RATIO);
        } finally {
            m.pressBack();
        }
    }

    @DataProvider(name = "listTabs")
    public Object[][] listTabs() {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode l : features().get("lists")) {
            if (l.get("tabs").isEmpty()) {
                rows.add(new Object[]{l.get("section").asText(), l.get("title").asText(), ""});
            }
            l.get("tabs").forEach(t -> rows.add(new Object[]{l.get("section").asText(), l.get("title").asText(), t.asText()}));
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(groups = {"regression"}, dataProvider = "listTabs")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_MK_05/06/07 - Tin tức / Phân tích / Lịch sự kiện > Xem thêm: mỗi tab có dữ liệu (trống => FAIL)")
    public void overviewListTabHasData(String section, String title, String tab) {
        MarketScreen m = navigator.openMarket();
        m.openSection(section, true);
        try {
            assertThat(visible(title)).as("Màn " + title).isTrue();
            if (!tab.isEmpty()) {
                By t = Locators.tab(tab);
                if (!WaitUtils.isVisible(t, Duration.ofSeconds(2))) {
                    // scrollable tab bar: reveal tabs on the right
                    int w = vn.bsc.automation.core.driver.DriverManager.getDriver().manage().window().getSize().getWidth();
                    UiNode bar = inspector.content().stream().filter(n -> n.desc().contains("trong tổng số")).findFirst().orElseThrow();
                    vn.bsc.automation.core.utils.GestureUtils.swipe(new org.openqa.selenium.Point((int) (w * 0.85), bar.y() + bar.height() / 2),
                            new org.openqa.selenium.Point((int) (w * 0.15), bar.y() + bar.height() / 2), Duration.ofMillis(400));
                }
                inspector.content().stream().filter(n -> n.desc().startsWith(tab + "\n") && n.desc().contains("trong tổng số"))
                        .findFirst().ifPresent(n -> vn.bsc.automation.core.utils.GestureUtils.tap(n.x() + n.width() / 2, n.y() + n.height() / 2));
            }
            int below = inspector.content().stream()
                    .filter(n -> n.desc().contains("trong tổng số") || n.desc().equals(title))
                    .mapToInt(n -> n.y() + n.height()).max().orElse(0);
            List<UiNode> rows = inspector.waitForRows(below, LOAD);
            String label = title + (tab.isEmpty() ? "" : " / " + tab);
            AllureUtils.attachScreenshot(label);
            assertThat(rows).as(label + ": không có dữ liệu").isNotEmpty();
        } finally {
            m.pressBack();
        }
    }
}
