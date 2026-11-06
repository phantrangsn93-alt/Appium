package vn.bsc.automation.tests.search;

import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.flows.Navigator;
import vn.bsc.automation.screens.OrderFormScreen;
import vn.bsc.automation.screens.StockDetailScreen;

/** Stock detail screen opened from search (read-only: "Mua" only opens the order form, nothing is submitted). */
public class StockDetailTest extends LoggedInBaseTest {

    private static final String SYMBOL = "AAA";

    @Test(groups = {"regression", "stock"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_STOCK_01 - Chi tiết mã: header, đủ tab, Trần > Tham chiếu > Sàn")
    public void stockDetailShowsPriceTable() {
        StockDetailScreen detail = new Navigator().openSearch().search(SYMBOL).openStock(SYMBOL);
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(detail.getHeader()).as("Header").startsWith(SYMBOL + "\n");
        soft.assertThat(detail.ceiling()).as("Trần > Tham chiếu").isGreaterThan(detail.reference());
        soft.assertThat(detail.reference()).as("Tham chiếu > Sàn").isGreaterThan(detail.floor());
        soft.assertAll();
    }

    @Test(groups = {"regression", "stock"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_STOCK_02 - Chi tiết mã: nút Mua mở form Đặt lệnh với mã đã chọn")
    public void buyOpensOrderFormWithSymbol() {
        StockDetailScreen detail = new Navigator().openSearch().search(SYMBOL).openStock(SYMBOL);
        OrderFormScreen form = detail.tapBuy();
        org.assertj.core.api.Assertions.assertThat(form.getSelectedStock().symbol()).isEqualTo(SYMBOL);
    }
}
