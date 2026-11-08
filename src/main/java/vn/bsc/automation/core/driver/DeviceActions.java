package vn.bsc.automation.core.driver;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.HasOnScreenKeyboard;
import io.appium.java_client.HidesKeyboard;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.appmanagement.ApplicationState;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.config.Platform;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Device-level actions whose implementation differs between Android and iOS.
 * Screens call these instead of platform-specific {@code mobile:} commands.
 */
public final class DeviceActions {

    /** Labels of a Flutter AppBar back button / iOS keyboard "done" key (localised). */
    private static final String[] BACK_LABELS = {"Back", "Quay lại", "Trở lại", "Trở về", "Menu"}; // "Menu": title button closing the drawer
    /**
     * iOS keys used to close the keyboard: "done" keys, and the search key of search fields (it only runs the
     * search). Never "Tiếp"/"Next"/"Return"/"Go": Flutter maps them to the field's submit action (on the BSC login
     * form "Tiếp" in the password field submits the login).
     */
    private static final String[] KEYBOARD_DONE = {"Done", "Xong", "Hoàn tất", "Tìm", "Search"}; // search key: harmless

    private DeviceActions() {
    }

    private static AppiumDriver driver() {
        return DriverManager.getDriver();
    }

    public static boolean isAppInForeground() {
        AppiumDriver d = driver();
        if (d instanceof AndroidDriver a) {
            return AppConfig.appId().equals(a.getCurrentPackage());
        }
        return ((InteractsWithApps) d).queryAppState(AppConfig.appId()) == ApplicationState.RUNNING_IN_FOREGROUND;
    }

    public static void activateApp() {
        ((InteractsWithApps) driver()).activateApp(AppConfig.appId());
    }

    public static boolean isKeyboardShown() {
        try {
            return ((HasOnScreenKeyboard) driver()).isKeyboardShown();
        } catch (Exception e) {
            return false;
        }
    }

    public static void hideKeyboard() {
        if (!isKeyboardShown()) {
            return;
        }
        if (Platform.ios()) {
            // only a real "done" key; driver.hideKeyboard() on iOS may press return (= submit) - leave it open instead
            tapKeyboardKey();
            return;
        }
        try {
            ((HidesKeyboard) driver()).hideKeyboard();
        } catch (Exception ignored) {
            // keyboard may already be hidden
        }
    }

    /**
     * Closes the soft keyboard WITHOUT a Back press (Back would also dismiss the bottom sheet underneath).
     * Android: IME action "done"; iOS: taps the keyboard's done/return key.
     */
    public static void keyboardDone() {
        if (Platform.android()) {
            driver().executeScript("mobile: performEditorAction", Map.of("action", "done"));
            return;
        }
        if (isKeyboardShown()) {
            tapKeyboardKey(); // no "done" key (numeric pad): keyboard stays, the sheet is still usable above it
        }
    }

    private static boolean tapKeyboardKey() {
        return tapKeyboardKey(KEYBOARD_DONE);
    }

    /** iOS: taps the first keyboard key/button with one of these labels (e.g. "Tiếp" to move to the next field). */
    public static boolean tapKeyboardKey(String... labels) {
        for (String label : labels) {
            List<WebElement> keys = driver().findElements(keyboardButton(label));
            if (!keys.isEmpty()) {
                GestureUtils.tapCenter(keys.get(0));
                return true;
            }
        }
        return false;
    }

    private static By keyboardButton(String label) {
        return io.appium.java_client.AppiumBy.iOSNsPredicateString(
                "type == \"XCUIElementTypeButton\" AND (name == \"" + label + "\" OR label == \"" + label + "\")");
    }

    /**
     * Types into the focused Flutter text field. Android: real key events (UiAutomator2 setText is ignored by
     * Flutter password fields); iOS: XCUITest typeText through the keyboard (W3C key actions are not supported).
     *
     * @param field focused field (used on iOS; may be null on Android)
     */
    public static void typeKeys(WebElement field, String text) {
        if (Platform.android() || field == null) {
            new Actions(driver()).sendKeys(text).perform();
        } else {
            field.sendKeys(text);
        }
    }

    /**
     * System Back. iOS has none: tap a labelled back button when present, else the position of the app's AppBar
     * "<" (unlabelled), else swipe from the left edge. Only call it on a child screen / sheet (on a main screen the
     * top-left area is the account header).
     */
    public static void back() {
        if (Platform.android()) {
            driver().navigate().back();
            return;
        }
        List<WebElement> back = driver().findElements(Locators.classWithDescAny("android.widget.Button", BACK_LABELS));
        if (!back.isEmpty()) {
            back.get(0).click();
            return;
        }
        // The app's AppBar "<" (top-left) has no accessibility node on iOS: tap its position; if the screen did not
        // change (no AppBar, e.g. a bottom sheet keeps the same page source), use the edge-swipe back gesture.
        Dimension s = driver().manage().window().getSize();
        String before = driver().getPageSource();
        GestureUtils.tap((int) (s.width * 0.055), (int) (s.height * 0.103));
        boolean changed = vn.bsc.automation.core.utils.WaitUtils.condition(
                () -> !driver().getPageSource().equals(before), Duration.ofSeconds(2));
        if (!changed) {
            GestureUtils.swipe(new Point(2, s.height / 2), new Point((int) (s.width * 0.7), s.height / 2),
                    Duration.ofMillis(300));
        }
    }

    /**
     * Taps an element. iOS: a coordinate tap on its centre - XCUITest's element tap is ignored for widgets that
     * report hittable/visible=false (this app keeps a full-screen ad container on top).
     */
    public static void tap(WebElement el) {
        if (Platform.ios()) {
            GestureUtils.tapCenter(el);
        } else {
            el.click();
        }
    }

    /**
     * Empties a text field. iOS: XCUITest clear() does not reach Flutter fields - focus the field and send one
     * delete key per character (the value of an obscured field is one "•" per character).
     */
    public static void clear(WebElement el) {
        if (Platform.android()) {
            el.clear();
            return;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            String v = el.getAttribute("value");
            String label = el.getAttribute("label");
            // empty field: no value, or the value is just the hint shown as label
            if (v == null || v.isEmpty() || v.equals(label)) {
                return;
            }
            el.sendKeys("\b".repeat(v.length() + 2));
        }
    }
}
