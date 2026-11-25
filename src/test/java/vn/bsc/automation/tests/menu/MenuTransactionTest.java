package vn.bsc.automation.tests.menu;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.data.MenuTransactionCase;
import vn.bsc.automation.flows.OrderFlow;
import vn.bsc.automation.flows.OrderResultReader;
import vn.bsc.automation.flows.OrderFlow.Outcome;
import vn.bsc.automation.screens.MenuItem;
import vn.bsc.automation.screens.MenuScreen;
import vn.bsc.automation.screens.SheetOption;
import vn.bsc.automation.screens.TransactionFormScreen;
import vn.bsc.automation.screens.components.ActionSheet;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.AppHeader;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.screens.components.ContentInspector;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.core.utils.UiNode;
import org.assertj.core.api.SoftAssertions;
import vn.bsc.automation.data.TransactionLog;
import vn.bsc.automation.screens.components.BottomNavBar;

/**
 * REAL money / securities transactions from the right menu, data-driven by testdata/menu_transactions.csv,
 * executed top -> bottom WITHOUT going back to the main screen: after each transaction the test only goes back
 * to the menu (the group stays expanded) and opens the next item.
 * Requires order.submit.enabled=true (UAT). System errors ("Lỗi hệ thống", E_SYSTEM_ERROR) are failures.
 */
@Epic("BSC Mobile Trading")
@Feature("Giao dịch trong Menu")
@Test(singleThreaded = true)
public class MenuTransactionTest extends LoggedInBaseTest {

    private final OrderFlow resultReader = new OrderFlow();

    @Override
    protected boolean keepScreenBetweenTests() {
        return true;
    }

    @DataProvider(name = "tx")
    public Object[][] tx() {
        // menu.tx.file: e.g. config-prod uses menu_transactions_prod.csv (10,000 VND)
        String file = ConfigReader.get("menu.tx.file", "testdata/menu_transactions.csv");
        String include = ConfigReader.get("menu.tx.include", ".*"); // regex on id
        return MenuTransactionCase.load(file).stream().filter(c -> c.id().matches(include))
                .map(c -> new Object[]{c}).toArray(Object[][]::new);
    }

    /** Ensure the menu is open: from a sub-screen go back (max 3), from the main screen open it once. */
    @BeforeMethod(alwaysRun = true)
    public void toMenu() {
        MenuScreen menu = new MenuScreen();
        AlertDialog dialog = new AlertDialog();
        for (int i = 0; i < 4 && !menu.isDisplayed(Duration.ofSeconds(1)); i++) {
            if (dialog.isShown(Duration.ofMillis(300)) && dialog.hasButton(AlertDialog.BTN_CLOSE)) {
                dialog.close();
                continue;
            }
            if (new BottomNavBar().isDisplayed(Duration.ofSeconds(1))) {
                new AppHeader().openMenu();
                return;
            }
            menu.pressBack();
        }
    }

