package vn.bsc.automation.tests.menu;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.appium.java_client.android.AndroidDriver;
import org.assertj.core.api.SoftAssertions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.MenuSequenceBaseTest;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.config.Platform;
import vn.bsc.automation.core.driver.DeviceActions;
import vn.bsc.automation.core.driver.DriverManager;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.screens.MenuItem;
import vn.bsc.automation.screens.MenuScreen;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The menu entries that are neither transactions nor plain account screens (surveyed 25/11/2026):
 * "Tài sản" (switches to the Trading tab), "Thông tin phần mềm" (inline version row), the "Hỗ trợ" entries and
 * "Ý kiến đóng góp" / "BSC Mtrader", which leave the app. READ-ONLY: nothing is sent, nobody is called.
 * "Tổng đài" places a real phone call: its row is checked, it is NEVER tapped.
 */
@Epic("BSC Mobile Trading")
@Feature("Menu - Tài sản / Hỗ trợ / Thông tin phần mềm (chỉ xem)")
public class MenuOtherItemsTest extends MenuSequenceBaseTest {

    private static final List<String> ASSET_FIGURES = List.of("Giá trị tài sản ròng", "Lãi/lỗ trong ngày",
            "Tiền tại BSC", "Phải thu", "Chứng khoán", "Phải trả", "Thặng dư ký quỹ", "Tỷ lệ ký quỹ hiện tại",
            "Hạn mức vay tối đa");

