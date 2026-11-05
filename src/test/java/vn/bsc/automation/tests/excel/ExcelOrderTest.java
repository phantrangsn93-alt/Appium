package vn.bsc.automation.tests.excel;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.market.TradingSession;
import vn.bsc.automation.data.OrderActionCase;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.data.OrderRegistry;
import vn.bsc.automation.data.OrderRegistry.PlacedOrder;
import vn.bsc.automation.flows.OrderFlow;
import vn.bsc.automation.flows.OrderFlow.Outcome;
import vn.bsc.automation.screens.OrderBookScreen;
import vn.bsc.automation.screens.OrderFormScreen;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excel "Kiểm tra Lệnh" No.2-8 - DATA-DRIVEN:
 * <ul>
 *   <li>{@code testdata/orders.csv}        one row per order (exchange, sub-account, side, order type, price, qty, session)</li>
 *   <li>{@code testdata/order_actions.csv} modify / cancel / cancel-all on those orders</li>
 * </ul>
 * Expected result per row: inside its session -> "Đặt lệnh thành công"; outside -> the system rejects it
 * (PASS, message recorded) unless the row says outOfSession=ACCEPT/ANY.
 * <p>
 * REAL ORDERS: only with {@code -Dorder.submit.enabled=true}. The tester types the PIN on the phone.
 * Every order placed by the run is cancelled at the end (safety net) if still pending.
 * Runs single-threaded (one account / one order book).
 */