    @Test(dataProvider = "tx", groups = {"menu", "real-transaction"})
    @Description("Giao dịch trong Menu theo testdata/menu_transactions.csv")
    public void menuTransaction(MenuTransactionCase c) {
        if (!c.enabled()) {
            throw new SkipException(c.note().isBlank() ? "Tắt trong dữ liệu" : c.note());
        }
        if (SCREEN_CHECKS.containsKey(c.menuItem())) {
            screenCheck(c, SCREEN_CHECKS.get(c.menuItem())); // read-only: never submits anything
            return;
        }
        if (!AppConfig.orderSubmitEnabled()) {
            throw new SkipException("Giao dịch thật đang khoá (order.submit.enabled=false)");
        }
        TransactionFormScreen form = openForm(c);
        Allure.addAttachment("Form", c.toString());
        AllureUtils.attachScreenshot("Form " + c.id());
        prepare(form, c, true);
        String symbol = isStock(c) ? form.pickHeldSymbol(null) : null; // a symbol the sending sub-account holds
        if (symbol != null) {
            Allure.addAttachment("Mã chứng khoán", symbol);
        }
        // never send a transaction whose effect cannot be checked afterwards
        Map.Entry<String, Long> before = balance(form, c, symbol);
        if (before == null) {
            AllureUtils.attachScreenshot(c.id() + " - không đọc được số dư");
        }
        assertThat(before).as("[%s] Đọc được số dư trên form trước giao dịch (nhãn: %s) - chưa gửi giao dịch",
                c.id(), TransactionFormScreen.BALANCE_LABELS).isNotNull();
        Allure.addAttachment(before.getKey() + " (trước)", String.format("%,d", before.getValue()));
        String receiver = "CHUYEN_TIEN_NOI_BO".equals(c.option())
                ? form.firstFieldValue("Tiểu khoản nhận", "Tiểu khoản đích", "Đến tiểu khoản", "Tiểu khoản chuyển đến") : null;

        form.typeAmount(c.amount());
        form.submit();
        Outcome out = resultReader.readResult(null);
        Allure.addAttachment("Kết quả", out.message());

        if (expectedRefusal(c, before, out)) {
            return; // the app refused as it should (owner rules 25/11/2026)
        }
        assertThat(OrderResultReader.isSystemError(out.message())).as("[%s] Lỗi hệ thống: %s", c.id(), out.message()).isFalse();
        assertThat(out.success()).as("[%s] Giao dịch thành công. Hệ thống trả: '%s'", c.id(), out.message()).isTrue();
        TransactionLog.succeeded(c, receiver); // statements checked by PostTransactionCheckTest
        checkBalanceMoved(c, before, symbol);
    }

    // ---------------------------------------------------------------- form preparation and balances

    private static boolean isStock(MenuTransactionCase c) {
        return "CHUYEN_CHUNG_KHOAN".equals(c.menuItem());
    }

    /** Beneficiary and sub-accounts of the form ({@code receiving}: also the receiving sub-account). */
    private static void prepare(TransactionFormScreen form, MenuTransactionCase c, boolean receiving) {
        if ("CHUYEN_TIEN_RA_NGOAI".equals(c.option())) {
            // beneficiary account is a dropdown (not pre-filled): pick the first registered account
            form.selectFirstOptionIfEmpty("Số tài khoản ngân hàng");
        }
        // sending / receiving sub-accounts from config (empty = the form's defaults)
        selectConfigured(form, "menu.tx.from.subaccount", FROM_LABELS);
        if (receiving && ("CHUYEN_TIEN_NOI_BO".equals(c.option()) || isStock(c))) {
            selectConfigured(form, "menu.tx.to.subaccount", "Tiểu khoản nhận", "Tiểu khoản đích", "Đến tiểu khoản");
        }
        if (BIDV.contains(c.option())) {
            // Phong / giải toả tiền BIDV: on the ordinary sub-account (others: "Trạng thái corebank ... không hợp lệ")
            selectValue(form, ConfigReader.get("menu.tx.bidv.subaccount", "Thường"), BIDV_LABELS);
        }
    }

    /** What the transaction takes from: the form's balance, or the transferable quantity of {@code symbol}. */
    private static Map.Entry<String, Long> balance(TransactionFormScreen form, MenuTransactionCase c, String symbol) {
        if (!isStock(c)) {
            return form.sourceBalance();
        }
        Long q = form.transferableQuantity();
        return q == null ? null : Map.entry("Khối lượng có thể chuyển " + symbol, q);
    }

