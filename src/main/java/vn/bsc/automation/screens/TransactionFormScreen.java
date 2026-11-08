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
            java.util.List<vn.bsc.automation.core.utils.UiNode> nodes =
                    vn.bsc.automation.core.utils.UiNode.snapshot(driver().getPageSource());
            vn.bsc.automation.core.utils.UiNode btn = nodes.stream()
                    .filter(n -> "Xác nhận".equals(n.desc()) && n.clickable())
                    .max(java.util.Comparator.comparingInt(vn.bsc.automation.core.utils.UiNode::y))
                    .orElseThrow(() -> new AssertionError("Nút Xác nhận trên màn Xác nhận thông tin không thấy"));
            vn.bsc.automation.core.utils.GestureUtils.tap(btn.x() + btn.width() / 2, btn.y() + btn.height() / 2);
            // PIN step (only when the app asks for it)
            vn.bsc.automation.screens.components.OrderConfirmDialog pin = new vn.bsc.automation.screens.components.OrderConfirmDialog();
            if (isVisible(vn.bsc.automation.core.utils.Locators.passwordField(), java.time.Duration.ofSeconds(2))) {
                pin.enterPinAndConfirm("Giao dịch " + title());
            }
        }
    }

    /** Submits the transaction (REAL on the connected environment). */
    @Step("Bấm Xác nhận")
    public void tapConfirm() {
        tap(CONFIRM);
    }
}
