package vn.bsc.automation.flows;

import io.qameta.allure.Step;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.data.PriceSpec;
import vn.bsc.automation.screens.OrderFormScreen;
import vn.bsc.automation.screens.components.OrderConfirmDialog;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.screens.PortfolioScreen;
import vn.bsc.automation.screens.components.TpSlSheet;

/** Business flow: fill the order form from an {@link OrderCase} row and submit it (real order). */
public class OrderFlow {

    /**
     * Result of a submit: whether the app reported success, its message, the order price and the balance the
     * order consumes measured right BEFORE submitting (BUY: Sức mua VND, SELL: KL tối đa = available shares;
     * null when not captured).
     */
    public record Outcome(boolean success, String message, BigDecimal price, Long balanceBefore) {
        public Outcome(boolean success, String message, BigDecimal price) {
            this(success, message, price, null);
        }

        Outcome withBalance(Long b) {
            return new Outcome(success, message, price, b);
        }
    }

    /**
     * Re-reads the balance of the order's side for its sub-account / symbol on the order form, waiting up to
     * {@code timeout} for it to differ from {@code before} (the app refreshes it asynchronously).
     */
    @Step("Đọc lại sức mua / KL khả dụng sau khi đặt lệnh {c}")
    public long readBalanceAfter(OrderCase c, long before, Duration timeout) {
        OrderFormScreen form = navigator.openTrading().openOrderForm();
        form.selectSubAccount(c.subAccountName());
        if (c.side() == OrderCase.Side.BUY) {
            form.selectBuy();
        } else {
            form.selectSell();
        }
        form.selectSymbol(c.symbol());
        OrderFormScreen.Side side = c.side() == OrderCase.Side.BUY ? OrderFormScreen.Side.BUY : OrderFormScreen.Side.SELL;
        long[] now = {before};
        WaitUtils.condition(() -> {
            try {
                now[0] = form.getAvailableBalance(side);
            } catch (AssertionError e) {
                return false;
            }
            return now[0] != before;
        }, timeout);
        return now[0];
    }

    private final Navigator navigator = new Navigator();

    /** Excel steps 2-7: Giao dịch -> tiểu khoản -> Mua/Bán -> mã CK -> giá -> khối lượng (NO submit). */
    @Step("Điền form đặt lệnh: {c}")
    public FilledForm fill(OrderCase c) {
        OrderFormScreen form = navigator.openTrading().openOrderForm();
        form.selectSubAccount(c.subAccountName());
        if (c.side() == OrderCase.Side.BUY) {
            form.selectBuy();
        } else {
            form.selectSell();
        }
        form.selectSymbol(c.symbol()); // NOTE: selecting a symbol resets the form to Limit -> order type after it
        BigDecimal price = null;
        if (c.isConditional()) {
            price = fillConditional(form, c);
        } else if (c.isMarketType()) {
            form.selectMarketPriceType(c.orderType());
        } else {
            form.selectOrderType(OrderFormScreen.OrderType.LIMIT);
            price = resolvePrice(form, c.price());
            form.enterPrice(price.toPlainString());
        }
        form.enterQuantity(c.quantity());
        // always explicit: the form keeps the previous order's validity. Date picker is never opened.
        form.selectValidity(c.isMultiDay() ? 1 : 0);
        form.scrollToTop();
        AllureUtils.attachScreenshot("Form " + c.id());
        return new FilledForm(form, price);
    }

