package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.utils.Locators;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.openqa.selenium.WebElement;
import vn.bsc.automation.core.driver.DeviceActions;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.ManualInput;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.screens.components.OrderConfirmDialog;

/**
 * Money / securities transaction forms opened from the menu (surveyed 06/11/2026, UAT.1-master-UAT5):
 * <pre>
 * Form (title)                 | Sub-account fields                       | Balance label               | Amount EditText (desc)
 * Chuyển tiền nội bộ           | Tiểu khoản chuyển, Tiểu khoản nhận       | Số tiền tối đa được chuyển  | "Chuyển tiền"
 * Chuyển tiền ra ngoài         | Tiểu khoản (+ Số tài khoản ngân hàng,    | Số tiền tối đa được chuyển  | "Chuyển tiền"
 *                              |   Ngân hàng, Người thụ hưởng, Tỉnh/Thành phố, Chi nhánh, Loại phí)
 * Nộp tiền vào TK phái sinh    | Tiểu khoản nhận, Tiểu khoản chuyển       | Số dư tiền                  | "Nộp tiền vào TK phái sinh"
 * Rút tiền từ TK phái sinh     | Tài khoản giao dịch, Tiểu khoản nhận     | Tiền mặt khả dụng           | "Rút tiền từ TK phái sinh"
 * Nộp tiền ký quỹ CCP          | Tiểu khoản chuyển (+ Loại phí)           | Số dư khả dụng              | "Yêu cầu nộp ký quỹ bằng tiền"
 * Rút tiền ký quỹ CCP          | Tiểu khoản nhận (+ Loại phí)             | Số dư khả dụng              | "Yêu cầu rút ký quỹ bằng tiền"
 * Phong toả tiền @BIDV         | Tiểu khoản                               | Số dư phong toả, Số dư khả dụng | (no EditText visible)
 * Giải toả tiền @BIDV          | Tiểu khoản                               | Số tiền có thể giải toả     | (no EditText visible)
 * Chuyển chứng khoán           | Tiểu khoản chuyển, Tiểu khoản nhận       | Tổng khối lượng, Khối lượng có thể chuyển (+ Mã CK)
 * </pre>
 * Every form: "Loại giao dịch"/"Loại chuyển tiền" label + value, and a "Xác nhận" button.
 * WARNING: {@link #tapConfirm()} submits a REAL money transaction (next step = OTP/PIN, not surveyed yet).
 */
public class TransactionFormScreen extends MenuTargetScreen {

    public static final By AMOUNT = Locators.editText(0);
    public static final By CONFIRM = Locators.a11y("Xác nhận");

    public TransactionFormScreen(String title) {
        super(title);
    }

    public TransactionFormScreen(SheetOption option) {
        super(option.formTitle);
    }

    @Step("Nhập số tiền: {amount}")
    public TransactionFormScreen enterAmount(String amount) {
        type(AMOUNT, amount);
        hideKeyboard();
        return this;
    }

    /**
     * Types the amount with REAL key events (Flutter fields ignore setText - the value would look filled but be
     * empty for the app) and closes the keyboard with the IME "done" action (never BACK: it would leave the form).
     */
    @Step("Nhập số tiền / khối lượng: {amount}")
    public TransactionFormScreen typeAmount(String amount) {
        WebElement field = find(AMOUNT);
        DeviceActions.tap(field);
        DeviceActions.typeKeys(field, amount);
        try {
            if (DeviceActions.isKeyboardShown()) {
                DeviceActions.keyboardDone();
            }
        } catch (Exception ignored) {
            // keyboard already closed
        }
        return this;
    }

