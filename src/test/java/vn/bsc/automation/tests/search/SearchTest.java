package vn.bsc.automation.tests.search;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.data.TestDataReader;
import vn.bsc.automation.screens.SearchScreen;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("BSC Mobile Trading")
@Feature("Tìm kiếm")
public class SearchTest extends LoggedInBaseTest {

    @DataProvider(name = "stocks")
    public Object[][] stocks() {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode n : TestDataReader.read("search.json").get("stocks")) {
            rows.add(new Object[]{n.get("keyword").asText(), n.get("symbol").asText(), n.get("companyContains").asText()});
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(groups = {"regression"})
    @Severity(SeverityLevel.MINOR)
    @Description("TC_SEARCH_01 - Màn tìm kiếm có 3 nhóm: Chứng khoán / Tin tức / Chức năng")
    public void searchCategoriesVisible() {
        SearchScreen search = navigator.openSearch();
        SoftAssertions soft = new SoftAssertions();
        for (String c : SearchScreen.CATEGORIES) {
            soft.assertThat(search.isCategoryVisible(c)).as(c).isTrue();
        }
        soft.assertAll();
    }

    @Test(groups = {"smoke", "regression"}, dataProvider = "stocks")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_SEARCH_02 - Tìm theo mã CK trả về đúng mã và tên công ty")
    public void searchStockBySymbol(String keyword, String symbol, String companyContains) {
        SearchScreen search = navigator.openSearch().search(keyword);
        assertThat(search.isStockResultVisible(symbol)).as("Kết quả " + symbol).isTrue();
        assertThat(search.getStockResultText(symbol)).contains(companyContains);
    }
}