    /**
     * Excel "Đặt lệnh Stop limit / Trailing Stop / OCO": Điều kiện -> Kiểu lệnh -> fields.
     * Returns the main price of the order (OCO: LO leg, Stop Limit: trigger) for reporting / order-book matching.
     */
    private BigDecimal fillConditional(OrderFormScreen form, OrderCase c) {
        OrderFormScreen.ConditionalType type = OrderFormScreen.ConditionalType.valueOf(c.orderType());
        form.selectConditionalType(type);
        switch (type) {
            case STOP_LIMIT -> {
                BigDecimal trigger = resolve(form, c.triggerPrice(), "Giá kích hoạt", 0);
                form.enterConditional(OrderFormScreen.CondField.TRIGGER, trigger.toPlainString());
                if (!c.price().isBlank()) { // empty = keep "Giá đặt" auto-filled by the app
                    form.enterConditional(OrderFormScreen.CondField.ORDER_PRICE,
                            resolve(form, c.price(), "Giá đặt", 0).toPlainString());
                }
                return trigger;
            }
            case TRAILING_STOP -> {
                form.enterConditional(OrderFormScreen.CondField.AMPLITUDE, c.amplitude());
                form.enterConditional(OrderFormScreen.CondField.PRICE_STEP, c.priceStep());
                return null;
            }
            case OCO -> {
                BigDecimal lo = resolve(form, c.price(), "Giá đặt", 0);
                form.enterConditional(OrderFormScreen.CondField.LO_PRICE, lo.toPlainString());
                BigDecimal trigger = resolve(form, c.triggerPrice(), "Giá kích hoạt", 0);
                form.enterConditional(OrderFormScreen.CondField.TRIGGER, trigger.toPlainString());
                return lo;
            }
            default -> throw new IllegalStateException(type.toString());
        }
    }

    /** {@link PriceSpec} -> price; HINT = suggested value under {@code hintLabel} on the form. */
    private BigDecimal resolve(OrderFormScreen form, String spec, String hintLabel, int occurrence) {
        PriceSpec p = PriceSpec.parse(spec);
        if (p == null) {
            throw new IllegalArgumentException("Thiếu giá cho " + hintLabel);
        }
        return switch (p.base()) {
            case FIXED -> p.resolve(null);
            case HINT -> p.resolve(form.hint(hintLabel, occurrence));
            case REF -> p.resolve(form.getCeilingRefFloor().get(1));
            case CEIL -> p.resolve(form.getCeilingRefFloor().get(0));
            case FLOOR -> p.resolve(form.getCeilingRefFloor().get(2));
        };
    }

    public record FilledForm(OrderFormScreen form, BigDecimal price) {
    }

    /**
     * TP/SL (take profit / stop loss) on a held symbol: Giao dịch &gt; Danh mục &gt; tap the holding &gt; quantity,
     * Chốt lời, Cắt lỗ &gt; Xác nhận (+ PIN/OTP). REAL ORDER. Prices are the sheet hints shifted by the test data
     * ("HINT+0.1" for take profit, "HINT-0.1" for stop loss); quantity is capped by the quantity held.
     */
    private OrderCase lastTpSlTarget;

    /** The TP/SL case on the holding actually picked in Danh mục (symbol / quantity), or null. */
    public OrderCase lastTpSlTarget() {
        return lastTpSlTarget;
    }

