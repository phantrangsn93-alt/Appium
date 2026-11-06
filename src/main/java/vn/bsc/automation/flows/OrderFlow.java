package vn.bsc.automation.flows;

import io.qameta.allure.Step;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.data.PriceSpec;
import vn.bsc.automation.screens.OrderFormScreen;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.OrderConfirmDialog;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/** Business flow: fill the order form from an {@link OrderCase} row and submit it (real order). */
public class OrderFlow {

    /** Result of a submit: whether the app reported success, and its message. */
    public record Outcome(boolean success, String message, BigDecimal price) {
    }

    private static final Pattern SUCCESS = Pattern.compile(ConfigReader.get("order.success.pattern", "(?i)(?s).*thành công.*"));

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

    /** Excel steps 2-10: fill + Mua/Bán -> PIN (typed by the tester) -> Xác nhận. REAL ORDER. */
    @Step("Đặt lệnh: {c}")
    public Outcome place(OrderCase c) {
        FilledForm f = fill(c);
        OrderConfirmDialog confirm = f.form().submitRealOrder();
        if (confirm == null) {
            return closeResult(f.price()); // rejected immediately, before PIN
        }
        confirm.enterPinAndConfirm("Đặt lệnh " + c.id());
        return readResult(f.price());
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

    /** Waits for the app's result dialog after "Xác nhận". */
    public Outcome readResult(BigDecimal price) {
        AlertDialog dialog = new AlertDialog();
        if (!dialog.isShown(Duration.ofSeconds(30))) {
            AllureUtils.attachScreenshot("Không thấy thông báo kết quả");
            return new Outcome(false, "", price);
        }
        return closeResult(price);
    }

    private Outcome closeResult(BigDecimal price) {
        AlertDialog dialog = new AlertDialog();
        WaitUtils.condition(() -> dialog.isShown(Duration.ofMillis(300)), Duration.ofSeconds(10));
        String msg = dialog.getMessage();
        AllureUtils.attachScreenshot("Kết quả: " + msg);
        AllureUtils.attachText("Thông báo của hệ thống", msg);
        for (String b : new String[]{AlertDialog.BTN_CLOSE, "OK", "Bỏ qua"}) {
            if (dialog.hasButton(b)) {
                dialog.tapButton(b);
                break;
            }
        }
        return new Outcome(SUCCESS.matcher(msg).matches(), msg, price);
    }
}
