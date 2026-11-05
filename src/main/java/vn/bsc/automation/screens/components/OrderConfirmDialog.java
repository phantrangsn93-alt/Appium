package vn.bsc.automation.screens.components;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.ManualInput;
import vn.bsc.automation.core.utils.WaitUtils;

import java.time.Duration;

/**
 * Order confirmation dialog (place / modify / cancel): order summary + PIN field + "Xác nhận".
 * <p>
 * The PIN is typed by the TESTER on the phone ({@link ManualInput}); it is never stored or logged.
 * <p>
 * NOTE: locators for this dialog were derived from the Excel steps ("Nhập Pin", "Chạm vào nút Xác nhận") and
 * could not be verified on the device yet (real orders are blocked during batch). Screenshot + page source
 * are attached on every appearance so they can be finalised from the first real run.
 */
public class OrderConfirmDialog extends BaseScreen {

    private final By confirmButton = Locators.descMatchesAny("Xác nhận");
    private final By pinField = By.xpath("//android.widget.EditText");

    @Override
    protected By anchor() {
        return confirmButton;
    }

    public void attachEvidence(String label) {
        AllureUtils.attachScreenshot("Xác nhận lệnh - " + label);
        AllureUtils.attachPageSource("Xác nhận lệnh - " + label + " (page source)");
    }

    /**
     * Waits for the tester to type the PIN, then taps "Xác nhận" (Excel steps "Nhập Pin" + "Xác nhận").
     * If the tester taps "Xác nhận" themselves, that is accepted too.
     */
    public void enterPinManuallyAndConfirm(String context) {
        attachEvidence(context);
        ManualInput.waitForUser("Nhập mã PIN trên điện thoại cho: " + context + " (rồi ẩn bàn phím hoặc tự bấm Xác nhận)",
                () -> !isDisplayed(Duration.ofMillis(300)) || isPinTyped());
        if (isDisplayed(Duration.ofSeconds(1))) {
            hideKeyboard();
            tap(confirmButton);
        }
        WaitUtils.waitInvisible(confirmButton, Duration.ofSeconds(15));
    }

    private boolean isPinTyped() {
        if (findAll(pinField).isEmpty()) {
            return false; // no PIN field (e.g. PIN remembered for the session) -> tester taps Xác nhận
        }
        String t = getText(pinField);
        return t != null && !t.isBlank() && !driver().isKeyboardShown();
    }
}