    @Step("Đặt lệnh TP/SL: {c}")
    public Outcome placeTpSl(OrderCase c) {
        lastTpSlTarget = null;
        if (!AppConfig.orderSubmitEnabled()) {
            throw new IllegalStateException("SAFETY GUARD: real orders are disabled (order.submit.enabled=false)");
        }
        navigator.openTrading().openOrderForm().selectSubAccount(c.subAccountName());
        PortfolioScreen portfolio = navigator.openTrading().openPortfolio();
        List<String> held = portfolio.holdings();
        if (held.isEmpty()) {
            return new Outcome(false, "Danh mục trống: không có mã đang nắm giữ để đặt TP/SL", null);
        }
        String symbol = c.symbol();
        if (!held.contains(symbol)) {
            // never place a REAL TP/SL on an arbitrary holding unless explicitly allowed
            if (!ConfigReader.getBool("order.tpsl.any.holding", false)) {
                throw new SkipException("Danh mục không có mã " + symbol + " (đang có: " + held
                        + "). Đặt order.symbol.sell=<mã đang nắm giữ> hoặc order.tpsl.any.holding=true");
            }
            symbol = held.get(0);
            LoggerFactory.getLogger(OrderFlow.class).warn("TP/SL: không có {} trong danh mục - dùng mã đầu tiên {} (order.tpsl.any.holding=true)", c.symbol(), symbol);
        }
        TpSlSheet sheet = portfolio.openTpSl(symbol);
        long qty = Math.min(c.quantity(), sheet.maxQuantity());
        BigDecimal tp = shift(TpSlSheet.hintValue(sheet.takeProfitHint()), c.price());
        BigDecimal sl = shift(TpSlSheet.hintValue(sheet.stopLossHint()), c.triggerPrice());
        AllureUtils.attachText("TP/SL", symbol + " KL " + qty + " | Chốt lời " + tp + " | Cắt lỗ " + sl);
        lastTpSlTarget = c.withTarget(symbol, qty);
        sheet.setQuantity(qty).setTakeProfit(tp).setStopLoss(sl);
        AllureUtils.attachScreenshot("TP/SL trước khi xác nhận");
        sheet.verify(qty, tp, sl); // SAFETY: never confirm values different from the test data
        sheet.confirm();
        OrderConfirmDialog confirm = new OrderConfirmDialog();
        if (confirm.isDisplayed(Duration.ofSeconds(10))) {
            confirm.enterPinAndConfirm("TP/SL " + c.id());
        }
        return readResult(tp);
    }

    /**
     * Pre-submit guard for REAL orders: the quantity on the form must equal the test data, and for limit-type orders
     * the price on the form must equal the price that was entered. Throws (no order sent) on any mismatch - e.g.
     * a keypad digit lost or doubled on a new platform.
     */
    void verifyFormBeforeSubmit(OrderCase c, FilledForm f) {
        OrderFormScreen form = f.form();
        // side: the submit button must be MUA for a buy, BÁN for a sell (one page source, no scroll if visible)
        OrderFormScreen.Side want = c.side() == OrderCase.Side.BUY ? OrderFormScreen.Side.BUY : OrderFormScreen.Side.SELL;
        OrderFormScreen.Side side = form.submitSideOnScreen().orElseGet(form::currentSide);
        block(side != want, "Chiều lệnh trên form = " + side + " khác dữ liệu " + want, c);
        // sub-account and symbol
        String sub = form.getSubAccount();
        String wantSub = Locators.nfc(c.subAccountName());
        block(!sub.startsWith(wantSub + " - "), "Tiểu khoản trên form = '" + sub + "' khác '" + wantSub + "'", c);
        String symbol = form.getSelectedStock().symbol();
        block(!c.symbol().equalsIgnoreCase(symbol), "Mã trên form = " + symbol + " khác dữ liệu " + c.symbol(), c);
        long qty = f.form().getQuantity();
        if (qty != c.quantity()) {
            AllureUtils.attachScreenshot("CHẶN GỬI LỆNH - KL sai");
            throw new IllegalStateException("SAFETY: KL trên form = " + qty + " khác dữ liệu " + c.quantity()
                    + " (" + c.id() + ") - KHÔNG gửi lệnh");
        }
        boolean limitPrice = !c.isMarketType() && !c.isConditional() && f.price() != null;
        if (limitPrice) {
            BigDecimal onForm = f.form().getPrice();
            if (onForm == null || onForm.compareTo(f.price()) != 0) {
                AllureUtils.attachScreenshot("CHẶN GỬI LỆNH - Giá sai");
                throw new IllegalStateException("SAFETY: Giá trên form = " + onForm + " khác giá đã nhập " + f.price()
                        + " (" + c.id() + ") - KHÔNG gửi lệnh");
            }
        }
        AllureUtils.attachText("Kiểm tra trước khi gửi", side + " " + symbol + " | " + sub + " | KL " + qty
                + (limitPrice ? ", giá " + f.price() : "") + " - khớp dữ liệu");
    }

