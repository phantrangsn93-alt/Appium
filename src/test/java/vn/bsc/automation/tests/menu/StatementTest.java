package vn.bsc.automation.tests.menu;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.MenuSequenceBaseTest;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.screens.MenuItem;
import vn.bsc.automation.screens.MenuScreen;
import vn.bsc.automation.screens.StatementScreen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sao kê tiền / Sao kê chứng khoán: defaults, date range rules (max 6 months, from &lt;= to), totals vs rows,
 * sub-account switch. READ-ONLY. Runs from the open menu (MenuSequenceBaseTest).
 */
@Epic("BSC Mobile Trading")
@Feature("Quản lý tài khoản – Sao kê")
public class StatementTest extends MenuSequenceBaseTest {

    @DataProvider(name = "statements")
    public Object[][] statements() {
        return new Object[][]{{"TC_SKT", MenuItem.SAO_KE_TIEN}, {"TC_SKCK", MenuItem.SAO_KE_CHUNG_KHOAN}};
    }

    private static StatementScreen open(MenuItem item) {
        MenuScreen menu = new MenuScreen().waitUntilLoaded();
        menu.open(item);
        StatementScreen s = new StatementScreen(item);
        s.waitUntilLoaded();
        return s;
    }

    @Test(groups = {"real-transaction", "readonly"}, dataProvider = "statements", priority = 1)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_SKT_01 / TC_SKCK_01 - Mặc định: Từ ngày = Đến ngày = ngày làm việc hiện tại")
    public void defaultDateRange(String prefix, MenuItem item) {
        StatementScreen s = open(item);
        LocalDate from = s.fromDate(), to = s.toDate(), today = LocalDate.now();
        AllureUtils.attachScreenshot(prefix + " mặc định");
        assertThat(from).as("[%s_01] Từ ngày = Đến ngày", prefix).isEqualTo(to);
        // business date: today, or the next working day after the end-of-day batch (weekend / evening)
        assertThat(to).as("[%s_01] Ngày mặc định là ngày làm việc hiện tại (%s)", prefix, today)
                .isBetween(today.minusDays(1), today.plusDays(3));
    }

