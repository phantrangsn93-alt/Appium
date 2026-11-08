package vn.bsc.automation.tests.priceboard;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.fasterxml.jackson.databind.JsonNode;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.data.TestDataReader;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.utils.NumberUtils;
import vn.bsc.automation.data.IndexCard;
import vn.bsc.automation.data.StockRow;
import vn.bsc.automation.screens.PriceBoardScreen;

import java.time.Duration;
import java.util.ArrayList;
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
        board.selectWatchlist("Danh mục nắm giữ");
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
        board.selectWatchlist("Danh mục nắm giữ"); // short list: the news section is right below it
        SoftAssertions soft = new SoftAssertions();
        for (String opt : new String[]{"A-Z", "Giá", "Khối lượng"}) {
            soft.assertThat(board.isSortOptionVisible(opt)).as("Sắp xếp " + opt).isTrue();
        }
        soft.assertThat(board.isNewsSectionVisible()).as("Tin tức").isTrue();
        soft.assertAll();
    }

    /** Every chip of the selector row x every option of its sheet (testdata/market_data.json) -> one test case each. */
    @DataProvider(name = "boardLists")
    public Object[][] boardLists() {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode sel : TestDataReader.read("market_data.json").get("priceBoard").get("selectors")) {
            int chip = sel.get("chip").asInt();
            String name = sel.get("name").asText();
            if ("watchlist".equals(sel.path("kind").asText())) {
                sel.get("watchlists").fields().forEachRemaining(e -> {
                    if (e.getValue().isEmpty()) {
                        rows.add(new Object[]{chip, name, e.getKey(), ""});
                    }
                    e.getValue().forEach(sub -> rows.add(new Object[]{chip, name, e.getKey(), sub.asText()}));
                });
            } else if (sel.get("options").isEmpty()) {
                rows.add(new Object[]{chip, name, "", ""});
            } else {
                sel.get("options").forEach(o -> rows.add(new Object[]{chip, name, o.asText(), ""}));
            }
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(groups = {"regression"}, dataProvider = "boardLists", priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_PB_04 - Mỗi bộ chọn / lựa chọn trên Bảng giá phải có dữ liệu (trống => FAIL)")
    public void everyBoardListHasData(int chip, String selector, String option, String subTab) {
        PriceBoardScreen board = navigator.openPriceBoard();
        if (chip == 0) {
            board.selectWatchlist(option);
        } else {
            board.selectChip(chip, option);
        }
        if (!subTab.isEmpty()) {
            board.selectSubTab(subTab);
        }
        String label = selector + (option.isEmpty() ? "" : " " + option) + (subTab.isEmpty() ? "" : " / " + subTab);
        List<UiNode> rows = board.dataRows(Duration.ofSeconds(10));
        AllureUtils.attachScreenshot("Bảng giá - " + label);
        assertThat(rows).as("Bảng giá - " + label + ": không có dữ liệu").isNotEmpty();
    }

    @Test(groups = {"regression"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_PB_05 - Icon bảng giá chi tiết: hiển thị bảng có dữ liệu, mỗi dòng Trần >= TC >= Sàn")
    public void detailBoardShowsValidRows() {
        PriceBoardScreen board = navigator.openPriceBoard();
        board.openDetailBoard();
        try {
            List<List<String>> rows = board.detailRows(Duration.ofSeconds(10));
            AllureUtils.attachScreenshot("Bảng giá chi tiết");
            assertThat(rows).as("Bảng giá chi tiết: không có dữ liệu").isNotEmpty();
            SoftAssertions soft = new SoftAssertions();
            for (List<String> r : rows) {
                double tc = NumberUtils.parse(r.get(0)).doubleValue(), ceil = NumberUtils.parse(r.get(1)).doubleValue(),
                        floor = NumberUtils.parse(r.get(2)).doubleValue();
                String sym = r.stream().filter(x -> x.matches("[A-Z0-9]{3,10}")).findFirst().orElse("?");
                soft.assertThat(ceil >= tc && tc >= floor).as(sym + ": Trần " + ceil + " >= TC " + tc + " >= Sàn " + floor).isTrue();
            }
            soft.assertAll();
        } finally {
            board.pressBack();
        }
    }
}