    @Test(groups = {"regression", "readonly"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ACC_08 - Menu > Tài sản: mở tab Tài sản của Giao dịch, đủ các chỉ tiêu và đều có số")
    public void assetTabShowsFigures() {
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(MenuItem.TAI_SAN);
        By tab = Locators.tab("Tài sản");
        assertThat(WaitUtils.isVisible(tab, Duration.ofSeconds(8))).as("Mở tab Tài sản của màn Giao dịch").isTrue();
        WaitUtils.pause(Duration.ofSeconds(2)); // figures load
        AllureUtils.attachScreenshot("Tài sản");
        List<String> lines = UiNode.snapshot(DriverManager.getDriver().getPageSource()).stream()
                .flatMap(n -> Arrays.stream(n.desc().split("\n"))).map(String::trim).toList();
        SoftAssertions soft = new SoftAssertions();
        for (String f : ASSET_FIGURES) {
            int i = indexOfLabel(lines, f);
            soft.assertThat(i).as("[TC_ACC_08] có chỉ tiêu \"%s\"", f).isGreaterThanOrEqualTo(0);
            if (i >= 0) {
                String value = i + 1 < lines.size() ? lines.get(i + 1) : "";
                soft.assertThat(value).as("[TC_ACC_08] \"%s\" có số (đọc được: '%s')", f, value).matches(".*[0-9].*");
            }
        }
        soft.assertAll();
    }

    @Test(groups = {"regression", "readonly"}, priority = 2)
    @Severity(SeverityLevel.MINOR)
    @Description("TC_INFO_01 - Menu > Thông tin phần mềm: hiện dòng phiên bản dạng Version x.y.z")
    public void softwareVersionShown() {
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(MenuItem.THONG_TIN_PHAN_MEM);
        boolean shown = WaitUtils.condition(() -> UiNode.snapshot(DriverManager.getDriver().getPageSource()).stream()
                .anyMatch(n -> n.desc().matches("(?s)Version\\s+\\d+\\.\\d+(\\.\\d+)+.*")), Duration.ofSeconds(5));
        AllureUtils.attachScreenshot("Thông tin phần mềm");
        assertThat(shown).as("[TC_INFO_01] Có dòng \"Version x.y.z\" sau khi bấm Thông tin phần mềm").isTrue();
    }

    @Test(groups = {"regression", "readonly"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_HELP_01 - Menu > Hỗ trợ > Tổng đài: mở danh sách hotline Miền Bắc / Miền Nam (KHÔNG bấm số - bấm số mới gọi thật)")
    public void hotlineListShown() {
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(MenuItem.TONG_DAI);
        boolean shown = WaitUtils.condition(() -> lines().stream().anyMatch(l -> l.startsWith("Hotline Miền Bắc")),
                Duration.ofSeconds(8));
        AllureUtils.attachScreenshot("Tổng đài");
        List<String> l = lines();
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(shown).as("[TC_HELP_01] Có Hotline Miền Bắc").isTrue();
        soft.assertThat(l.stream().anyMatch(x -> x.startsWith("Hotline Miền Nam"))).as("[TC_HELP_01] Có Hotline Miền Nam").isTrue();
        long phones = l.stream().filter(x -> x.matches("\\(0\\d{2}\\)\\s*\\d{4}\\s*\\d{4}")).count();
        soft.assertThat(phones).as("[TC_HELP_01] Có 2 số hotline dạng (0xx) xxxx xxxx (thấy: %s)", l).isGreaterThanOrEqualTo(2);
        soft.assertAll();
        Allure.addAttachment("Ghi chú", "Không bấm vào số hotline: bấm số sẽ gọi thật");
    }

    @DataProvider(name = "external")
    public Object[][] external() {
        return new Object[][]{
                {"TC_HELP_02", MenuItem.CHAT_TU_VAN_VIEN},
                {"TC_HELP_03", MenuItem.HUONG_DAN_SU_DUNG},
                {"TC_HELP_04", MenuItem.SO_TAY_GIAO_DICH},
                {"TC_FEEDBACK_01", MenuItem.Y_KIEN_DONG_GOP},
                {"TC_MTRADER_01", MenuItem.BSC_MTRADER}};
    }

    @Test(groups = {"regression", "readonly"}, dataProvider = "external", priority = 4)
    @Severity(SeverityLevel.MINOR)
    @Description("TC_HELP / TC_FEEDBACK - Mục mở ra ngoài app (trình duyệt / chọn ứng dụng): mở được rồi quay lại app, không gửi gì")
    public void externalEntryOpensAndReturns(String id, MenuItem item) {
        requireAndroid();
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(item);
        boolean left = WaitUtils.condition(() -> !DeviceActions.isAppInForeground(), Duration.ofSeconds(10));
        // Android "Mở bằng" chooser: open with Chrome, once (never tick "Nhớ lựa chọn của tôi")
        By chrome = By.xpath("//*[@text='Chrome']");
        if (WaitUtils.isVisible(By.xpath("//*[@text='Mở bằng' or @text='Open with']"), Duration.ofSeconds(3))) {
            AllureUtils.attachScreenshot(id + " - hộp Mở bằng");
            DriverManager.getDriver().findElement(chrome).click();
        }
        boolean inChrome = WaitUtils.condition(() -> currentPackage().equals(CHROME), Duration.ofSeconds(10));
        String url = "";
        if (inChrome) {
            // a normal tab shows the address in the url bar; a Chrome Custom Tab (BSC Mtrader) has none -> the
            // rendered page itself is the proof (a WebView with content)
            WaitUtils.condition(() -> !urlBar().isBlank() || webPageRendered(), Duration.ofSeconds(15));
            WaitUtils.pause(Duration.ofSeconds(3)); // page renders
            url = urlBar().isBlank() && webPageRendered() ? "(Chrome Custom Tab - trang đã hiển thị)" : urlBar();
        }
        String src = DriverManager.getDriver().getPageSource();
        boolean errorPage = src.contains("ERR_") || src.contains("Không thể truy cập trang web")
                || src.contains("This site can") || src.contains("Webpage not available");
        String pkg = currentPackage();
        AllureUtils.attachScreenshot(id + " - " + item.label + " (" + (url.isBlank() ? pkg : url) + ")");
        Allure.addAttachment("Trang đã mở", url.isBlank() ? pkg : url);
        DeviceActions.activateApp(); // back to BSC
        WaitUtils.condition(DeviceActions::isAppInForeground, Duration.ofSeconds(5));
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(left).as("[%s] %s mở ra ngoài app", id, item.label).isTrue();
        soft.assertThat(inChrome).as("[%s] %s mở được bằng Chrome (đang mở: %s)", id, item.label, pkg).isTrue();
        soft.assertThat(url).as("[%s] %s: Chrome đã tải một địa chỉ web", id, item.label).isNotBlank();
        soft.assertThat(errorPage).as("[%s] %s: trang web không báo lỗi (%s)", id, item.label, url).isFalse();
        soft.assertAll();
    }

    private static final String CHROME = "com.android.chrome";

    private static String urlBar() {
        List<WebElement> bar = DriverManager.getDriver()
                .findElements(By.id("com.android.chrome:id/url_bar"));
        return bar.isEmpty() ? "" : bar.get(0).getText().trim();
    }

    /** A web page with content is rendered (WebView holding text nodes). */
    private static boolean webPageRendered() {
        String src = DriverManager.getDriver().getPageSource();
        int web = src.indexOf("android.webkit.WebView");
        return web >= 0 && src.substring(web).matches("(?s).*text=\"[^\"]{3,}\".*");
    }

    private static List<String> lines() {
        return UiNode.snapshot(DriverManager.getDriver().getPageSource()).stream()
                .flatMap(n -> Arrays.stream((n.desc().isBlank() ? n.text() : n.desc()).split("\n")))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    @Test(groups = {"regression", "readonly"}, priority = 5)
    @Severity(SeverityLevel.MINOR)
    @Description("TC_HELP_05 - Menu > Hỗ trợ > Mạng lưới: hỏi quyền vị trí (từ chối) hoặc mở bản đồ mạng lưới, rồi quay lại")
    public void networkAsksLocationOrOpens() {
        requireAndroid();
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(MenuItem.MANG_LUOI);
        WaitUtils.pause(Duration.ofSeconds(3));
        String pkg = currentPackage();
        AllureUtils.attachScreenshot("Mạng lưới (" + pkg + ")");
        By deny = By.xpath("//*[@text='Không cho phép' or @text='Từ chối' or @text=\"Don't allow\" or @text='Deny'"
                + " or @text='KHÔNG CHO PHÉP' or @text='TỪ CHỐI']");
        boolean permission = pkg.contains("permission") || pkg.contains("packageinstaller")
                || !DriverManager.getDriver().findElements(deny).isEmpty();
        Allure.addAttachment("Kết quả", permission ? "Hỏi quyền vị trí - bấm từ chối" : "Mở: " + pkg);
        if (permission && !DriverManager.getDriver().findElements(deny).isEmpty()) {
            DriverManager.getDriver().findElements(deny).get(0).click();
        }
        if (!DeviceActions.isAppInForeground()) {
            DeviceActions.activateApp();
        }
        assertThat(permission || !pkg.isBlank()).as("[TC_HELP_05] Mạng lưới hỏi quyền vị trí hoặc mở được (%s)", pkg)
                .isTrue();
    }

    @Test(groups = {"regression", "readonly"}, priority = 6)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_CCQ_01 - Menu > Chứng chỉ quỹ: mở phân hệ CCQ, đủ các mục; mỗi tab loại quỹ có danh sách quỹ (chỉ xem)")
    public void fundCertificatesOpen() {
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(MenuItem.CHUNG_CHI_QUY);
        boolean opened = WaitUtils.condition(() -> lines().stream().anyMatch(l -> l.startsWith("Gợi ý cho bạn")),
                Duration.ofSeconds(10));
        AllureUtils.attachScreenshot("Chứng chỉ quỹ");
        assertThat(opened).as("[TC_CCQ_01] Mở phân hệ Chứng chỉ quỹ").isTrue();
        SoftAssertions soft = new SoftAssertions();
        for (String label : List.of("Gợi ý cho bạn", "Khẩu vị", "Danh sách Chứng chỉ quỹ", "Đang nắm giữ", "Quỹ cổ phiếu",
                "Quỹ trái phiếu", "Quỹ cân bằng", "Trang chủ", "Sổ lệnh", "Đặt lệnh", "Tiện ích", "Tài sản")) {
            soft.assertThat(lines().stream().anyMatch(l -> l.startsWith(label))).as("[TC_CCQ_01] có \"%s\"", label).isTrue();
        }
        for (String tab : List.of("Quỹ cổ phiếu", "Quỹ trái phiếu", "Quỹ cân bằng")) {
            UiNode.snapshot(DriverManager.getDriver().getPageSource()).stream()
                    .filter(n -> n.desc().startsWith(tab)).findFirst().ifPresent(GestureUtils::tap);
            WaitUtils.pause(Duration.ofSeconds(3)); // list loads
            String count = lines().stream().filter(l -> l.startsWith("Danh sách Chứng chỉ quỹ")).findFirst().orElse("");
            boolean empty = lines().stream().anyMatch(l -> l.startsWith("Không tìm thấy chứng chỉ quỹ"));
            AllureUtils.attachScreenshot("Chứng chỉ quỹ - " + tab + " (" + count + ")");
            soft.assertThat(empty).as("[TC_CCQ_01] %s có danh sách quỹ (%s)", tab, count).isFalse();
        }
        soft.assertAll();
    }

    private static void requireAndroid() {
        if (Platform.ios()) {
            throw new SkipException("Kiểm tra ứng dụng bên ngoài chỉ chạy trên Android (gói đang mở)");
        }
    }

    private static String currentPackage() {
        return DriverManager.getDriver() instanceof AndroidDriver a ? a.getCurrentPackage() : AppConfig.appId();
    }

    private static int indexOfLabel(List<String> lines, String label) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(label) || lines.get(i).startsWith(label + ":") || lines.get(i).startsWith(label + " ")) {
                return i;
            }
        }
        return -1;
    }
}
