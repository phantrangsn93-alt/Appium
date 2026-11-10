package vn.bsc.automation.screens.components;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.ManualInput;
import vn.bsc.automation.core.utils.WaitUtils;

import java.time.Duration;

/**
 * Order confirmation dialog (place / modify / cancel): order summary + PIN field + "Xác nhận".
 * <p>
 * The PIN comes from BSC_ORDER_PIN in .env (git-ignored) or, if unset, is typed by the TESTER on the phone
 * ({@link ManualInput}). It is never logged.
 * <p>
 * NOTE: locators for this dialog were derived from the Excel steps ("Nhập Pin", "Chạm vào nút Xác nhận") and
 * could not be verified on the device yet (real orders are blocked during batch). Screenshot + page source
 * are attached on every appearance so they can be finalised from the first real run.
 */
public class OrderConfirmDialog extends BaseScreen {

    /**
     * Sheet title "Xác nhận đặt lệnh" / "Huỷ lệnh\nĐã xác thực" / "Sửa lệnh...". Used as the anchor: the
     * "Xác nhận" BUTTON is not in the accessibility tree while it is disabled (before the PIN is typed).
     */
    private final By title = Locators.descRegex("(?:" + Locators.anyForm("Xác nhận ") + "|" + Locators.anyForm("Huỷ lệnh")
            + "|" + Locators.anyForm("Hủy lệnh") + "|" + Locators.anyForm("Sửa lệnh") + ").*");
    private final By confirmButton = Locators.a11y("Xác nhận");
    /** "Nhập mật khẩu đặt lệnh" (PIN method) - a masked EditText. */
    private final By pinField = vn.bsc.automation.core.utils.Locators.pinField();

    @Override
    protected By anchor() {
        return title;
    }

    public void attachEvidence(String label) {
        AllureUtils.attachScreenshot("Xác nhận lệnh - " + label);
        AllureUtils.attachPageSource("Xác nhận lệnh - " + label + " (page source)");
    }

    /**
     * Excel steps "Nhập Pin" + "Xác nhận". With BSC_ORDER_PIN set in .env the PIN is typed automatically;
     * otherwise waits for the tester to type it on the phone. If the sheet has no PIN field (PIN remembered
     * for the session) only "Xác nhận" is tapped.
     */
    public void enterPinAndConfirm(String context) {
        attachEvidence(context);
        String pin = AppConfig.orderPin();
        if (pin != null) {
            if (isVisible(pinField, Duration.ofSeconds(3))) {
                log.info("Nhập PIN tự động (từ .env) cho: {}", context); // never log the PIN itself
                typePin(pin, context);
                closeKeyboardWithoutBack();
            } else {
                log.warn("Không thấy ô nhập PIN trên màn xác nhận ({})", context);
            }
        } else if (!isVisible(pinField, Duration.ofSeconds(2))) {
            log.info("Không có ô PIN/OTP (Đã xác thực) - bấm Xác nhận luôn: {}", context);
        } else {
            ManualInput.waitForUser("Nhập mã PIN trên điện thoại cho: " + context + " (rồi ẩn bàn phím hoặc tự bấm Xác nhận)",
                    () -> isPinTyped() || sheetReallyClosed());
            closeKeyboardWithoutBack();
        }
        tapConfirmWhenEnabled(context);
        WaitUtils.waitInvisible(title, Duration.ofSeconds(15));
    }

    /**
     * Flutter password fields ignore UiAutomator2's setText (the field looks filled but the app reads it as
     * empty: "Chưa nhập mã xác thực"). Focus the field and type real key events, then verify the number of
     * "•" equals the PIN length; fall back to sendKeys once.
     */
    private void typePin(String pin, String context) {
        org.openqa.selenium.WebElement field = find(pinField);
        vn.bsc.automation.core.driver.DeviceActions.tap(field);
        vn.bsc.automation.core.driver.DeviceActions.typeKeys(field, pin);
        if (!vn.bsc.automation.core.utils.WaitUtils.condition(() -> pinLength() == pin.length(), Duration.ofSeconds(3))) {
            log.warn("PIN not registered by key events ({} chars) - retrying with sendKeys", pinLength());
            field = find(pinField);
            vn.bsc.automation.core.driver.DeviceActions.tap(field);
            vn.bsc.automation.core.driver.DeviceActions.clear(field);
            field.sendKeys(pin);
        }
        if (pinLength() != pin.length()) {
            AllureUtils.attachScreenshot("PIN không nhập được - " + context);
            throw new AssertionError("Không nhập được PIN vào ô mật khẩu đặt lệnh (" + context + ")");
        }
    }

    private int pinLength() {
        try {
            String t = getText(pinField);
            return t == null ? 0 : t.length();
        } catch (Exception | AssertionError e) {
            return -1;
        }
    }

    /**
     * NEVER driver.hideKeyboard() here: on Android it sends BACK, which dismisses the bottom sheet and the
     * order is silently not sent. Use the keyboard's own "done" action, then tap the sheet title if needed.
     */
    private void closeKeyboardWithoutBack() {
        try {
            if (vn.bsc.automation.core.driver.DeviceActions.isKeyboardShown()) {
                vn.bsc.automation.core.driver.DeviceActions.keyboardDone();
            }
            if (vn.bsc.automation.core.driver.DeviceActions.isKeyboardShown()) {
                tapCenter(title);
            }
        } catch (Exception e) {
            log.info("Keyboard close: {}", e.getMessage());
        }
    }

    /** The button appears in the tree once enabled; it sits at the bottom of the sheet (may need a small scroll). */
    private void tapConfirmWhenEnabled(String context) {
        if (!isDisplayed(Duration.ofSeconds(1))) {
            return; // tester already confirmed, or the sheet closed
        }
        for (int i = 0; i < 3; i++) {
            if (isVisible(confirmButton, Duration.ofSeconds(i == 0 ? 3 : 1))) {
                tap(confirmButton);
                return;
            }
            vn.bsc.automation.core.utils.GestureUtils.scrollBy(0.15);
        }
        AllureUtils.attachScreenshot("Nút Xác nhận không bấm được - " + context);
        throw new ConfirmDisabledException("Nút 'Xác nhận' không khả dụng sau khi nhập PIN (" + context + ")");
    }

    /**
     * The sheet is gone for good: not displayed, no dialog on top of it (e.g. "Đã gửi OTP" after "Lấy OTP" hides the
     * sheet's semantics), and still gone 1.5 s later.
     */
    private boolean sheetReallyClosed() {
        AlertDialog overlay = new AlertDialog();
        if (isDisplayed(Duration.ofMillis(300)) || overlay.isShown(Duration.ofMillis(300))) {
            return false;
        }
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> false, Duration.ofMillis(1500));
        return !isDisplayed(Duration.ofMillis(300)) && !overlay.isShown(Duration.ofMillis(300));
    }

    /** The app keeps "Xác nhận" disabled: it refuses the order (e.g. MTL outside the continuous session). */
    public static class ConfirmDisabledException extends AssertionError {
        public ConfirmDisabledException(String message) {
            super(message);
        }
    }

    /** Closes the confirmation sheet without confirming (chevron at its top-right, else Back). */
    public void dismiss() {
        if (isDisplayed(Duration.ofMillis(500))) {
            pressBack();
        }
    }

    private boolean isPinTyped() {
        if (findAll(pinField).isEmpty()) {
            return false; // no PIN field (e.g. PIN remembered for the session) -> tester taps Xác nhận
        }
        String t = getText(pinField);
        return t != null && !t.isBlank() && !vn.bsc.automation.core.driver.DeviceActions.isKeyboardShown();
    }
}
