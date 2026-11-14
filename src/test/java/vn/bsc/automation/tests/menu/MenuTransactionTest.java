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
import vn.bsc.automation.flows.OrderFlow.Outcome;
import vn.bsc.automation.screens.MenuItem;
import vn.bsc.automation.screens.MenuScreen;
import vn.bsc.automation.screens.SheetOption;
import vn.bsc.automation.screens.TransactionFormScreen;
import vn.bsc.automation.screens.components.ActionSheet;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.AppHeader;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
        String file = vn.bsc.automation.core.config.ConfigReader.get("menu.tx.file", "testdata/menu_transactions.csv");
        String include = vn.bsc.automation.core.config.ConfigReader.get("menu.tx.include", ".*"); // regex on id
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
            if (new vn.bsc.automation.screens.components.BottomNavBar().isDisplayed(Duration.ofSeconds(1))) {
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
        if (!AppConfig.orderSubmitEnabled()) {
            throw new SkipException("Giao dịch thật đang khoá (order.submit.enabled=false)");
        }
        TransactionFormScreen form = openForm(c);
        Allure.addAttachment("Form", c.toString());
        vn.bsc.automation.core.utils.AllureUtils.attachScreenshot("Form " + c.id());

        if ("CHUYEN_TIEN_RA_NGOAI".equals(c.option())) {
            // beneficiary account is a dropdown (not pre-filled): pick the first registered account
            form.selectFirstOptionIfEmpty("Số tài khoản ngân hàng");
        }
        // sending / receiving sub-accounts from config (empty = the form's defaults)
        selectConfigured(form, "menu.tx.from.subaccount", "Tiểu khoản chuyển", "Tiểu khoản", "Từ tiểu khoản");
        if ("CHUYEN_TIEN_NOI_BO".equals(c.option()) || "CHUYEN_CHUNG_KHOAN".equals(c.menuItem())) {
            selectConfigured(form, "menu.tx.to.subaccount", "Tiểu khoản nhận", "Tiểu khoản đích", "Đến tiểu khoản");
        }
        boolean money = !"CHUYEN_CHUNG_KHOAN".equals(c.menuItem());
        Long before = money ? form.maxTransferable() : null; // balance of the SENDING (sub-)account
        String receiver = "CHUYEN_TIEN_NOI_BO".equals(c.option())
                ? form.firstFieldValue("Tiểu khoản nhận", "Tiểu khoản đích", "Đến tiểu khoản", "Tiểu khoản chuyển đến") : null;
        if (before != null) {
            Allure.addAttachment("Số tiền tối đa được chuyển (trước)", String.format("%,d", before));
        }
        form.typeAmount(c.amount());
        form.submit();
        Outcome out = resultReader.readResult(null);
        Allure.addAttachment("Kết quả", out.message());

        assertThat(OrderFlow.isSystemError(out.message())).as("[%s] Lỗi hệ thống: %s", c.id(), out.message()).isFalse();
        assertThat(out.success()).as("[%s] Giao dịch thành công. Hệ thống trả: '%s'", c.id(), out.message()).isTrue();
        vn.bsc.automation.data.TransactionLog.succeeded(c, receiver); // statements checked by PostTransactionCheckTest

        // money leaves the sending account: exactly the amount for an internal transfer, at least the amount otherwise
        if (before != null) {
            backToMenu();
            toMenu();
            Long after = openForm(c).maxTransferable();
            long amount = Long.parseLong(c.amount());
            Allure.addAttachment("Số tiền tối đa được chuyển (trước -> sau)",
                    String.format("%,d -> %s", before, after == null ? "?" : String.format("%,d", after)));
            assertThat(after).as("[%s] Đọc được số tiền tối đa được chuyển sau giao dịch", c.id()).isNotNull();
            if ("CHUYEN_TIEN_NOI_BO".equals(c.option())) {
                assertThat(before - after).as("[%s] Tiểu khoản gửi giảm đúng %,d", c.id(), amount).isEqualTo(amount);
            } else {
                assertThat(before - after).as("[%s] Tiền giảm ít nhất %,d (có thể kèm phí)", c.id(), amount)
                        .isGreaterThanOrEqualTo(amount);
            }
        }
    }

    /** Picks the configured sub-account (its number or name) in the first of these fields present on the form. */
    private static void selectConfigured(TransactionFormScreen form, String key, String... labels) {
        String wanted = vn.bsc.automation.core.config.ConfigReader.get(key);
        if (wanted == null || wanted.isBlank()) {
            return;
        }
        for (String label : labels) {
            if (form.firstFieldValue(label) != null) {
                if (!form.fieldValue(label).contains(wanted.trim())) {
                    form.selectFieldOption(label, wanted.trim());
                }
                return;
            }
        }
        throw new AssertionError("Không thấy trường tiểu khoản (" + String.join(" / ", labels) + ") để chọn " + key);
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