    private static void block(boolean wrong, String what, OrderCase c) {
        if (wrong) {
            AllureUtils.attachScreenshot("CHẶN GỬI LỆNH - " + what);
            throw new IllegalStateException("SAFETY: " + what + " (" + c.id() + ") - KHÔNG gửi lệnh");
        }
    }

    /** "HINT+0.1" / "HINT-0.2" / "" (= hint) / "7.5" (absolute) relative to {@code hint}. */
    static BigDecimal shift(BigDecimal hint, String spec) {
        if (spec == null || spec.isBlank() || "HINT".equalsIgnoreCase(spec.trim())) {
            return hint;
        }
        Matcher m = Pattern.compile("(?i)HINT\\s*([+-])\\s*([0-9.]+)").matcher(spec.trim());
        if (m.matches()) {
            BigDecimal d = new BigDecimal(m.group(2));
            return "+".equals(m.group(1)) ? hint.add(d) : hint.subtract(d);
        }
        return new BigDecimal(spec.trim());
    }

    /** Excel steps 2-10: fill + Mua/Bán -> PIN (typed by the tester) -> Xác nhận. REAL ORDER. */
    @Step("Đặt lệnh: {c}")
    public Outcome place(OrderCase c) {
        FilledForm f = fill(c);
        Long before = null;
        try { // balance the order will consume, read on the filled form right before submitting
            before = f.form().getAvailableBalance(c.side() == OrderCase.Side.BUY ? OrderFormScreen.Side.BUY : OrderFormScreen.Side.SELL);
            AllureUtils.attachText((c.side() == OrderCase.Side.BUY ? "Sức mua" : "KL khả dụng") + " trước khi đặt", String.valueOf(before));
        } catch (AssertionError e) {
            AllureUtils.attachText("Không đọc được số dư trước khi đặt", e.getMessage());
        }
        verifyFormBeforeSubmit(c, f); // SAFETY: never send an order whose form differs from the test data
        OrderConfirmDialog confirm = f.form().submitRealOrder(c.isConditional());
        String preError = f.form().lastPreConfirmError();
        if (confirm == null) {
            // no confirmation sheet at all: the app rejected the order right away
            // e.g. a client-side validation snackbar: read it with the fast poll (+ screenshot burst)
            return preError != null ? new Outcome(false, preError, f.price(), before)
                    : readResult(f.price()).withBalance(before);
        }
        // Excel steps 9-10 even if an error popped up over the sheet: PIN -> Xác nhận -> final result decides
        try {
            confirm.enterPinAndConfirm("Đặt lệnh " + c.id());
        } catch (OrderConfirmDialog.ConfirmDisabledException disabled) {
            // the app does not allow the order (e.g. MTL outside the continuous session): a refusal, not a test error
            confirm.dismiss();
            return new Outcome(false, "Hệ thống không cho xác nhận lệnh (nút Xác nhận bị khoá)", f.price(), before);
        }
        Outcome result = readResult(f.price()).withBalance(before);
        if (preError != null && !result.success()) {
            return new Outcome(false, "Trước khi nhập PIN: " + preError + " | Sau khi xác nhận: " + result.message(),
                    f.price(), before);
        }
        return result;
    }

    /** LO / ODDLOT price: REF / CEIL / FLOOR / number, with optional +/- delta (empty = REF). */
    private BigDecimal resolvePrice(OrderFormScreen form, String spec) {
        List<BigDecimal> crf = form.getCeilingRefFloor(); // [ceiling, ref, floor]
        PriceSpec p = PriceSpec.parse(spec.isBlank() ? "REF" : spec);
        return switch (p.base()) {
            case FIXED -> p.resolve(null);
            case CEIL -> p.resolve(crf.get(0));
            case REF, HINT -> p.resolve(crf.get(1));
            case FLOOR -> p.resolve(crf.get(2));
        };
    }

    private final OrderResultReader results = new OrderResultReader();

    /** Waits for the app's result (dialog / toast) after "Xác nhận" - see {@link OrderResultReader}. */
    public Outcome readResult(BigDecimal price) {
        return results.read(price);
    }
}
