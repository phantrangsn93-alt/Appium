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
        return MenuTransactionCase.load("testdata/menu_transactions.csv").stream()
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
        Allure.addAttachment("Form", c.toString());
        vn.bsc.automation.core.utils.AllureUtils.attachScreenshot("Form " + c.id());

        form.typeAmount(c.amount());
        form.submit();
        Outcome out = resultReader.readResult(null);
        Allure.addAttachment("Kết quả", out.message());

        assertThat(OrderFlow.isSystemError(out.message())).as("[%s] Lỗi hệ thống: %s", c.id(), out.message()).isFalse();
        assertThat(out.success()).as("[%s] Giao dịch thành công. Hệ thống trả: '%s'", c.id(), out.message()).isTrue();
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
