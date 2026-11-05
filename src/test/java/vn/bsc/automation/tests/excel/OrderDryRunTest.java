package vn.bsc.automation.tests.excel;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.flows.OrderFlow;
import vn.bsc.automation.flows.OrderFlow.FilledForm;
import vn.bsc.automation.screens.OrderFormScreen;
import vn.bsc.automation.screens.OrderFormScreen.Side;
import vn.bsc.automation.screens.components.AlertDialog;

import java.time.Duration;

/**
 * DRY RUN of testdata/orders.csv: fills the order form for every row exactly like the real test,
 * verifies each field, then resets the quantity to 0 - NOTHING is submitted. Safe at any time
 * (use it to validate new data rows before a real-order run).
 */
@Epic("Kịch bản Excel - sau khi update App")
@Feature("Kiểm tra Lệnh - dry run (không gửi lệnh)")
public class OrderDryRunTest extends LoggedInBaseTest {

    private final OrderFlow orderFlow = new OrderFlow();
    private OrderFormScreen form;

    @DataProvider(name = "orders")
    public Object[][] orders() {
        // -Ddryrun.includeDisabled=true also checks rows disabled for real runs (validate new data safely)
        boolean all = vn.bsc.automation.core.config.ConfigReader.getBool("dryrun.includeDisabled", false);
        return OrderCase.load("testdata/orders.csv").stream()
                .filter(c -> all || c.enabled()).map(c -> new Object[]{c}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "orders", groups = {"excel", "order", "dry-run"})
    @Description("Điền form theo từng dòng orders.csv và kiểm tra, KHÔNG bấm Mua/Bán")
    public void fillOrderForm(OrderCase c) {
        FilledForm f = orderFlow.fill(c);
        form = f.form();
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(form.getSubAccount()).as("Tiểu khoản").startsWith(Locators.nfc(c.subAccount()) + " - ");
        soft.assertThat(form.currentSide()).as("Mua/Bán").isEqualTo(c.side() == OrderCase.Side.BUY ? Side.BUY : Side.SELL);
        soft.assertThat(form.getSelectedStock().symbol()).as("Mã CK").isEqualTo(c.symbol());
        soft.assertThat(form.getSelectedStock().exchange()).as("Sàn").isEqualTo(c.exchange());
        if (c.isMarketType() && !"MARKET".equals(c.orderType())) {
            soft.assertThat(form.getMarketPriceType()).as("Loại lệnh thị trường").isEqualTo(c.orderType());
        } else if (!c.isMarketType()) {
            soft.assertThat(form.getPrice()).as("Giá").isEqualByComparingTo(f.price());
        }
        soft.assertThat(form.getQuantity()).as("Khối lượng").isEqualTo(c.quantity());
        soft.assertAll();
    }

    /** Never leave a filled form behind. */
    @AfterMethod(alwaysRun = true)
    public void resetForm() {
        try {
            AlertDialog d = new AlertDialog();
            if (d.isShown(Duration.ofMillis(500)) && d.hasButton(AlertDialog.BTN_CLOSE)) {
                d.close();
            }
            if (form != null && form.isDisplayed(Duration.ofSeconds(2))) {
                form.resetQuantity();
                form.selectOrderType(OrderFormScreen.OrderType.LIMIT);
            }
        } catch (Exception | AssertionError ignored) {
            // best effort
        }
    }
}
