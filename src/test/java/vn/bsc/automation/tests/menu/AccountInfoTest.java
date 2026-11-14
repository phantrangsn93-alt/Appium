package vn.bsc.automation.tests.menu;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.MenuSequenceBaseTest;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.data.TestDataReader;
import vn.bsc.automation.screens.MenuItem;
import vn.bsc.automation.screens.MenuScreen;
import vn.bsc.automation.screens.MenuTargetScreen;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * READ-ONLY: account management / utilities / settings screens of the menu show their fields, columns and data.
 * Nothing is changed or saved. Data: testdata/account_screens.json.
 */
@Epic("BSC Mobile Trading")
@Feature("Quản lý tài khoản / Cài đặt (chỉ đọc)")
public class AccountInfoTest extends MenuSequenceBaseTest {

    @DataProvider(name = "screens")
    public Object[][] screens() {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode s : TestDataReader.read("account_screens.json").get("screens")) {
            rows.add(new Object[]{s.get("id").asText(), s.get("menuItem").asText(), s});
        }
        return rows.toArray(new Object[0][]);
    }

    /** The menu is already open (MenuSequenceBaseTest): open the item directly. */
    private static MenuTargetScreen open(MenuItem item) {
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(item);
        return new MenuTargetScreen(item).waitUntilLoaded();
    }

    /** A line equals the label or starts with it (labels carry ":" / values on the same node). */
    private static boolean hasLabel(List<String> lines, String label) {
        return lines.stream().anyMatch(l -> l.equals(label) || l.startsWith(label + ":") || l.startsWith(label + " ")
                || l.startsWith(label));
    }

    @Test(groups = {"regression", "readonly"}, dataProvider = "screens")
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_ACC/TC_UTIL/TC_SET - Màn Quản lý tài khoản / Tiện ích / Cài đặt hiển thị đủ trường, cột và dữ liệu (chỉ xem)")
    public void accountScreenShowsFields(String id, String menuItem, JsonNode spec) {
        MenuTargetScreen screen = open(MenuItem.valueOf(menuItem));
        boolean privacy = spec.path("privacy").asBoolean(false);
        if (!privacy) {
            AllureUtils.attachScreenshot(id + " - " + spec.get("title").asText());
        }
        SoftAssertions soft = new SoftAssertions();
        List<String> top = screen.lines(); // before scrolling (statement totals are at the top)
        List<String> lines = screen.collectTexts(3);
        for (JsonNode e : spec.path("expect")) {
            soft.assertThat(hasLabel(lines, e.asText())).as("[%s] có trường/cột \"%s\"", id, e.asText()).isTrue();
            if (spec.path("valuesRequired").asBoolean(false)) {
                String value = valueAfter(lines, e.asText());
                soft.assertThat(value).as("[%s] trường \"%s\" có giá trị", id, e.asText()).isNotBlank();
            }
        }
        if (spec.path("balanceCheck").asBoolean(false)) {
            // same visit: Đầu kỳ + Phát sinh tăng - Phát sinh giảm = Cuối kỳ (lines() keeps equal values)
            List<String> l = top;
            BigDecimal open = amount(valueAfter(l, "Đầu kỳ")), in = amount(valueAfter(l, "Phát sinh tăng"));
            BigDecimal out = amount(valueAfter(l, "Phát sinh giảm")), close = amount(valueAfter(l, "Cuối kỳ"));
            soft.assertThat(open.add(in).subtract(out.abs())).as("[%s] Đầu kỳ %s + tăng %s - giảm %s = Cuối kỳ %s",
                    id, open, in, out.abs(), close).isEqualByComparingTo(close);
        }
        if (spec.path("rows").asBoolean(false)) {
            soft.assertThat(screen.rows()).as("[%s] có dữ liệu (trống => FAIL)", id).isNotEmpty();
        }
        soft.assertAll();
    }

    /** Value printed after a label: next line (separate node) or the rest of a "label: value" line. */
    private static String valueAfter(List<String> lines, String label) {
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.equals(label) && i + 1 < lines.size()) {
                return lines.get(i + 1);
            }
            if (l.startsWith(label + ":")) {
                String rest = l.substring(label.length() + 1).trim();
                return rest.isEmpty() && i + 1 < lines.size() ? lines.get(i + 1) : rest;
            }
        }
        return "";
    }

    /** "+2,376,958" / "-10,000" / "0" -> number. */
    static BigDecimal amount(String text) {
        String t = text == null ? "" : text.replaceAll("[^0-9\\-]", "");
        if (t.isEmpty() || "-".equals(t)) {
            throw new AssertionError("Không đọc được số tiền: '" + text + "'");
        }
        return new BigDecimal(t);
    }
}
