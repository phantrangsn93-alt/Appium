package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.utils.Locators;

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
        org.openqa.selenium.WebElement field = find(AMOUNT);
        vn.bsc.automation.core.driver.DeviceActions.tap(field);
        vn.bsc.automation.core.driver.DeviceActions.typeKeys(field, amount);
        try {
            if (vn.bsc.automation.core.driver.DeviceActions.isKeyboardShown()) {
                vn.bsc.automation.core.driver.DeviceActions.keyboardDone();
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
        java.util.List<vn.bsc.automation.core.utils.UiNode> options = new java.util.ArrayList<>();
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> {
            options.clear();
            if (!isVisible(close, java.time.Duration.ofMillis(300))) {
                return false;
            }
            vn.bsc.automation.core.utils.UiNode.snapshot(driver().getPageSource()).stream()
                    .filter(n -> n.clickable() && n.desc().contains("\n") && !n.className().endsWith("Button"))
                    .forEach(options::add);
            return !options.isEmpty();
        }, java.time.Duration.ofSeconds(15));
        if (options.isEmpty()) {
            vn.bsc.automation.core.utils.AllureUtils.attachScreenshot("Không có lựa chọn cho " + label);
            throw new AssertionError("Không có lựa chọn nào cho \"" + label + "\" sau 15 giây");
        }
        vn.bsc.automation.core.utils.UiNode first = options.stream()
                .min(java.util.Comparator.comparingInt(vn.bsc.automation.core.utils.UiNode::y)).get();
        vn.bsc.automation.core.utils.GestureUtils.tap(first.x() + first.width() / 2, first.y() + first.height() / 2);
        vn.bsc.automation.core.utils.WaitUtils.waitInvisible(close, java.time.Duration.ofSeconds(5));
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
        if (isVisible(summary, java.time.Duration.ofSeconds(8))) {
            vn.bsc.automation.core.utils.AllureUtils.attachScreenshot("Xác nhận thông tin - " + title());
            // Production: the summary sheet itself asks for an OTP ("Lấy OTP" + input). The tester requests and types
            // it and taps "Xác nhận"; the test only waits for the sheet to close.
            By otp = Locators.descMatchesAny("Lấy OTP", "Lấy mã OTP");
            if (isVisible(otp, java.time.Duration.ofSeconds(2))
                    || isVisible(vn.bsc.automation.core.utils.Locators.editText(0), java.time.Duration.ofSeconds(1))) {
                vn.bsc.automation.core.utils.ManualInput.waitForUser("Giao dịch " + title()
                                + ": bấm Lấy OTP, nhập OTP rồi bấm Xác nhận trên điện thoại",
                        () -> !isVisible(summary, java.time.Duration.ofMillis(300)));
                return;
            }
            java.util.List<vn.bsc.automation.core.utils.UiNode> nodes =
                    vn.bsc.automation.core.utils.UiNode.snapshot(driver().getPageSource());
            vn.bsc.automation.core.utils.UiNode btn = nodes.stream()
                    .filter(n -> "Xác nhận".equals(n.desc()) && n.clickable())
                    .max(java.util.Comparator.comparingInt(vn.bsc.automation.core.utils.UiNode::y))
                    .orElseThrow(() -> new AssertionError("Nút Xác nhận trên màn Xác nhận thông tin không thấy"));
            vn.bsc.automation.core.utils.GestureUtils.tap(btn.x() + btn.width() / 2, btn.y() + btn.height() / 2);
            // PIN step (only when the app asks for it)
            vn.bsc.automation.screens.components.OrderConfirmDialog pin = new vn.bsc.automation.screens.components.OrderConfirmDialog();
            if (isVisible(vn.bsc.automation.core.utils.Locators.passwordField(), java.time.Duration.ofSeconds(2))
                    || isVisible(Locators.descMatchesAny("Lấy OTP", "Lấy mã OTP"), java.time.Duration.ofSeconds(1))) {
                pin.enterPinAndConfirm("Giao dịch " + title()); // PIN/OTP typed by the tester when not in .env
            }
        }
    }

    /** Submits the transaction (REAL on the connected environment). */
    @Step("Bấm Xác nhận")
    public void tapConfirm() {
        tap(CONFIRM);
    }
}
