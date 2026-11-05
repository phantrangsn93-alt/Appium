package vn.bsc.automation.tests.priceboard;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.utils.NumberUtils;
import vn.bsc.automation.data.IndexCard;
import vn.bsc.automation.data.StockRow;
import vn.bsc.automation.screens.PriceBoardScreen;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Live market data changes constantly: tests assert structure/format, never exact values. */
@Epic("BSC Mobile Trading")
@Feature("Bảng giá")
public class PriceBoardTest extends LoggedInBaseTest {

    @Test(groups = {"smoke", "regression"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_PB_01 - Thẻ chỉ số VN INDEX / VN30 hiển thị đúng định dạng")
    public void indexCardsHaveValidFormat() {
        PriceBoardScreen board = navigator.openPriceBoard();
        SoftAssertions soft = new SoftAssertions();
        for (String name : new String[]{"VN INDEX", "VN30"}) {
            IndexCard card = board.getIndexCard(name);
            soft.assertThat(card.name()).isEqualTo(name);
            soft.assertThat(card.value()).as(name + " giá trị").matches(NumberUtils.PRICE);
            soft.assertThat(card.change()).as(name + " thay đổi").matches(NumberUtils.CHANGE_WITH_PERCENT);
            soft.assertThat(card.turnover()).as(name + " GTGD").endsWith("(Tỷ VND)");
        }
        soft.assertAll();
    }

    @Test(groups = {"smoke", "regression"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_PB_02 - Danh sách mã: mã CK, sàn, giá, khối lượng, thay đổi hợp lệ")
    public void stockRowsHaveValidData() {
        PriceBoardScreen board = navigator.openPriceBoard();
        List<StockRow> rows = board.getStockRows();
        assertThat(rows).as("Danh sách mã").isNotEmpty();

        SoftAssertions soft = new SoftAssertions();
        for (StockRow r : rows) {
            soft.assertThat(r.symbol()).as("Mã CK").matches("^[A-Z0-9]{3,10}$");
            soft.assertThat(r.exchange()).as(r.symbol() + " sàn").isIn("HOSE", "HNX", "UPCOM");
            soft.assertThat(r.companyName()).as(r.symbol() + " tên công ty").isNotBlank();
            soft.assertThat(r.price()).as(r.symbol() + " giá").matches(NumberUtils.PRICE);
            soft.assertThat(r.volume()).as(r.symbol() + " KL").matches(NumberUtils.VOLUME);
            soft.assertThat(r.changePercent()).as(r.symbol() + " %").matches("^[+-]?\\d+(\\.\\d+)?%$");
        }
        soft.assertAll();
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.MINOR)
    @Description("TC_PB_03 - Các tuỳ chọn sắp xếp và mục Tin tức hiển thị")
    public void sortOptionsAndNewsVisible() {
        PriceBoardScreen board = navigator.openPriceBoard();
        SoftAssertions soft = new SoftAssertions();
        for (String opt : new String[]{"A-Z", "Giá", "Khối lượng"}) {
            soft.assertThat(board.isSortOptionVisible(opt)).as("Sắp xếp " + opt).isTrue();
        }
        soft.assertThat(board.isNewsSectionVisible()).as("Tin tức").isTrue();
        soft.assertAll();
    }
}
