package vn.bsc.automation.flows;

import io.qameta.allure.Step;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.OrderCase;
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
        form.selectSubAccount(c.subAccount());
        if (c.side() == OrderCase.Side.BUY) {
            form.selectBuy();
        } else {
            form.selectSell();
        }
        form.selectSymbol(c.symbol());
        BigDecimal price = null;
        if (c.isMarketType()) {
            form.selectMarketPriceType(c.orderType());
        } else {
            form.selectOrderType(OrderFormScreen.OrderType.LIMIT);
            price = resolvePrice(form, c.price());
            form.enterPrice(price.toPlainString());
        }
        form.enterQuantity(c.quantity());
        AllureUtils.attachScreenshot("Form " + c.id());
        return new FilledForm(form, price);
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
        confirm.enterPinManuallyAndConfirm("Đặt lệnh " + c.id());
        return readResult(f.price());
    }

    /** REF / CEIL / FLOOR / number (x1000). */
    private BigDecimal resolvePrice(OrderFormScreen form, String spec) {
        List<BigDecimal> crf = form.getCeilingRefFloor(); // [ceiling, ref, floor]
        return switch (spec) {
            case "", "REF" -> crf.get(1);
            case "CEIL" -> crf.get(0);
            case "FLOOR" -> crf.get(2);
            default -> new BigDecimal(spec);
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
