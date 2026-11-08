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

    private static final Pattern SUCCESS = Pattern.compile(ConfigReader.get("order.success.pattern", "(?i)(?s).*thành công.*"));
    private static final Pattern SUCCESS_BUY = Pattern.compile(vn.bsc.automation.core.utils.Locators.nfc(
            ConfigReader.get("order.success.buy.pattern", "(?is).*mua.*thành công.*|.*thành công.*mua.*")));
    private static final Pattern SUCCESS_SELL = Pattern.compile(vn.bsc.automation.core.utils.Locators.nfc(
            ConfigReader.get("order.success.sell.pattern", "(?is).*bán.*thành công.*|.*thành công.*bán.*")));

    private static final Pattern SYSTEM_ERROR = Pattern.compile(
            ConfigReader.get("order.systemError.pattern", "(?is).*(E_SYSTEM_ERROR|Lỗi hệ thống).*"));

    /**
     * System / technical errors (e.g. E_SYSTEM_ERROR) are ALWAYS a test failure - they are never accepted as
     * the expected "rejected outside the session" business response.
     */
    public static boolean isSystemError(String msg) {
        return msg != null && SYSTEM_ERROR.matcher(msg).matches();
    }

    /** "Mua ... thành công" for a buy order / "Bán ... thành công" for a sell order. */
    public static boolean isSideSuccessMessage(OrderCase.Side side, String msg) {
        String m = msg == null ? "" : vn.bsc.automation.core.utils.Locators.nfc(msg);
        return (side == OrderCase.Side.BUY ? SUCCESS_BUY : SUCCESS_SELL).matcher(m).matches();
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
        OrderConfirmDialog confirm = f.form().submitRealOrder();
        String preError = f.form().lastPreConfirmError();
        if (confirm == null) {
            // no confirmation sheet at all: the app rejected the order right away
            // e.g. a client-side validation snackbar: read it with the fast poll (+ screenshot burst)
            return preError != null ? new Outcome(false, preError, f.price(), before)
                    : readResult(f.price()).withBalance(before);
        }
        // Excel steps 9-10 even if an error popped up over the sheet: PIN -> Xác nhận -> final result decides
        confirm.enterPinAndConfirm("Đặt lệnh " + c.id());
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

    /** Waits for the app's result dialog after "Xác nhận". */
    public Outcome readResult(BigDecimal price) {
        // The result can be a modal dialog OR a short-lived toast / snackbar (gone after ~2 s):
        // poll fast (one page-source per round) and keep a burst of screenshots of the first seconds as evidence.
        AlertDialog dialog = new AlertDialog();
        var d = vn.bsc.automation.core.driver.DriverManager.getDriver();
        long end = System.currentTimeMillis() + 30_000, burstEnd = System.currentTimeMillis() + 3_000;
        int shots = 0;
        while (System.currentTimeMillis() < end) {
            String src;
            try {
                src = d.getPageSource();
            } catch (Exception e) {
                continue;
            }
            var nodes = vn.bsc.automation.core.utils.UiNode.snapshot(src);
            boolean dialogShown = nodes.stream().anyMatch(n -> n.className().endsWith("Button")
                    && java.util.List.of(AlertDialog.BTN_CLOSE, AlertDialog.BTN_YES, AlertDialog.BTN_NO, "Bỏ qua").contains(n.desc()));
            if (dialogShown) {
                return closeResult(price);
            }
            String t = nodes.stream().map(n -> !n.desc().isBlank() ? n.desc() : n.text())
                    .filter(x -> x != null && x.length() < 200 && RESULT_TEXT.matcher(x).matches()).findFirst().orElse(null);
            if (t == null) {
                t = toastText();
            }
            if (t != null) {
                AllureUtils.attachScreenshot("Thông báo nhanh: " + t);
                AllureUtils.attachText("Thông báo của hệ thống (toast/snackbar)", t);
                return new Outcome(SUCCESS.matcher(t).matches(), t, price);
            }
            if (System.currentTimeMillis() < burstEnd && shots < 5) {
                AllureUtils.attachScreenshot("Sau khi Xác nhận +" + (++shots));
            }
        }
        AllureUtils.attachScreenshot("Không thấy thông báo kết quả");
        return new Outcome(false, "", price);
    }

    private String toastText() {
        try {
            var toasts = vn.bsc.automation.core.driver.DriverManager.getDriver()
                    .findElements(vn.bsc.automation.core.utils.Locators.xpath("//android.widget.Toast"));
            if (!toasts.isEmpty() && toasts.get(0).getText() != null && !toasts.get(0).getText().isBlank()) {
                return vn.bsc.automation.core.utils.Locators.nfc(toasts.get(0).getText());
            }
        } catch (Exception ignored) {
            // no toast
        }
        return null;
    }

    private static final Pattern RESULT_TEXT = Pattern.compile("(?is).*(thành công|thất bại|lỗi|không hợp lệ|E_[A-Z_]+).*");


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