    /** Re-opens the form after a successful transaction: the balance moved by the amount (more with a fee). */
    private void checkBalanceMoved(MenuTransactionCase c, Map.Entry<String, Long> before, String symbol) {
        backToMenu();
        toMenu();
        TransactionFormScreen again = openForm(c);
        prepare(again, c, false);
        if (symbol != null) {
            again.pickHeldSymbol(symbol);
        }
        Map.Entry<String, Long> after = balance(again, c, symbol);
        long amount = Long.parseLong(c.amount());
        AllureUtils.attachScreenshot(c.id() + " - form sau giao dịch");
        Allure.addAttachment(before.getKey() + " (trước -> sau)", String.format("%,d -> %s", before.getValue(),
                after == null ? "?" : String.format("%,d", after.getValue())));
        assertThat(after).as("[%s] Đọc được \"%s\" sau giao dịch", c.id(), before.getKey()).isNotNull();
        long decrease = before.getValue() - after.getValue();
        if (isStock(c) || EXACT.contains(c.option())) {
            assertThat(decrease).as("[%s] \"%s\" giảm đúng %,d (trước %,d, sau %,d)", c.id(), before.getKey(),
                    amount, before.getValue(), after.getValue()).isEqualTo(amount);
        } else {
            assertThat(decrease).as("[%s] \"%s\" giảm ít nhất %,d - có thể kèm phí (trước %,d, sau %,d)", c.id(),
                    before.getKey(), amount, before.getValue(), after.getValue()).isGreaterThanOrEqualTo(amount);
        }
        backToMenu();
        toMenu();
    }

    // ---------------------------------------------------------------- expected refusals (owner rules 25/11/2026)

    /**
     * True when a REFUSAL is the correct answer for this case, after asserting it was that refusal: a CCP margin
     * deposit below {@code menu.tx.ccp.min} (fee + VAT), a money transfer after {@code menu.tx.transfer.cutoff}
     * (out of hours), or an amount above the available balance.
     */
    private static boolean expectedRefusal(MenuTransactionCase c, Map.Entry<String, Long> before, Outcome out) {
        long amount = Long.parseLong(c.amount());
        long ccpMin = Long.parseLong(ConfigReader.get("menu.tx.ccp.min", "500000"));
        String cutoff = ConfigReader.get("menu.tx.transfer.cutoff", "19:00");
        if ("NOP_KY_QUY_CCP".equals(c.option()) && amount < ccpMin) {
            return refused(c, out, CCP_BELOW_FEE, String.format("Nộp ký quỹ CCP %,d < %,d phải báo số tiền phải lớn hơn "
                    + "phí và VAT", amount, ccpMin));
        }
        if ("CHUYEN_TIEN".equals(c.menuItem()) && !LocalTime.now().isBefore(LocalTime.parse(cutoff))) {
            return refused(c, out, OUT_OF_HOURS, "Sau " + cutoff + " chuyển tiền phải báo ngoài giờ giao dịch");
        }
        if (amount > before.getValue()) {
            return refused(c, out, INSUFFICIENT, String.format("Số tiền %,d > %s %,d: app phải báo không đủ tiền / vượt "
                    + "mức tối đa", amount, before.getKey(), before.getValue()));
        }
        return false;
    }

    private static boolean refused(MenuTransactionCase c, Outcome out, Pattern expected, String rule) {
        assertThat(out.success()).as("[%s] %s, nhưng giao dịch vẫn thành công: '%s'", c.id(), rule, out.message()).isFalse();
        assertThat(expected.matcher(out.message()).find()).as("[%s] %s. Hệ thống trả: '%s'", c.id(), rule, out.message())
                .isTrue();
        Allure.addAttachment("Kết luận", "App chặn đúng - " + rule + ": " + out.message());
        return true;
    }

    private static final String[] FROM_LABELS = {"Tiểu khoản chuyển", "Tiểu khoản", "Từ tiểu khoản"};
    private static final String[] BIDV_LABELS = {"Tiểu khoản", "Tiểu khoản chuyển", "Từ tiểu khoản", "Tài khoản"};
    private static final Set<String> BIDV = Set.of("PHONG_TOA_TIEN", "GIAI_TOA_TIEN");

    /** Transactions between the customer's own accounts: no fee, the balance moves by exactly the amount. */
    private static final Set<String> EXACT = Set.of("CHUYEN_TIEN_NOI_BO", "NOP_TIEN_PHAI_SINH", "RUT_TIEN_PHAI_SINH",
            "PHONG_TOA_TIEN", "GIAI_TOA_TIEN");

