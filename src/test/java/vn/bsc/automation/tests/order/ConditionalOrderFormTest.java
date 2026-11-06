package vn.bsc.automation.tests.order;

import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.data.SubAccount;
import vn.bsc.automation.flows.Navigator;
import vn.bsc.automation.screens.OrderFormScreen;
import vn.bsc.automation.screens.OrderFormScreen.ConditionalType;
import vn.bsc.automation.screens.PortfolioScreen;
import vn.bsc.automation.screens.components.TpSlSheet;

/**
 * Conditional orders (Stop Limit / Trailing Stop / OCO), multi-day LO and TP/SL from the portfolio.
 * Form only - nothing is submitted, the date picker is never opened.
 */
public class ConditionalOrderFormTest extends LoggedInBaseTest {

    private final Navigator navigator = new Navigator();
    private OrderFormScreen form;

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ORD_COND_01 - Lệnh điều kiện: chọn được Stop Limit / Trailing Stop / OCO")
    public void conditionalTypesCanBeSelected() {
        form = navigator.openTrading().openOrderForm();
        form.selectSubAccount(SubAccount.CO_SO.accountName());
        SoftAssertions soft = new SoftAssertions();
        for (ConditionalType t : ConditionalType.values()) {
            form.selectConditionalType(t);
            soft.assertThat(form.getConditionalType()).as("Kiểu lệnh").isEqualTo(t.label());
        }
        soft.assertAll();
    }

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_ORD_COND_02 - Lệnh LO nhiều ngày: hiện ô Đến ngày (dd/MM/yyyy)")
    public void multiDayLimitShowsExpiryDate() {
        form = navigator.openTrading().openOrderForm();
        form.selectOrderType(OrderFormScreen.OrderType.LIMIT).selectValidity(1);
        org.assertj.core.api.Assertions.assertThat(form.getExpiryDate()).matches("\\d{2}/\\d{2}/\\d{4}");
    }

    @Test(groups = {"regression", "order"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_ORD_TPSL_01 - Danh mục (margin): mở TP/SL của mã đang nắm giữ, đọc giá vốn / KL tối đa / gợi ý")
    public void tpSlSheetFromPortfolio() {
        navigator.openTrading().openOrderForm().selectSubAccount(SubAccount.MARGIN.accountName());
        PortfolioScreen portfolio = navigator.openTrading().openPortfolio();
        org.assertj.core.api.Assertions.assertThat(portfolio.holdings()).as("Danh mục margin có CP").isNotEmpty();
        String symbol = portfolio.holdings().get(0);
        TpSlSheet sheet = portfolio.openTpSl(symbol);
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(sheet.symbol()).as("Mã CK").isEqualTo(symbol);
        soft.assertThat(sheet.costPrice()).as("Giá vốn").isNotNull();
        soft.assertThat(sheet.maxQuantity()).as("KL tối đa").isPositive();
        soft.assertThat(sheet.takeProfitHint()).as("Gợi ý chốt lời").startsWith("≥");
        soft.assertThat(sheet.stopLossHint()).as("Gợi ý cắt lỗ").startsWith("≤");
        sheet.close();
        soft.assertAll();
    }

    /** Leave the form as other suites expect it: Limit, in-day validity. */
    @AfterMethod(alwaysRun = true)
    public void resetForm() {
        try {
            if (form != null && form.isDisplayed(java.time.Duration.ofSeconds(2))) {
                form.selectOrderType(OrderFormScreen.OrderType.LIMIT).selectValidity(0);
            }
        } catch (Exception | AssertionError ignored) {
            // best effort
        }
    }
}
