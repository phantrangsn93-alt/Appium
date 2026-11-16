package vn.bsc.automation.tests.menu;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.SkipException;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.data.MenuTransactionCase;
import vn.bsc.automation.data.OrderRegistry;
import vn.bsc.automation.data.OrderRegistry.PlacedOrder;
import vn.bsc.automation.data.TransactionLog;
import vn.bsc.automation.screens.MenuItem;
import vn.bsc.automation.screens.MenuTargetScreen;
import vn.bsc.automation.screens.OrderBookScreen;
import vn.bsc.automation.data.OrderSpec;
import vn.bsc.automation.screens.components.AppHeader;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import vn.bsc.automation.data.OrderCase;

/**
 * Last step of the TRANSACTION suite: after the orders and the menu transactions of this run, the order book
 * holds no live test order and the history / statement screens show what was done. Read-only.
 */
@Epic("BSC Mobile Trading")
@Feature("Giao dịch - đối chiếu sau giao dịch")
public class PostTransactionCheckTest extends LoggedInBaseTest {

    @Test(groups = {"real-transaction"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_POST_01 - Sổ lệnh không còn lệnh nào do test tạo đang chờ khớp / chờ kích hoạt")
    public void noLiveTestOrderLeft() {
        List<PlacedOrder> placed = OrderRegistry.all();
        if (placed.isEmpty()) {
            throw new SkipException("Lần chạy này không đặt lệnh nào");
        }
        OrderBookScreen book = navigator.openTrading().openOrderBook();
        SoftAssertions soft = new SoftAssertions();
        for (PlacedOrder o : placed) {
            OrderSpec spec = OrderSpec.of(o);
            soft.assertThat(book.count(spec, OrderBookScreen.placedStatus(spec)))
                    .as("[%s] %s còn đang chờ trên Sổ lệnh", o.source().id(), spec).isZero();
        }
        AllureUtils.attachScreenshot("Sổ lệnh sau giao dịch");
        soft.assertThat(OrderRegistry.pending()).as("Lệnh test chưa huỷ").isEmpty();
        soft.assertAll();
    }

    @Test(groups = {"real-transaction"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_POST_02 - Lịch sử đặt lệnh ghi nhận các lệnh thường vừa đặt (mã, M/B, KL)")
    public void orderHistoryShowsTodaysOrders() {
        List<PlacedOrder> normal = OrderRegistry.all().stream().filter(o -> !o.source().inConditionalBook()).toList();
        if (normal.isEmpty()) {
            throw new SkipException("Không có lệnh thường nào được đặt trong lần chạy này");
        }
        new AppHeader().openMenu().open(MenuItem.LICH_SU_DAT_LENH);
        MenuTargetScreen history = new MenuTargetScreen(MenuItem.LICH_SU_DAT_LENH).waitUntilLoaded();
        AllureUtils.attachScreenshot("Lịch sử đặt lệnh");
        List<String> rows = history.rows();
        SoftAssertions soft = new SoftAssertions();
        for (PlacedOrder o : normal) {
            String qty = NumberFormat.getIntegerInstance(Locale.US).format(o.source().quantity());
            String side = o.source().side() == OrderCase.Side.BUY ? "Mua" : "Bán";
            soft.assertThat(rows).as("[%s] Lịch sử có lệnh %s %s KL %s", o.source().id(), side, o.source().symbol(), qty)
                    .anySatisfy(r -> assertThat(r).startsWith(o.source().symbol()).containsIgnoringCase(side).contains(qty));
        }
        soft.assertAll();
    }

    @Test(groups = {"real-transaction"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_POST_03 - Sao kê tiền ghi nhận giao dịch chuyển tiền nội bộ vừa thực hiện (số tiền đúng)")
    public void cashStatementShowsTransfer() {
        MenuTransactionCase transfer = internalTransfer() == null ? null : internalTransfer().c();
        if (transfer == null) {
            throw new SkipException("Chuyển tiền nội bộ không thành công / không chạy trong lần này");
        }
        new AppHeader().openMenu().open(MenuItem.SAO_KE_TIEN);
        MenuTargetScreen statement = new MenuTargetScreen(MenuItem.SAO_KE_TIEN).waitUntilLoaded();
        AllureUtils.attachScreenshot("Sao kê tiền sau giao dịch");
        String amount = NumberFormat.getIntegerInstance(Locale.US).format(Long.parseLong(transfer.amount()));
        assertThat(statement.collectTexts(2)).as("Sao kê có dòng chuyển khoản nội bộ -%s", amount)
                .anySatisfy(l -> assertThat(l).contains(amount));
    }

    private static TransactionLog.Entry internalTransfer() {
        return TransactionLog.all().values().stream()
                .filter(e -> "CHUYEN_TIEN_NOI_BO".equals(e.c().option())).findFirst().orElse(null);
    }

    @Test(groups = {"real-transaction"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_POST_04 - Chuyển tiền nội bộ: Sao kê tiền của tiểu khoản NHẬN có phát sinh tăng đúng số tiền")
    public void receivingSubAccountIsCredited() {
        TransactionLog.Entry e = internalTransfer();
        if (e == null || e.receiver() == null) {
            throw new SkipException("Không có chuyển tiền nội bộ thành công / không đọc được tiểu khoản nhận");
        }
        new AppHeader().openMenu().open(MenuItem.SAO_KE_TIEN);
        MenuTargetScreen statement = new MenuTargetScreen(MenuItem.SAO_KE_TIEN).waitUntilLoaded();
        // the receiving sub-account number identifies it in the dropdown ("MR90 BSC ĐB - 0101098819")
        String number = e.receiver().replaceAll(".*-\\s*", "").trim();
        statement.selectFieldOption("Tiểu khoản", number);
        String amount = NumberFormat.getIntegerInstance(Locale.US).format(Long.parseLong(e.c().amount()));
        AllureUtils.attachScreenshot("Sao kê tiền - tiểu khoản nhận");
        List<String> lines = statement.collectTexts(2);
        assertThat(lines).as("Sao kê tiểu khoản nhận có dòng +%s", amount)
                .anySatisfy(l -> assertThat(l).contains(amount).doesNotContain("-" + amount));
    }
}