    /**
     * Dropdown field "label\nvalue" with an empty value: open it, wait (options load slowly on production) and pick
     * the first option. Returns the chosen option label (null when the field already had a value).
     */
    @Step("Chọn lựa chọn đầu tiên cho: {label}")
    public String selectFirstOptionIfEmpty(String label) {
        By field = Locators.field(label);
        String value = getDesc(find(field));
        String current = value.contains("\n") ? value.substring(value.indexOf('\n') + 1).trim() : "";
        if (!current.isEmpty()) {
            return null;
        }
        tap(field);
        By close = Locators.classWithDesc("android.widget.Button", "Đóng");
        List<UiNode> options = new ArrayList<>();
        WaitUtils.condition(() -> {
            options.clear();
            if (!isVisible(close, Duration.ofMillis(300))) {
                return false;
            }
            UiNode.snapshot(driver().getPageSource()).stream()
                    .filter(n -> n.clickable() && n.desc().contains("\n") && !n.className().endsWith("Button"))
                    .forEach(options::add);
            return !options.isEmpty();
        }, Duration.ofSeconds(15));
        if (options.isEmpty()) {
            AllureUtils.attachScreenshot("Không có lựa chọn cho " + label);
            throw new AssertionError("Không có lựa chọn nào cho \"" + label + "\" sau 15 giây");
        }
        UiNode first = options.stream()
                .min(Comparator.comparingInt(UiNode::y)).get();
        GestureUtils.tap(first);
        WaitUtils.waitInvisible(close, Duration.ofSeconds(5));
        log.info("{}: đã chọn lựa chọn đầu tiên", label); // option holds an account number: not logged
        return first.desc();
    }

    public String amountText() {
        return getText(AMOUNT);
    }

    /**
     * REAL transaction: form "Xác nhận" -> summary sheet "Xác nhận thông tin" -> its "Xác nhận"
     * (+ PIN when the app asks for it; "Đã xác thực" = PIN remembered for the session).
     */
    @Step("Xác nhận giao dịch")
    public void submit() {
        scrollTo(CONFIRM);
        tap(CONFIRM);
        By summary = Locators.descStartsWith("Xác nhận thông tin");
        if (isVisible(summary, Duration.ofSeconds(8))) {
            AllureUtils.attachScreenshot("Xác nhận thông tin - " + title());
            // Production: the summary sheet itself asks for an OTP ("Lấy OTP" + input). The tester requests and types
            // it and taps "Xác nhận"; the test only waits for the sheet to close.
            By otp = Locators.descMatchesAny("Lấy OTP", "Lấy mã OTP");
            if (isVisible(otp, Duration.ofSeconds(2))
                    || isVisible(Locators.editText(0), Duration.ofSeconds(1))) {
                ManualInput.waitForUser("Giao dịch " + title()
                                + ": bấm Lấy OTP, nhập OTP rồi bấm Xác nhận trên điện thoại",
                        () -> !isVisible(summary, Duration.ofMillis(300)));
                return;
            }
            List<UiNode> nodes =
                    UiNode.snapshot(driver().getPageSource());
            UiNode btn = nodes.stream()
                    .filter(n -> "Xác nhận".equals(n.desc()) && n.clickable())
                    .max(Comparator.comparingInt(UiNode::y))
                    .orElseThrow(() -> new AssertionError("Nút Xác nhận trên màn Xác nhận thông tin không thấy"));
            GestureUtils.tap(btn);
            // PIN step (only when the app asks for it)
            OrderConfirmDialog pin = new OrderConfirmDialog();
            if (isVisible(Locators.passwordField(), Duration.ofSeconds(2))
                    || isVisible(Locators.descMatchesAny("Lấy OTP", "Lấy mã OTP"), Duration.ofSeconds(1))) {
                pin.enterPinAndConfirm("Giao dịch " + title()); // PIN/OTP typed by the tester when not in .env
            }
        }
    }

    /** Submits the transaction (REAL on the connected environment). */
    @Step("Bấm Xác nhận")
    public void tapConfirm() {
        tap(CONFIRM);
    }

    /** "Số tiền tối đa được chuyển" of the sending (sub-)account; null when the form does not show it. */
    public Long maxTransferable() {
        try {
            String v = valueBelow("Số tiền tối đa được chuyển").replaceAll("[^0-9]", "");
            return v.isEmpty() ? null : Long.parseLong(v);
        } catch (AssertionError e) {
            return null;
        }
    }

    /** Value of the first of these "label\nvalue" fields present on the form (null when none). */
    public String firstFieldValue(String... labels) {
        for (String l : labels) {
            if (isVisible(Locators.field(l), Duration.ofSeconds(1))) {
                return fieldValue(l);
            }
        }
        return null;
    }
}
