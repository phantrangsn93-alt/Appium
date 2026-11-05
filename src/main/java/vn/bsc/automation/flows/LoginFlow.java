package vn.bsc.automation.flows;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.driver.DriverManager;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.ManualInput;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.screens.LoginScreen;
import vn.bsc.automation.screens.MarketScreen;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.AppHeader;
import vn.bsc.automation.screens.components.BottomNavBar;

import java.time.Duration;

/**
 * Business flows around authentication. Keeps tests independent of the app's current state.
 * <p>
 * App states:
 * <ul>
 *   <li>LOGGED_IN  - bottom nav + header "&lt;Name&gt; - &lt;custody code&gt;"</li>
 *   <li>GUEST      - bottom nav + header "BIDV Securities" (e.g. after the server session expired);
 *                    trading features show "Quý khách vui lòng đăng nhập..." prompt</li>
 *   <li>LOGGED_OUT - login screen</li>
 * </ul>
 */
public class LoginFlow {

    private static final Logger LOG = LoggerFactory.getLogger(LoginFlow.class);
    private static final By GUEST_HEADER = Locators.a11y("BIDV Securities");
    private static final String BTN_LOGIN_PROMPT = "Đăng nhập";

    public enum AppState { LOGGED_IN, GUEST, LOGGED_OUT, UNKNOWN }

    private final LoginScreen loginScreen = new LoginScreen();
    private final BottomNavBar bottomNav = new BottomNavBar();
    private final AlertDialog dialog = new AlertDialog();

    /** Waits (up to app-launch timeout) until a known state is reached. */
    public AppState detectState() {
        AppState[] state = {AppState.UNKNOWN};
        WaitUtils.condition(() -> {
            if (loginScreen.isDisplayed(Duration.ofMillis(300))) {
                state[0] = AppState.LOGGED_OUT;
                return true;
            }
            if (bottomNav.isDisplayed(Duration.ofMillis(300))) {
                state[0] = WaitUtils.isVisible(GUEST_HEADER, Duration.ofMillis(300)) ? AppState.GUEST : AppState.LOGGED_IN;
                return true;
            }
            dismissDialogIfAny();
            hideKeyboardSilently();
            return false;
        }, AppConfig.appLaunchTimeout());
        LOG.info("Detected app state: {}", state[0]);
        return state[0];
    }

    /**
     * Logs in if needed. With BSC_USERNAME/BSC_PASSWORD in .env the login is automatic; otherwise the
     * test pauses and the user types the credentials on the device ({@link #loginManually()}).
     */
    @Step("Đảm bảo đã đăng nhập")
    public MarketScreen ensureLoggedIn() {
        AppState s = detectState();
        if (s == AppState.LOGGED_IN) {
            return new MarketScreen();
        }
        if (!AppConfig.hasCredentials()) {
            return loginManually();
        }
        if (s == AppState.GUEST) {
            openLoginFromGuest();
        }
        return loginWithValidAccount();
    }

    /** Excel steps 2-3: Menu -> "Đăng nhập" (guest mode). */
    @Step("Mở màn đăng nhập qua Menu")
    public LoginScreen openLoginViaMenu() {
        AppState s = detectState();
        if (s == AppState.LOGGED_OUT) {
            return loginScreen;
        }
        if (s == AppState.LOGGED_IN) {
            throw new IllegalStateException("Already logged in - log out first");
        }
        new AppHeader().openMenu().tapLogin();
        return loginScreen.waitUntilLoaded();
    }