    /** CCP margin deposit refused because the amount does not cover the fee + VAT. */
    private static final Pattern CCP_BELOW_FEE = Pattern.compile("(?i)phải lớn hơn số tiền phí");
    /** Refusal outside the transaction window. */
    private static final Pattern OUT_OF_HOURS = Pattern.compile("(?i)ngoài giờ giao dịch|ngoài thời gian giao dịch");
    /** Refusal because the amount exceeds what is available. */
    private static final Pattern INSUFFICIENT = Pattern.compile("(?i)không đủ|vượt quá|nhỏ hơn hoặc bằng số tiền tối đa"
            + "|lớn hơn số (dư|tiền) (khả dụng|tối đa|có thể)|số dư không đủ|insufficient");

    // ---------------------------------------------------------------- screen-only cases

    /**
     * Transactions that need something on the account first (a right to buy, an open CW IPO, a voucher, an order
     * waiting for confirmation, a framework loan contract). Surveyed 25/11/2026. The test opens the screen, checks
     * its fields / columns and the list: empty -> SKIP with the live reason; data -> SKIP asking for a survey of the
     * registration form (nothing is ever submitted here).
     */
    private record ScreenCheck(List<String> labels, List<String> tabs, String header, String emptyReason) {
    }

    private static final Map<String, ScreenCheck> SCREEN_CHECKS = Map.of(
            "DANG_KY_QUYEN_MUA", new ScreenCheck(List.of("Tiểu khoản", "Mã CK", "KL mua tối đa", "Ngày hết hạn"),
                    List.of(), "Mã CK", "Không có quyền mua khả dụng trên tiểu khoản"),
            "DANG_KY_MUA_CW_IPO", new ScreenCheck(List.of("Tiểu khoản", "Danh sách IPO", "IPO đã đăng ký", "Mã CW IPO",
                    "Số lượng phát hành", "Ngày hết hạn"), List.of(), "Mã CW IPO", "Không có đợt chứng quyền IPO đang mở"),
            "DANG_KY_VOUCHER", new ScreenCheck(List.of("Còn hiệu lực", "Nhận ưu đãi"),
                    List.of("Còn hiệu lực", "Nhận ưu đãi"), null, "Không có voucher còn hiệu lực / ưu đãi để nhận"),
            "XAC_NHAN_LENH_ONLINE", new ScreenCheck(List.of("Tiểu khoản", "Từ ngày", "Đến ngày", "Mã CK", "Ngày GD", "Giá",
                    "KL", "Xác nhận tất cả", "Chọn lệnh"), List.of(), "Ngày GD", "Không có lệnh chờ xác nhận trong ngày"),
            // "Số dư khả dụng" / "Số dư cho vay" are drawn but not exposed in the accessibility tree
            "CHO_VAY", new ScreenCheck(List.of("Chuyển tiền cho vay", "Đăng ký HĐ khung", "Tiểu khoản", "Tạo mới"), List.of(), "Ngày tạo khoản", "Chưa có khoản cho vay. Tạo mới cần đăng ký "
                    + "Hợp đồng khung (đồng ý điều khoản hợp đồng) - chờ chủ tài khoản quyết định"));

