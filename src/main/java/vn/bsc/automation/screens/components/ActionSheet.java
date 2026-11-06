package vn.bsc.automation.screens.components;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

/**
 * Bottom sheet "Chọn loại chuyển tiền" / "Chọn loại giao dịch": title View + option Buttons + "Đóng" Button.
 * The area above the sheet is a "Scrim" View (tapping it closes the sheet).
 */
public class ActionSheet extends BaseScreen {

    private final By closeButton = Locators.classWithDesc("android.widget.Button", "Đóng");

    @Override
    protected By anchor() {
        return closeButton;
    }

    public boolean hasTitle(String title) {
        return isVisible(Locators.a11y(title));
    }

    public boolean hasOption(String label) {
        return isVisible(option(label));
    }

    @Step("Chọn: {label}")
    public void choose(String label) {
        tap(option(label));
    }

    @Step("Đóng bottom sheet")
    public void close() {
        tap(closeButton);
    }

    private static By option(String label) {
        return Locators.classWithDesc("android.widget.Button", label);
    }
}
