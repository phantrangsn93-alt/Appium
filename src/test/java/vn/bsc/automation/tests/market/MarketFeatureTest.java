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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.openqa.selenium.Point;
import vn.bsc.automation.core.driver.DriverManager;
import vn.bsc.automation.core.utils.GestureUtils;

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
                    WaitUtils.pause(Duration.ofSeconds(5)); // WebView chart: no UI signal to wait on
                    int h = DriverManager.getDriver().manage().window().getSize().getHeight();
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
                m.backToHome(); // leave without saving anything
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

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_MK_04 - Xem bản đồ nhiệt: mở Biểu đồ nhiệt khối ngoại MỘT lần, đổi lần lượt từng sàn, mỗi sàn có dữ liệu")
    public void heatmapHasData() {
        JsonNode hm = features().get("heatmap");
        MarketScreen m = navigator.openMarket();
        m.openSection(hm.get("button").asText(), false);
        try {
            assertThat(visible(hm.get("title").asText())).as("Màn " + hm.get("title").asText()).isTrue();
            SoftAssertions soft = new SoftAssertions();
            int h = DriverManager.getDriver().manage().window().getSize().getHeight();
            for (JsonNode f : hm.get("filters")) {
                String filter = f.asText();
                if (!inspector.tapVisible(filter)) {
                    soft.fail("Không thấy bộ lọc " + filter);
                    continue;
                }
                WaitUtils.pause(Duration.ofSeconds(3)); // canvas re-draw: no UI signal to wait on
                double r = inspector.colouredPixelRatio((int) (h * 0.25), (int) (h * 0.85));
                AllureUtils.attachScreenshot("Bản đồ nhiệt " + filter + String.format(" (%.2f%%)", r * 100));
                soft.assertThat(r).as("Bản đồ nhiệt " + filter + " trống").isGreaterThanOrEqualTo(MIN_IMAGE_RATIO);
            }
            soft.assertAll();
        } finally {
            m.backToHome();
        }
    }

    @DataProvider(name = "listSections")
    public Object[][] listSections() {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode l : features().get("lists")) {
            List<String> tabs = new ArrayList<>();
            l.get("tabs").forEach(t -> tabs.add(t.asText()));
            rows.add(new Object[]{l.get("section").asText(), l.get("title").asText(), tabs});
        }
        return rows.toArray(new Object[0][]);
    }

    /**
     * Opens the section's "Xem thêm" ONCE and walks its tabs there (one screenshot + check per tab), instead of
     * re-opening the list from the market screen for every tab.
     */
    @Test(groups = {"regression"}, dataProvider = "listSections")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_MK_05/06/07 - Tin tức / Phân tích / Lịch sự kiện > Xem thêm: mở 1 lần, mỗi tab có dữ liệu (trống => FAIL)")
    public void overviewListTabHasData(String section, String title, List<String> tabs) {
        MarketScreen m = navigator.openMarket();
        m.openSection(section, true);
        try {
            assertThat(visible(title)).as("Màn " + title).isTrue();
            SoftAssertions soft = new SoftAssertions();
            if (tabs.isEmpty()) {
                checkListRows(soft, title, title);
            }
            for (String tab : tabs) {
                if (!openListTab(tab)) {
                    soft.fail(title + ": không thấy tab " + tab);
                    continue;
                }
                checkListRows(soft, title, title + " / " + tab);
            }
            soft.assertAll();
        } finally {
            m.backToHome();
        }
    }

    /** Taps a tab of the list screen; the tab bar scrolls, so an off-screen tab is revealed by swiping it left. */
    private boolean openListTab(String tab) {
        for (int i = 0; i < 3; i++) {
            Optional<UiNode> t = inspector.content().stream()
                    .filter(n -> n.desc().startsWith(tab + "\n") && n.desc().contains("trong tổng số"))
                    .findFirst();
            int w = DriverManager.getDriver().manage().window().getSize().getWidth();
            if (t.isPresent() && t.get().x() >= 0 && t.get().x() + t.get().width() <= w + 2) {
                GestureUtils.tap(t.get());
                return true;
            }
            UiNode bar = inspector.content().stream().filter(n -> n.desc().contains("trong tổng số")).findFirst().orElse(null);
            if (bar == null) {
                return false;
            }
            GestureUtils.swipe(new Point((int) (w * 0.85), bar.centerY()), new Point((int) (w * 0.15), bar.centerY()),
                    Duration.ofMillis(400));
        }
        return false;
    }

    private void checkListRows(SoftAssertions soft, String title, String label) {
        int below = inspector.content().stream()
                .filter(n -> n.desc().contains("trong tổng số") || n.desc().equals(title))
                .mapToInt(n -> n.y() + n.height()).max().orElse(0);
        List<UiNode> rows = inspector.waitForRows(below, LOAD);
        AllureUtils.attachScreenshot(label);
        soft.assertThat(rows).as(label + ": không có dữ liệu").isNotEmpty();
    }

}