    /**
     * Excel TC 1 with manual credentials: Menu -> Đăng nhập -> Đăng nhập tài khoản khác -> clear username
     * -> USER types username + password on the phone -> Đăng nhập.
     * The framework taps "Đăng nhập" once both fields are filled and the keyboard is closed; if the user
     * taps it themselves (or completes an OTP step) that is accepted too.
     */
    @Step("Đăng nhập (người dùng tự nhập username/password trên điện thoại)")
    public MarketScreen loginManually() {
        openLoginViaMenu();
        loginScreen.useFullForm();
        loginScreen.clearUsername();
        ManualInput.waitForUser(
                "Nhập USERNAME và PASSWORD trên điện thoại, rồi ẨN BÀN PHÍM (hoặc tự bấm Đăng nhập).",
                () -> isLoggedInNow() || loginScreen.isFormFilledByUser());
        // The tester may tap "Đăng nhập" at the same moment: only tap if still there, and tolerate the
        // button disappearing mid-tap (screen transition) - the wait below decides the outcome.
        if (!isLoggedInNow() && loginScreen.isDisplayed(Duration.ofSeconds(1))) {
            try {
                loginScreen.tapLogin();
            } catch (org.openqa.selenium.WebDriverException e) {
                LOG.info("Login button vanished before the tap (tester already tapped it) - continuing");
            }
        }
        boolean ok = WaitUtils.condition(() -> {
            if (isLoggedInNow()) {
                return true;
            }
            if (dialog.isShown(Duration.ofMillis(300))) {
                // A modal hides the page below: close it first, then see where we are.
                String msg = dialog.getMessage();
                dismissDialogIfAny();
                if (loginScreen.isDisplayed(Duration.ofSeconds(1))) {
                    throw new AssertionError("Đăng nhập thất bại: " + msg);
                }
            }
            return false;
        }, ManualInput.timeout()); // allows time for an OTP step typed by the user
        if (!ok) {
            throw new AssertionError("Login did not reach the main screen");
        }
        return new MarketScreen();
    }

    public boolean isLoggedInNow() {
        return bottomNav.isDisplayed(Duration.ofMillis(300)) && !WaitUtils.isVisible(GUEST_HEADER, Duration.ofMillis(200));
    }

    @Step("Từ chế độ khách mở màn hình đăng nhập")
    public LoginScreen openLoginFromGuest() {
        hideKeyboardSilently();
        bottomNav.open(BottomNavBar.Item.TRADING);   // guest -> "Quý khách vui lòng đăng nhập..." prompt
        dialog.waitUntilLoaded();
        dialog.tapButton(BTN_LOGIN_PROMPT);
        return loginScreen.waitUntilLoaded();
    }

    @Step("Đăng nhập bằng tài khoản hợp lệ (.env)")
    public MarketScreen loginWithValidAccount() {
        loginScreen.waitUntilLoaded();
        loginScreen.loginAs(AppConfig.username(), AppConfig.password());
        boolean ok = WaitUtils.condition(() -> {
            if (bottomNav.isDisplayed(Duration.ofMillis(500)) && !WaitUtils.isVisible(GUEST_HEADER, Duration.ofMillis(200))) {
                return true;
            }
            dismissDialogIfAny(); // e.g. "enable biometric login?" or promo popup after login
            return false;
        }, AppConfig.appLaunchTimeout());
        if (!ok) {
            throw new AssertionError("Login with valid account did not reach the main screen");
        }
        return new MarketScreen();
    }

    @Step("Đảm bảo đang ở màn hình đăng nhập")
    public LoginScreen ensureLoggedOut() {
        AppState s = detectState();
        if (s == AppState.LOGGED_IN) {
            logout();
        }
        if (detectState() == AppState.GUEST) {
            openLoginFromGuest();
        }
        return loginScreen.waitUntilLoaded();
    }

    /** Logs out; the app then shows either the login screen or the guest home. */
    @Step("Đăng xuất")
    public void logout() {
        AlertDialog confirm = new AppHeader().openMenu().tapLogout();
        confirm.tapButton(AlertDialog.BTN_YES);
        WaitUtils.condition(() -> loginScreen.isDisplayed(Duration.ofMillis(300))
                || WaitUtils.isVisible(GUEST_HEADER, Duration.ofMillis(300)), Duration.ofSeconds(20));
    }

    /** Closes informational popups using the least destructive button available. */
    private void dismissDialogIfAny() {
        if (!dialog.isShown(Duration.ofMillis(300))) {
            return;
        }
        for (String btn : new String[]{AlertDialog.BTN_CLOSE, AlertDialog.BTN_NO, "Để sau", "Bỏ qua"}) {
            if (dialog.hasButton(btn)) {
                LOG.info("Dismissing popup '{}' with button '{}'", dialog.getMessage(), btn);
                dialog.tapButton(btn);
                return;
            }
        }
    }

    private void hideKeyboardSilently() {
        try {
            if (DriverManager.getDriver().isKeyboardShown()) {
                DriverManager.getDriver().hideKeyboard();
            }
        } catch (Exception ignored) {
        }
    }
}