@Epic("Kịch bản Excel - sau khi update App")
@Feature("Kiểm tra Lệnh")
@Test(singleThreaded = true)
public class ExcelOrderTest extends LoggedInBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelOrderTest.class);
    private final OrderFlow orderFlow = new OrderFlow();

    @BeforeClass(alwaysRun = true)
    public void requireRealOrderOptIn() {
        if (!AppConfig.orderSubmitEnabled()) {
            throw new SkipException("Đặt lệnh THẬT đang khoá. Chạy với: -Dorder.submit.enabled=true");
        }
    }

    @DataProvider(name = "orders")
    public Object[][] orders() {
        return OrderCase.load("testdata/orders.csv").stream()
                .filter(OrderCase::enabled).map(c -> new Object[]{c}).toArray(Object[][]::new);
    }

    @DataProvider(name = "actions")
    public Object[][] actions() {
        return OrderActionCase.load("testdata/order_actions.csv").stream()
                .filter(OrderActionCase::enabled).map(a -> new Object[]{a}).toArray(Object[][]::new);
    }

    // ------------------------------------------------------------------ Excel No.2-5 (+ HNX/UPCOM/lô lẻ/phái sinh)

    @Test(dataProvider = "orders", priority = 1, groups = {"excel", "order", "real-order", "manual-input"})
    @Severity(SeverityLevel.BLOCKER)
    @Description("Đặt lệnh theo testdata/orders.csv: Giao dịch -> tiểu khoản -> mã CK -> giá -> KL -> Mua/Bán -> PIN -> Xác nhận")
    public void placeOrder(OrderCase c) {
        boolean inSession = TradingSession.isOpenNow(c.exchange(), c.sessions());
        Allure.parameter("Phiên hiện tại hợp lệ", inSession);
        Allure.parameter("Thời điểm", ZonedDateTime.now(TradingSession.VN).toString());

        Outcome out = orderFlow.place(c);
        if (out.success()) {
            OrderRegistry.register(new PlacedOrder(c, c.subAccount(), out.price(), c.quantity(), LocalDateTime.now(), out.message()));
        }
        LOG.info("[{}] inSession={} success={} message='{}'", c.id(), inSession, out.success(), out.message());

        if (inSession) {
            assertThat(out.success()).as("[%s] Trong phiên: phải đặt lệnh thành công. Hệ thống trả: %s", c.id(), out.message())
                    .isTrue();
            return;
        }
        // Out of session (e.g. ATO/ATC/MTL placed in the wrong session)
        switch (c.outOfSession()) {
            case REJECT -> {
                assertThat(out.success()).as("[%s] Ngoài phiên: hệ thống phải từ chối lệnh, nhưng lại báo: %s", c.id(), out.message())
                        .isFalse();
                assertThat(out.message()).as("[%s] Ngoài phiên: phải có thông báo lỗi", c.id()).isNotBlank();
            }
            case ACCEPT -> assertThat(out.success()).as("[%s] Ngoài phiên: kỳ vọng hệ thống nhận lệnh. Trả: %s",
                    c.id(), out.message()).isTrue();
            case ANY -> assertThat(out.message()).as("[%s] phải có phản hồi từ hệ thống", c.id()).isNotBlank();
        }
    }

    // ------------------------------------------------------------------ Excel No.6-8

    @Test(dataProvider = "actions", priority = 2, groups = {"excel", "order", "real-order", "manual-input"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("Sửa / Huỷ / Huỷ tất cả theo testdata/order_actions.csv (chỉ trên lệnh do test tạo)")
    public void orderAction(OrderActionCase a) {
        OrderBookScreen book = navigator.openTrading().openOrderBook();
        switch (a.action()) {
            case MODIFY -> {
                PlacedOrder o = target(a);
                book.modify(o, a.priceSteps(), new OrderFormScreen());
                Outcome out = orderFlow.readResult(o.price());
                assertThat(out.success()).as("[%s] Sửa lệnh thành công. Hệ thống trả: %s", a.id(), out.message()).isTrue();
            }
            case CANCEL -> {
                PlacedOrder o = target(a);
                book.cancel(o);
                Outcome out = orderFlow.readResult(o.price());
                assertThat(out.success()).as("[%s] Huỷ lệnh thành công. Hệ thống trả: %s", a.id(), out.message()).isTrue();
                OrderRegistry.markClosed(o.source().id());
            }
            case CANCEL_ALL -> {
                List<PlacedOrder> pending = OrderRegistry.pending();
                if (pending.isEmpty()) {
                    throw new SkipException("Không có lệnh nào do test tạo đang chờ");
                }
                book.cancelAllOnlyTestOrders(pending);
                Outcome out = orderFlow.readResult(null);
                assertThat(out.success()).as("[%s] Huỷ tất cả thành công. Hệ thống trả: %s", a.id(), out.message()).isTrue();
                pending.forEach(o -> OrderRegistry.markClosed(o.source().id()));
            }
        }
    }

    private PlacedOrder target(OrderActionCase a) {
        return OrderRegistry.get(a.targetId())
                .filter(o -> OrderRegistry.pending().contains(o))
                .orElseThrow(() -> new SkipException("Lệnh gốc " + a.targetId()
                        + " chưa được đặt thành công / đã huỷ (vd. đặt ngoài phiên) - bỏ qua " + a.id()));
    }

    // ------------------------------------------------------------------ safety net

    /** Cancels every order of this run that is still pending (each cancel asks the tester for the PIN). */
    @AfterClass(alwaysRun = true)
    public void cancelLeftoverTestOrders() {
        List<PlacedOrder> left = OrderRegistry.pending();
        if (left.isEmpty() || !AppConfig.orderSubmitEnabled()) {
            return;
        }
        LOG.warn("Safety net: huỷ {} lệnh còn chờ do test tạo: {}", left.size(), left.stream().map(o -> o.source().id()).toList());
        for (PlacedOrder o : left) {
            try {
                OrderBookScreen book = navigator.openTrading().openOrderBook();
                book.cancel(o);
                if (orderFlow.readResult(o.price()).success()) {
                    OrderRegistry.markClosed(o.source().id());
                }
            } catch (Exception | AssertionError e) {
                LOG.error("KHÔNG huỷ được lệnh {} - cần huỷ tay trong Sổ lệnh: {}", o.source().id(), e.getMessage());
            }
        }
    }
}
