package vn.bsc.automation.tests.excel;

import vn.bsc.automation.data.OrderSpec;
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
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.market.TradingSession;
import vn.bsc.automation.data.OrderActionCase;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.data.OrderRegistry;
import vn.bsc.automation.data.OrderRegistry.PlacedOrder;
import vn.bsc.automation.flows.OrderFlow;
import vn.bsc.automation.flows.OrderResultReader;
import vn.bsc.automation.flows.OrderFlow.Outcome;
import vn.bsc.automation.screens.OrderBookScreen;
import vn.bsc.automation.screens.OrderFormScreen;

import org.assertj.core.api.SoftAssertions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.regex.Pattern;
import vn.bsc.automation.core.config.ConfigReader;

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
        // -Dorders.include=<regex on id>, e.g. "EX1[0-5].*" = conditional orders only
        Pattern include = Pattern.compile(
                ConfigReader.get("orders.include", ".*"));
        return OrderCase.load("testdata/orders.csv").stream()
                .filter(OrderCase::enabled).filter(c -> include.matcher(c.id()).matches())
                .filter(c -> !c.isConditional() && !c.isTpSl())
                .map(c -> new Object[]{c}).toArray(Object[][]::new);
    }

    /** Conditional orders (Stop Limit / Trailing Stop / OCO) and TP/SL - run AFTER the normal orders' modify/cancel. */
    @DataProvider(name = "conditionalOrders")
    public Object[][] conditionalOrders() {
        Pattern include = Pattern.compile(
                ConfigReader.get("orders.include", ".*"));
        return OrderCase.load("testdata/orders.csv").stream()
                .filter(OrderCase::enabled).filter(c -> include.matcher(c.id()).matches())
                .filter(c -> c.isConditional() || c.isTpSl())
                .map(c -> new Object[]{c}).toArray(Object[][]::new);
    }

    @DataProvider(name = "actions")
    public Object[][] actions() {
        // -Dactions.include=<regex on id>; "none" skips modify/cancel (e.g. when running a subset of orders)
        Pattern include = Pattern.compile(
                ConfigReader.get("actions.include", ".*"));
        return OrderActionCase.load("testdata/order_actions.csv").stream()
                .filter(OrderActionCase::enabled).filter(a -> include.matcher(a.id()).matches())
                // all MODIFY cases first (they need live orders), then CANCEL, CANCEL_ALL last; stable within a group
                .sorted(Comparator.comparingInt(a -> a.action().ordinal()))
                .map(a -> new Object[]{a}).toArray(Object[][]::new);
    }

    // ------------------------------------------------------------------ Excel No.2-5 (+ HNX/UPCOM/lô lẻ/phái sinh)

    @Test(dataProvider = "orders", priority = 1, groups = {"excel", "order", "real-order", "manual-input"})
    @Severity(SeverityLevel.BLOCKER)
    @Description("Đặt lệnh thường theo testdata/orders.csv: Giao dịch -> tiểu khoản -> mã CK -> giá -> KL -> Mua/Bán -> PIN -> Xác nhận")
    public void placeOrder(OrderCase c) {
        runOrder(c);
    }

    /** Priority 3: after placing (1) and modifying / cancelling (2) the normal orders. */
    @Test(dataProvider = "conditionalOrders", priority = 3, groups = {"excel", "order", "real-order", "manual-input"})
    @Severity(SeverityLevel.BLOCKER)
    @Description("Đặt lệnh điều kiện (Stop Limit / Trailing Stop / OCO) và TP/SL: thông báo thành công, Sổ lệnh tab Điều kiện "
            + "có lệnh trạng thái \"Chờ kích hoạt\"")
    public void placeConditionalOrder(OrderCase c) {
        runOrder(c);
    }

    private void runOrder(OrderCase c) {
        boolean inSession = TradingSession.isOpenNow(c.exchange(), c.sessions());
        Allure.parameter("Phiên hiện tại hợp lệ", inSession);
        Allure.parameter("Thời điểm", ZonedDateTime.now(TradingSession.VN).toString());

        // rows of this order already in the book (same symbol/side/qty/type) - to detect the new one afterwards
        OrderSpec probe = probeSpec(c);
        int rowsBefore = navigator.openTrading().openOrderBook().count(probe, OrderBookScreen.placedStatus(probe));

        Outcome out;
        try {
            out = c.isTpSl() ? orderFlow.placeTpSl(c) : orderFlow.place(c);
        } catch (RuntimeException | AssertionError e) {
            // an error AFTER "Xác nhận" can still leave a live order: register any new row so the safety net cancels it
            registerIfInBook(c, probe, rowsBefore, null, "Lỗi khi đặt lệnh: " + e.getMessage());
            throw e;
        }
        if (c.isTpSl() && orderFlow.lastTpSlTarget() != null) {
            c = orderFlow.lastTpSlTarget(); // verify / cancel the holding actually picked (may differ from the CSV symbol)
            Allure.parameter("Mã TP/SL thực tế", c.symbol() + " x" + c.quantity());
        }
        boolean messageCaptured = !out.message().isBlank();
        boolean registered = false;
        if (!out.success()) {
            // the order book is the source of truth: a new row means the order IS live, whatever the message said
            int rowsAfter = rowsInBook(probe);
            Allure.addAttachment("Sổ lệnh trước / sau", rowsBefore + " -> " + rowsAfter + " dòng " + probe);
            if (rowsAfter > rowsBefore) {
                if (!messageCaptured) {
                    out = new Outcome(true, "", out.price(), out.balanceBefore());
                } else {
                    LOG.error("[{}] App báo '{}' nhưng Sổ lệnh có lệnh mới {} - ghi nhận để tự huỷ", c.id(), out.message(), probe);
                    registerPlaced(c, out.price(), "Sổ lệnh có lệnh mới dù app báo: " + out.message());
                    registered = true;
                }
            }
        }
        PlacedOrder placed = null;
        if (out.success() && !registered) {
            // ATO/ATC/MTL have no price on the form: take it from "Đặt mua AAA KL 100 giá 7,460.00 thành công!"
            BigDecimal price = out.price() != null ? out.price() : OrderResultReader.priceFromMessage(out.message());
            placed = new PlacedOrder(c, c.subAccountName(), price, c.quantity(), LocalDateTime.now(), out.message());
            OrderRegistry.register(placed);
        }
        LOG.info("[{}] inSession={} success={} message='{}'", c.id(), inSession, out.success(), out.message());

        // System errors are always FAIL (never counted as the expected out-of-session rejection)
        assertThat(OrderResultReader.isSystemError(out.message()))
                .as("[%s] Lỗi hệ thống khi đặt lệnh: %s", c.id(), out.message()).isFalse();

        if (inSession) {
            assertThat(out.success()).as("[%s] Trong phiên: phải đặt lệnh thành công. Hệ thống trả: %s", c.id(), out.message())
                    .isTrue();
            verifyPlacedOrder(c, out, placed, true);
            return;
        }
        if (placed != null && c.outOfSession() != OrderCase.OutOfSession.REJECT) {
            verifyPlacedOrder(c, out, placed, false); // accepted outside the session: queued "Chờ gửi"
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
                OrderSpec oldSpec = OrderSpec.of(o);
                OrderBookScreen.Modified m = book.modify(o, a.priceSteps(), a.newQuantity(), new OrderFormScreen());
                Outcome out = orderFlow.readResult(o.price());
                // old order -> "Đã sửa"; a new order with the updated price / quantity -> "Đã gửi"/"Chờ gửi".
                // The registry follows the BOOK (not the message) BEFORE any assert, so later cancels and the safety
                // net always target the order that is really live.
                OrderSpec newSpec = oldSpec.withPriceAndQty(m.price(), m.quantity());
                OrderBookScreen after = navigator.openTrading().openOrderBook();
                Optional<UiNode> newRow = after.waitFor(newSpec, OrderBookScreen.STATUS_PLACED, BOOK_TIMEOUT);
                if (newRow.isPresent()) {
                    OrderRegistry.updateQuantity(o.source().id(), m.quantity());
                    if (m.price() != null) {
                        OrderRegistry.updatePrice(o.source().id(), m.price());
                    }
                }
                assertThat(OrderResultReader.isSystemError(out.message())).as("[%s] Lỗi hệ thống khi sửa lệnh: %s", a.id(), out.message()).isFalse();
                SoftAssertions soft = new SoftAssertions();
                soft.assertThat(out.success() || (out.message().isBlank() && newRow.isPresent()))
                        .as("[%s] Sửa lệnh thành công. Hệ thống trả: %s", a.id(), out.message()).isTrue();
                soft.assertThat(after.waitFor(oldSpec, OrderBookScreen.STATUS_MODIFIED, BOOK_TIMEOUT))
                        .as("[%s] Lệnh cũ %s chuyển trạng thái Đã sửa", a.id(), oldSpec).isPresent();
                soft.assertThat(newRow).as("[%s] Sinh lệnh mới %s (Đã gửi/Chờ gửi)", a.id(), newSpec).isPresent();
                soft.assertAll();
            }
            case CANCEL -> {
                PlacedOrder o = target(a);
                book.cancel(o);
                Outcome out = orderFlow.readResult(o.price());
                boolean cancelled = confirmCancelled(o);
                assertThat(OrderResultReader.isSystemError(out.message())).as("[%s] Lỗi hệ thống khi huỷ lệnh: %s", a.id(), out.message()).isFalse();
                assertThat(out.success()).as("[%s] Huỷ lệnh thành công. Hệ thống trả: %s", a.id(), out.message()).isTrue();
                assertThat(cancelled).as("[%s] Lệnh %s chuyển trạng thái Đã huỷ", a.id(), OrderSpec.of(o)).isTrue();
            }
            case CANCEL_ALL -> {
                List<PlacedOrder> pending = OrderRegistry.pending();
                if (pending.isEmpty()) {
                    throw new SkipException("Không có lệnh nào do test tạo đang chờ");
                }
                book.cancelAllOnlyTestOrders(pending);
                Outcome out = orderFlow.readResult(null);
                SoftAssertions soft = new SoftAssertions();
                for (PlacedOrder o : pending) { // each order is closed only when the book shows it cancelled
                    soft.assertThat(confirmCancelled(o))
                            .as("[%s] Lệnh %s (%s) chuyển trạng thái Đã huỷ", a.id(), o.source().id(), OrderSpec.of(o)).isTrue();
                }
                assertThat(OrderResultReader.isSystemError(out.message())).as("[%s] Lỗi hệ thống khi huỷ tất cả: %s", a.id(), out.message()).isFalse();
                soft.assertThat(out.success()).as("[%s] Huỷ tất cả thành công. Hệ thống trả: %s", a.id(), out.message()).isTrue();
                soft.assertAll();
            }
        }
    }

    /** True (and the order is closed in the registry) only when the book shows it "Đã huỷ". */
    private boolean confirmCancelled(PlacedOrder o) {
        OrderSpec spec = OrderSpec.of(o);
        boolean cancelled = navigator.openTrading().openOrderBook()
                .waitFor(spec, OrderBookScreen.STATUS_CANCELLED, BOOK_TIMEOUT).isPresent();
        if (cancelled) {
            OrderRegistry.markClosed(o.source().id());
        } else {
            LOG.error("Lệnh {} ({}) CHƯA thấy trạng thái Đã huỷ trong Sổ lệnh - vẫn coi là còn chờ", o.source().id(), spec);
        }
        return cancelled;
    }

    // ------------------------------------------------------------------ post-conditions

    private static final Duration BOOK_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BALANCE_TIMEOUT = Duration.ofSeconds(20);

    /**
     * An accepted order must:
     * (1) show the "mua / bán thành công" message of its side,
     * (2) appear in Sổ lệnh with the same symbol, side, price and quantity, status "Đã gửi"/"Chờ gửi"
     *     (normal order) or "Chờ kích hoạt" (conditional order, listed under the "Điều kiện" filter),
     * (3) NORMAL orders only: reduce Sức mua (buy) / the available stock balance = KL tối đa (sell).
     */
    private void verifyPlacedOrder(OrderCase c, Outcome out, PlacedOrder placed, boolean inSession) {
        SoftAssertions soft = new SoftAssertions();
        String side = c.side() == OrderCase.Side.BUY ? "mua" : "bán";

        if (out.message().isBlank()) {
            // order confirmed via the book; the app's success notice was too short-lived to be read
            Allure.addAttachment("(1) Thông báo thành công", "KHÔNG XÁC ĐỊNH: không bắt được thông báo (hiển thị quá nhanh); "
                    + "lệnh đã được xác nhận qua Sổ lệnh. Xem ảnh 'Sau khi Xác nhận +n'.");
        } else {
            soft.assertThat(OrderResultReader.isSideSuccessMessage(c.side(), out.message()))
                    .as("[%s] (1) Thông báo đặt lệnh %s thành công. Hệ thống trả: '%s'", c.id(), side, out.message()).isTrue();
        }

        OrderSpec spec = OrderSpec.of(placed);
        // conditional / TP/SL / LO-GTC: "Chờ kích hoạt"; normal: "Đã gửi" in session, "Chờ gửi" out of session
        Pattern status = c.inConditionalBook() ? OrderBookScreen.STATUS_CONDITIONAL_PLACED
                : inSession ? OrderBookScreen.STATUS_SENT : OrderBookScreen.STATUS_QUEUED;
        String expected = c.inConditionalBook() ? "Chờ kích hoạt" : inSession ? "Đã gửi" : "Chờ gửi";
        Optional<UiNode> row = navigator.openTrading().openOrderBook().waitFor(spec, status, BOOK_TIMEOUT);
        row.ifPresent(r -> Allure.addAttachment("Dòng lệnh trong Sổ lệnh", OrderSpec.text(r)));
        soft.assertThat(row).as("[%s] (2) Sổ lệnh có lệnh %s với trạng thái %s", c.id(), spec, expected).isPresent();

        // (3) business rule: normal BUY -> Sức mua decreases; normal SELL -> KL khả dụng decreases by the quantity;
        //     conditional orders (incl. LO-GTC) -> balance UNCHANGED (nothing is held until activation)
        if (c.inConditionalBook() && out.balanceBefore() != null) {
            long before = out.balanceBefore();
            long after = orderFlow.readBalanceAfter(c, before, Duration.ofSeconds(6)); // no change expected
            String what = c.side() == OrderCase.Side.BUY ? "Sức mua" : "KL khả dụng";
            Allure.addAttachment(what + " trước / sau (lệnh điều kiện)", before + " -> " + after);
            soft.assertThat(after).as("[%s] (3) Lệnh điều kiện: %s không đổi (trước %,d)", c.id(), what, before)
                    .isEqualTo(before);
        }
        boolean checkBalance = !c.inConditionalBook();
        if (checkBalance && out.balanceBefore() != null) {
            long before = out.balanceBefore();
            long after = orderFlow.readBalanceAfter(c, before, BALANCE_TIMEOUT);
            Allure.addAttachment((c.side() == OrderCase.Side.BUY ? "Sức mua" : "KL khả dụng") + " trước / sau",
                    before + " -> " + after);
            if (c.side() == OrderCase.Side.BUY) {
                soft.assertThat(after).as("[%s] (3) Sức mua phải giảm sau lệnh mua (trước %,d)", c.id(), before).isLessThan(before);
            } else {
                soft.assertThat(after).as("[%s] (3) Số dư CK khả dụng phải giảm %,d sau lệnh bán (trước %,d)",
                        c.id(), c.quantity(), before).isEqualTo(before - c.quantity());
            }
        } else if (checkBalance) {
            soft.fail("[%s] (3) Không đọc được số dư trước khi đặt lệnh", c.id());
        }
        soft.assertAll();
    }

    private int rowsInBook(OrderSpec probe) {
        return navigator.openTrading().openOrderBook().count(probe, OrderBookScreen.placedStatus(probe));
    }

    private void registerPlaced(OrderCase c, BigDecimal price, String note) {
        OrderRegistry.register(new PlacedOrder(c, c.subAccountName(), price, c.quantity(), LocalDateTime.now(), note));
    }

    /** After a failed / interrupted placement: registers the order when a new row of it is in the book. */
    private void registerIfInBook(OrderCase c, OrderSpec probe, int rowsBefore, BigDecimal price, String note) {
        try {
            OrderCase target = c.isTpSl() && orderFlow.lastTpSlTarget() != null ? orderFlow.lastTpSlTarget() : c;
            if (rowsInBook(probe) > rowsBefore) {
                LOG.error("[{}] {} - Sổ lệnh có lệnh mới {}: ghi nhận để tự huỷ", c.id(), note, probe);
                registerPlaced(target, price, note);
            }
        } catch (RuntimeException | AssertionError check) {
            LOG.error("[{}] Không kiểm tra được Sổ lệnh sau lỗi đặt lệnh ({}) - CẦN KIỂM TRA TAY", c.id(), check.getMessage());
        }
    }

    /** Book identity of a row BEFORE the price is known (normal orders: any price). */
    private static OrderSpec probeSpec(OrderCase c) {
        boolean buy = c.side() == OrderCase.Side.BUY;
        if (c.inConditionalBook()) {
            return new OrderSpec(c.symbol(), buy, c.quantity(), null,
                    OrderSpec.conditionalLabel(c.orderType()), true);
        }
        return new OrderSpec(c.symbol(), buy, c.quantity(), null, c.isMarketType() ? c.orderType() : null, false);
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
                orderFlow.readResult(o.price());
                if (!confirmCancelled(o)) {
                    LOG.error("KHÔNG xác nhận được lệnh {} đã huỷ - cần kiểm tra / huỷ tay trong Sổ lệnh", o.source().id());
                }
            } catch (Exception | AssertionError e) {
                LOG.error("KHÔNG huỷ được lệnh {} - cần huỷ tay trong Sổ lệnh: {}", o.source().id(), e.getMessage());
            }
        }
    }
}
