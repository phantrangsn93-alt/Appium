package vn.bsc.automation.tests.order;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.data.SelectedStock;
import vn.bsc.automation.data.TestDataReader;
import vn.bsc.automation.screens.OrderFormScreen;
import vn.bsc.automation.screens.OrderFormScreen.OrderType;
import vn.bsc.automation.screens.OrderFormScreen.Side;
import vn.bsc.automation.screens.components.AlertDialog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order form checks on a REAL account. No order is ever submitted:
 * the only submit tap is done with quantity = 0 through the guarded
 * {@link OrderFormScreen#tapSubmitExpectingValidation()}.
 */
@Epic("BSC Mobile Trading")
@Feature("Đặt lệnh (form - không gửi lệnh)")
public class OrderFormTest extends LoggedInBaseTest {

    private static final JsonNode DATA = TestDataReader.read("order.json");
    private OrderFormScreen form;

    @BeforeMethod(alwaysRun = true)
    public void openOrderForm() {
        form = navigator.openTrading().openOrderForm();
        form.selectSubAccount(vn.bsc.automation.data.SubAccount.resolve(DATA.get("subAccount").asText())); // independent of the previous suite's state
        form.selectBuy();
    }

    /** Never leave a non-zero quantity on the form. */
    @AfterMethod(alwaysRun = true)
    public void cleanUpForm() {
        try {
            AlertDialog dialog = new AlertDialog();
            if (dialog.isShown(java.time.Duration.ofMillis(500)) && dialog.hasButton(AlertDialog.BTN_CLOSE)) {
                dialog.close();
            }
            if (form != null) {
                form.hideKeyboard();
            }
            if (form != null && form.isDisplayed(java.time.Duration.ofSeconds(2))) {
                form.selectOrderType(OrderType.LIMIT);
                form.resetQuantity();
                form.selectBuy();
            }
        } catch (Exception | AssertionError ignored) {
            // best effort clean-up - must never fail the configuration (would skip all following tests)
        }
    }

    @DataProvider(name = "symbols")
    public Object[][] symbols() {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode n : DATA.get("symbols")) {
            rows.add(new Object[]{n.get("symbol").asText(), n.get("exchange").asText()});
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(groups = {"smoke", "regression", "order"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ORD_01 - Form mặc định: Lệnh mua, Limit, hiệu lực Trong ngày, đủ thành phần")
    public void defaultFormState() {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(form.currentSide()).isEqualTo(Side.BUY);
        for (OrderType t : OrderType.values()) {
            soft.assertThat(form.isOrderTypeVisible(t)).as("Loại lệnh " + t.label()).isTrue();
        }
        for (int p : new int[]{25, 50, 75, 100}) {
            soft.assertThat(form.isPercentButtonVisible(p)).as(p + "%").isTrue();
        }
        soft.assertThat(form.isValidityChecked(0)).as("Trong ngày được chọn").isTrue();
        soft.assertThat(form.isValidityChecked(1)).as("Nhiều ngày không chọn").isFalse();
        soft.assertThat(form.getBuyingPower()).as("Sức mua >= 0").isGreaterThanOrEqualTo(0);
        soft.assertAll();
    }

    @Test(groups = {"smoke", "regression", "order"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ORD_02 - Chuyển Lệnh mua <-> Lệnh bán đổi nút MUA/BÁN")
    public void switchBuySell() {
        assertThat(form.selectSell().currentSide()).isEqualTo(Side.SELL);
        assertThat(form.selectBuy().currentSide()).isEqualTo(Side.BUY);
    }

    @Test(groups = {"regression", "order"}, dataProvider = "symbols")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ORD_03 - Nhập mã CK: hiển thị gợi ý, chọn mã điền thông tin, giá Trần > TC > Sàn, giá đặt mặc định = TC")
    public void selectSymbolFillsMarketInfo(String symbol, String exchange) {
        form.typeSymbol(symbol);
        assertThat(form.isSuggestionVisible(symbol)).as("Gợi ý " + symbol).isTrue();
        form.chooseSuggestion(symbol);

        SelectedStock stock = form.getSelectedStock();
        List<BigDecimal> crf = form.getCeilingRefFloor();
        BigDecimal ceiling = crf.get(0), ref = crf.get(1), floor = crf.get(2);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(stock.symbol()).isEqualTo(symbol);
        soft.assertThat(stock.exchange()).isEqualTo(exchange);
        soft.assertThat(stock.companyName()).isNotBlank();
        soft.assertThat(ceiling).as("Trần > TC").isGreaterThan(ref);
        soft.assertThat(ref).as("TC > Sàn").isGreaterThan(floor);
        soft.assertThat(form.getPrice()).as("Giá đặt mặc định trong biên độ")
                .isBetween(floor, ceiling);
        soft.assertThat(form.getQuantity()).as("KL mặc định").isZero();
        soft.assertAll();
    }

    @Test(groups = {"smoke", "regression", "order"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_ORD_04 - Nút +/- khối lượng thay đổi theo lô 100")
    public void quantityStepperUsesLotSize() {
        int lot = DATA.get("lotSize").asInt();
        form.selectSymbol(DATA.get("symbols").get(0).get("symbol").asText());

        assertThat(form.getQuantity()).isZero();
        assertThat(form.increaseQuantity().getQuantity()).isEqualTo(lot);
        assertThat(form.increaseQuantity().getQuantity()).isEqualTo(2L * lot);
        assertThat(form.decreaseQuantity().getQuantity()).isEqualTo(lot);
        assertThat(form.decreaseQuantity().getQuantity()).isZero();
    }

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_ORD_05 - Nút +/- giá thay đổi 1 bước giá và quay về giá ban đầu")
    public void priceStepperChangesByTick() {
        form.selectSymbol(DATA.get("symbols").get(0).get("symbol").asText());
        BigDecimal initial = form.getPrice();

        BigDecimal up = form.increasePrice().getPrice();
        assertThat(up).as("Giá tăng").isGreaterThan(initial);
        BigDecimal back = form.decreasePrice().getPrice();
        assertThat(back).as("Giá quay lại").isEqualByComparingTo(initial);
        BigDecimal down = form.decreasePrice().getPrice();
        assertThat(down).as("Giá giảm").isLessThan(initial);
        form.increasePrice();
    }

    @Test(groups = {"smoke", "regression", "order"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ORD_06 - Bấm MUA khi khối lượng = 0 hiển thị lỗi validate, không gửi lệnh")
    public void submitWithZeroQuantityShowsValidation() {
        form.selectSymbol(DATA.get("symbols").get(0).get("symbol").asText());
        form.resetQuantity();

        AlertDialog dialog = form.tapSubmitExpectingValidation();
        assertThat(dialog.getMessage()).isEqualTo(DATA.get("zeroQuantityMessage").asText());
        dialog.close();
        assertThat(form.isDisplayed()).as("Vẫn ở form đặt lệnh").isTrue();
    }

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ORD_07 - Bấm BÁN khi khối lượng = 0 hiển thị lỗi validate, không gửi lệnh")
    public void sellWithZeroQuantityShowsValidation() {
        form.selectSymbol(DATA.get("symbols").get(0).get("symbol").asText());
        form.selectSell().resetQuantity();

        AlertDialog dialog = form.tapSubmitExpectingValidation();
        assertThat(dialog.getMessage()).isEqualTo(DATA.get("zeroQuantityMessage").asText());
        dialog.close();
    }

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ORD_08 - Safety guard: framework từ chối bấm MUA khi khối lượng > 0")
    public void safetyGuardBlocksRealOrder() {
        form.selectSymbol(DATA.get("symbols").get(0).get("symbol").asText());
        form.increaseQuantity();

        org.assertj.core.api.Assertions.assertThatThrownBy(form::tapSubmitExpectingValidation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAFETY GUARD");
        form.resetQuantity();
    }
}