    private void screenCheck(MenuTransactionCase c, ScreenCheck chk) {
        MenuItem item = MenuItem.valueOf(c.menuItem());
        MenuScreen menu = new MenuScreen();
        menu.waitUntilLoaded();
        menu.open(item);
        WaitUtils.pause(Duration.ofSeconds(2)); // list loads
        AllureUtils.attachScreenshot(c.id() + " - " + item.title);
        List<UiNode> nodes = new ContentInspector().content();
        SoftAssertions soft = new SoftAssertions();
        for (String l : chk.labels()) {
            // the app often merges several labels into one node ("Mã CW IPO\nSố lượng phát hành\nNgày hết hạn")
            soft.assertThat(nodes.stream().anyMatch(n -> Arrays.stream(n.desc().split("\n"))
                    .anyMatch(line -> line.trim().equals(l) || line.trim().startsWith(l + " "))))
                    .as("[%s] Màn %s có \"%s\"", c.id(), item.title, l).isTrue();
        }
        soft.assertAll();
        int rows = 0;
        if (chk.tabs().isEmpty()) {
            rows = rowsBelow(nodes, chk.header());
        } else {
            for (String tab : chk.tabs()) {
                nodes.stream().filter(n -> n.desc().startsWith(tab + "\n")).findFirst().ifPresent(GestureUtils::tap);
                WaitUtils.pause(Duration.ofSeconds(2));
                AllureUtils.attachScreenshot(c.id() + " - tab " + tab);
                List<UiNode> now = new ContentInspector().content();
                int barBottom = now.stream().filter(n -> n.desc().contains("trong tổng số"))
                        .mapToInt(n -> n.y() + n.height()).max().orElse(0);
                rows += (int) now.stream().filter(n -> n.y() > barBottom && !n.desc().isBlank()).count();
                nodes = now;
            }
        }
        if (rows == 0) {
            throw new SkipException(chk.emptyReason() + " (kiểm tra lúc chạy " + LocalTime.now().withNano(0) + ")");
        }
        throw new SkipException("Có " + rows + " dòng dữ liệu trên màn " + item.title
                + " - cần khảo sát form đăng ký để bật giao dịch thật (xem ảnh chụp)");
    }

    /** Data rows under the column header (labels on the header row itself are not rows). */
    private static int rowsBelow(List<UiNode> nodes, String header) {
        Optional<UiNode> h = nodes.stream().filter(n -> n.desc().startsWith(header)).findFirst();
        if (h.isEmpty()) {
            return 0;
        }
        int below = h.get().y() + h.get().height();
        return (int) nodes.stream().filter(n -> n.y() >= below + 4 && n.desc().contains("\n")
                && !n.desc().startsWith("Xác nhận tất cả")).count();
    }

    /** Picks the configured sub-account (config key {@code key}; empty = keep the form's default). */
    private static void selectConfigured(TransactionFormScreen form, String key, String... labels) {
        String wanted = ConfigReader.get(key);
        if (wanted != null && !wanted.isBlank()) {
            selectValue(form, wanted, labels);
        }
    }

    /** Picks {@code wanted} (name or number of a sub-account) in the first of these fields present on the form. */
    private static void selectValue(TransactionFormScreen form, String wanted, String... labels) {
        for (String label : labels) {
            if (form.firstFieldValue(label) != null) {
                if (!form.fieldValue(label).contains(wanted.trim())) {
                    form.selectFieldOption(label, wanted.trim());
                }
                return;
            }
        }
        throw new AssertionError("Không thấy trường tiểu khoản (" + String.join(" / ", labels) + ") để chọn " + wanted);
    }

    /** Menu (already open) -> item -> option of its bottom sheet -> the transaction form. */
    private TransactionFormScreen openForm(MenuTransactionCase c) {
        MenuItem item = MenuItem.valueOf(c.menuItem());
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(item);
        TransactionFormScreen form;
        if (!c.option().isBlank()) {
            SheetOption opt = SheetOption.valueOf(c.option());
            ActionSheet sheet = new ActionSheet();
            sheet.waitUntilLoaded();
            sheet.choose(opt.label);
            form = new TransactionFormScreen(opt);
        } else {
            form = new TransactionFormScreen(item.title);
        }
        form.waitUntilLoaded();
        return form;
    }

    /** Back to the MENU only (not the main screen), ready for the next transaction. */
    @AfterMethod(alwaysRun = true)
    public void backToMenu() {
        try {
            toMenu();
        } catch (Exception | AssertionError ignored) {
            // next test's @BeforeMethod retries
        }
    }
}
