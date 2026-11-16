package vn.bsc.automation.flows;

import org.openqa.selenium.NoSuchSessionException;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.core.driver.DriverManager;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.screens.components.AlertDialog;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and classifies the app's answer after "Xác nhận" (place / modify / cancel): a modal dialog or a short-lived
 * toast / snackbar. "... thành công" is a success unless it is a failure wording ("Huỷ lệnh không thành công").
 */
public class OrderResultReader {

    private static final Pattern SUCCESS = Pattern.compile(ConfigReader.get("order.success.pattern", "(?i)(?s).*thành công.*"));
    private static final Pattern SUCCESS_BUY = Pattern.compile(Locators.nfc(
            ConfigReader.get("order.success.buy.pattern", "(?is).*mua.*thành công.*|.*thành công.*mua.*")));
    private static final Pattern SUCCESS_SELL = Pattern.compile(Locators.nfc(
            ConfigReader.get("order.success.sell.pattern", "(?is).*bán.*thành công.*|.*thành công.*bán.*")));

    /** Failure wording that also contains "thành công" ("Huỷ lệnh không thành công") or reports an error. */
    private static final Pattern FAILURE = Pattern.compile(ConfigReader.get("order.failure.pattern",
            "(?is).*(không thành công|chưa thành công|thất bại|lỗi|không hợp lệ|E_[A-Z_]+).*"));

    /** "... thành công" and NOT a failure message ("Huỷ lệnh không thành công" is a failure). */
    public static boolean isSuccessMessage(String msg) {
        String m = msg == null ? "" : Locators.nfc(msg);
        return SUCCESS.matcher(m).matches() && !FAILURE.matcher(m).matches();
    }

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
        String m = msg == null ? "" : Locators.nfc(msg);
        if (FAILURE.matcher(m).matches()) {
            return false;
        }
        if (m.matches("(?is).*TP/SL thành công.*")) {
            return side == OrderCase.Side.SELL; // "Đặt lệnh TP/SL thành công" (TP/SL = sell of a holding)
        }
        return (side == OrderCase.Side.BUY ? SUCCESS_BUY : SUCCESS_SELL).matcher(m).matches();
    }


    /** Waits for the app's result dialog / toast after "Xác nhận". */
    public OrderFlow.Outcome read(BigDecimal price) {
        // The result can be a modal dialog OR a short-lived toast / snackbar (gone after ~2 s):
        // poll fast (one page-source per round) and keep a burst of screenshots of the first seconds as evidence.
        var d = DriverManager.getDriver();
        long end = System.currentTimeMillis() + 30_000, burstEnd = System.currentTimeMillis() + 3_000;
        int shots = 0;
        while (System.currentTimeMillis() < end) {
            String src;
            try {
                src = d.getPageSource();
            } catch (NoSuchSessionException e) {
                throw e; // device / session lost: never report it as "no result"
            } catch (Exception e) {
                WaitUtils.pause(Duration.ofMillis(300)); // screen in transition: retry shortly
                continue;
            }
            var nodes = UiNode.snapshot(src);
            boolean dialogShown = nodes.stream().anyMatch(n -> n.className().endsWith("Button")
                    && List.of(AlertDialog.BTN_CLOSE, AlertDialog.BTN_YES, AlertDialog.BTN_NO, "Bỏ qua").contains(n.desc()));
            if (dialogShown) {
                return closeResult(price);
            }
            String t = nodes.stream().map(UiNode::label)
                    .filter(x -> x != null && x.length() < 200 && RESULT_TEXT.matcher(x).matches()).findFirst().orElse(null);
            if (t == null) {
                t = toastText();
            }
            if (t != null) {
                AllureUtils.attachScreenshot("Thông báo nhanh: " + t);
                AllureUtils.attachText("Thông báo của hệ thống (toast/snackbar)", t);
                return new OrderFlow.Outcome(isSuccessMessage(t), t, price);
            }
            if (System.currentTimeMillis() < burstEnd && shots < 5) {
                AllureUtils.attachScreenshot("Sau khi Xác nhận +" + (++shots));
            }
        }
        AllureUtils.attachScreenshot("Không thấy thông báo kết quả");
        return new OrderFlow.Outcome(false, "", price);
    }

    private String toastText() {
        try {
            var toasts = DriverManager.getDriver()
                    .findElements(Locators.xpath("//android.widget.Toast"));
            if (!toasts.isEmpty() && toasts.get(0).getText() != null && !toasts.get(0).getText().isBlank()) {
                return Locators.nfc(toasts.get(0).getText());
            }
        } catch (Exception ignored) {
            // no toast
        }
        return null;
    }

    private static final Pattern RESULT_TEXT = Pattern.compile("(?is).*(thành công|thất bại|lỗi|không hợp lệ|E_[A-Z_]+).*");


    private OrderFlow.Outcome closeResult(BigDecimal price) {
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
        return new OrderFlow.Outcome(isSuccessMessage(msg), msg, price);
    }

    /** Price in a success message ("... giá 7,460.00 thành công!" / "giá 6.98"); null when absent. */
    public static BigDecimal priceFromMessage(String message) {
        Matcher m = Pattern.compile("giá\\s+([0-9][0-9.,]*)").matcher(message == null ? "" : message);
        if (!m.find()) {
            return null;
        }
        String t = m.group(1).replaceAll("[.,]$", "");
        // "7,460.00" (VND, comma = thousands) or "6.98" (x1000)
        return new BigDecimal(t.replace(",", ""));
    }
}