    @Test(groups = {"real-transaction", "readonly"}, dataProvider = "statements", priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_SKT_06 / TC_SKCK_05 (+ TC_SKT_04/05) - Từ ngày lùi 1 tháng (có dữ liệu): dòng nằm trong khoảng ngày; Sao kê tiền: số dư và tổng dòng khớp")
    public void oneMonthRange(String prefix, MenuItem item) {
        StatementScreen s = open(item);
        LocalDate to = s.toDate(), from = to.minusMonths(1); // 1 month back: there is data to check
        s.setFrom(from);
        Optional<String> err = s.validationMessage();
        assertThat(err).as("[%s] Khoảng 1 tháng không bị báo lỗi", prefix).isEmpty();
        AllureUtils.attachScreenshot(prefix + " 1 tháng");
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(s.fromDate()).as("Từ ngày đã đổi").isEqualTo(from);
        List<List<String>> rows = s.rowsOnScreen();
        for (List<String> r : rows) {
            LocalDate d = LocalDate.parse(r.get(0), StatementScreen.DMY);
            soft.assertThat(d).as("[%s] Dòng %s nằm trong khoảng %s – %s", prefix, r, from, to).isBetween(from, to);
        }
        if (item == MenuItem.SAO_KE_TIEN) {
            BigDecimal open = s.total("Đầu kỳ"), in = s.total("Phát sinh tăng");
            BigDecimal out = s.total("Phát sinh giảm").abs(), close = s.total("Cuối kỳ");
            soft.assertThat(open.add(in).subtract(out)).as("[TC_SKT_03] Đầu kỳ + tăng - giảm = Cuối kỳ").isEqualByComparingTo(close);
            if (!rows.isEmpty() && rows.size() < 6) { // all rows on one screen: totals must equal the rows
                BigDecimal plus = BigDecimal.ZERO, minus = BigDecimal.ZERO;
                for (List<String> r : rows) {
                    BigDecimal a = StatementScreen.amount(r.get(r.size() - 1));
                    if (a.signum() >= 0) {
                        plus = plus.add(a);
                    } else {
                        minus = minus.add(a.abs());
                    }
                }
                soft.assertThat(plus).as("[TC_SKT_04] Tổng dòng dương = Phát sinh tăng").isEqualByComparingTo(in);
                soft.assertThat(minus).as("[TC_SKT_04] Tổng dòng âm = Phát sinh giảm").isEqualByComparingTo(out);
            }
        }
        soft.assertAll();
    }

    @Test(groups = {"real-transaction", "readonly"}, dataProvider = "statements", priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_SKT_07 / TC_SKCK_06 - Từ ngày lớn hơn Đến ngày: báo lỗi hoặc không cho chọn")
    public void fromAfterTo(String prefix, MenuItem item) {
        StatementScreen s = open(item);
        LocalDate to = s.toDate();
        s.setFrom(to.plusDays(1));
        Optional<String> err = s.validationMessage();
        AllureUtils.attachScreenshot(prefix + " Từ ngày > Đến ngày");
        assertThat(err.isPresent() || !s.fromDate().isAfter(s.toDate()))
                .as("[%s] Từ ngày > Đến ngày phải bị chặn (thông báo: %s)", prefix, err.orElse("không có")).isTrue();
    }

    @Test(groups = {"real-transaction", "readonly"}, dataProvider = "statements", priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_SKT_08 / TC_SKCK_06b - Khoảng ngày vượt 6 tháng: báo giới hạn tối đa 6 tháng")
    public void moreThanSixMonths(String prefix, MenuItem item) {
        StatementScreen s = open(item);
        LocalDate to = s.toDate();
        s.setFrom(to.minusMonths(6).minusDays(1));
        Optional<String> err = s.validationMessage();
        AllureUtils.attachScreenshot(prefix + " > 6 tháng");
        assertThat(err.isPresent() || !s.fromDate().isBefore(to.minusMonths(6)))
                .as("[%s] Khoảng > 6 tháng phải bị chặn (thông báo: %s)", prefix, err.orElse("không có")).isTrue();
    }

    @Test(groups = {"real-transaction", "readonly"}, dataProvider = "statements", priority = 5)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_SKT_08b - Khoảng ngày đúng 6 tháng: tra cứu được, không báo lỗi")
    public void exactlySixMonths(String prefix, MenuItem item) {
        StatementScreen s = open(item);
        LocalDate to = s.toDate(), from = to.minusMonths(6);
        s.setFrom(from);
        Optional<String> err = s.validationMessage();
        AllureUtils.attachScreenshot(prefix + " = 6 tháng");
        assertThat(err).as("[%s] Khoảng đúng 6 tháng không bị báo lỗi", prefix).isEmpty();
        assertThat(s.fromDate()).as("[%s] Từ ngày = %s", prefix, from).isEqualTo(from);
    }

    @Test(groups = {"real-transaction", "readonly"}, dataProvider = "statements", priority = 6)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_SKT_09 / TC_SKCK_07 - Đổi tiểu khoản: màn hiển thị theo tiểu khoản mới")
    public void switchSubAccount(String prefix, MenuItem item) {
        StatementScreen s = open(item);
        String before = s.fieldValue("Tiểu khoản");
        String after = s.switchSubAccount();
        if (after == null) {
            throw new SkipException("Tài khoản chỉ có 1 tiểu khoản");
        }
        AllureUtils.attachScreenshot(prefix + " đổi tiểu khoản");
        assertThat(after).as("[%s] Tiểu khoản đã đổi", prefix).isNotEqualTo(before);
        if (item == MenuItem.SAO_KE_TIEN) {
            assertThat(s.lines()).as("[TC_SKT_09] Số liệu tổng hợp của tiểu khoản mới").anySatisfy(l -> assertThat(l).startsWith("Cuối kỳ"));
        }
    }
}
