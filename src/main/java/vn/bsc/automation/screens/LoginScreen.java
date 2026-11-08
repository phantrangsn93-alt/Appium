package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;

import java.time.Duration;

/**
 * Login screen. Two variants exist:
 * <ul>
 *   <li><b>Remembered account</b>: shows customer name + custody code, only a password field and the link
 *       "Đăng nhập tài khoản khác".</li>
 *   <li><b>Full form</b>: username (pre-filled with "002C" prefix + autocomplete) and password.</li>
 * </ul>
 * Flutter TextFields have no id/desc -> located by the {@code password} attribute.
 */
public class LoginScreen extends BaseScreen {

    private final By usernameInput = vn.bsc.automation.core.utils.Locators.textField();
    private final By passwordInput = vn.bsc.automation.core.utils.Locators.passwordField();
    private final By loginButton = Locators.classWithDesc("android.widget.Button", "Đăng nhập");
    private final By forgotPasswordLink = Locators.a11y("Quên mật khẩu?");
    private final By otherAccountLink = Locators.a11y("Đăng nhập tài khoản khác");
    /** Custody code such as 002C123456 shown in remembered mode. */
    private final By rememberedAccount = vn.bsc.automation.core.utils.Locators.xpath("//android.view.View[@clickable='false' and "
            + "string-length(@content-desc)=10 and substring(@content-desc,4,1)='C']");

    /** "Quên mật khẩu?" exists only on the login screen (the guest prompt dialog also has "Đăng nhập"). */
    @Override
    protected By anchor() {
        return forgotPasswordLink;
    }

    public boolean isRememberedMode() {
        return isVisible(otherAccountLink, Duration.ofSeconds(2));
    }

    /** Custody code in remembered mode, or null in full-form mode. */
    public String getRememberedAccount() {
        return isRememberedMode() ? getDesc(rememberedAccount) : null;
    }

    @Step("Chuyển sang form đăng nhập đầy đủ (Đăng nhập tài khoản khác)")
    public LoginScreen useFullForm() {
        if (isRememberedMode()) {
            tap(otherAccountLink);
            find(usernameInput);
        }
        return this;
    }

    @Step("Nhập tài khoản: {username}")
    public LoginScreen enterUsername(String username) {
        type(usernameInput, username);
        dismissKeyboard(); // also closes the account autocomplete (it covers the password field)
        return this;
    }

    /**
     * Android: hide the keyboard. iOS: move to the password field with "Tiếp" (tapping elsewhere leaves the
     * account autocomplete over the password field, and a tap there would pick the suggested account).
     */
    private void dismissKeyboard() {
        if (!vn.bsc.automation.core.config.Platform.ios()) {
            hideKeyboard();
            return;
        }
        // "Tiếp" in the ACCOUNT field moves the focus to the password field and closes the autocomplete
        // (verified: it does not submit); "Xong" or "Tiếp" in the password field would submit.
        if (vn.bsc.automation.core.driver.DeviceActions.isKeyboardShown()) {
            vn.bsc.automation.core.driver.DeviceActions.tapKeyboardKey("Tiếp", "Next");
        }
    }

    @Step("Nhập mật khẩu")
    public LoginScreen enterPassword(String password) {
        type(passwordInput, password);
        return this;
    }

    @Step("Bấm Đăng nhập")
    public void tapLogin() {
        if (!vn.bsc.automation.core.config.Platform.ios()) {
            hideKeyboard(); // iOS: the button stays above the keyboard; its keys would submit the form
        }
        tap(loginButton);
    }

    /** Works with both variants: re-uses the remembered account when it matches. */
    public void loginAs(String username, String password) {
        if (!username.equals(getRememberedAccount())) {
            useFullForm().enterUsername(username);
        }
        enterPassword(password);
        tapLogin();
    }

    @Step("Xoá hết thông tin ở username")
    public LoginScreen clearUsername() {
        org.openqa.selenium.WebElement el = find(usernameInput);
        vn.bsc.automation.core.driver.DeviceActions.tap(el);
        vn.bsc.automation.core.driver.DeviceActions.clear(el);
        return this;
    }

    /** Password field shows one "•" per typed character. */
    public int getPasswordLength() {
        String t = getText(passwordInput);
        return t == null ? 0 : t.length();
    }

    /** Both fields typed by the user and keyboard closed. */
    public boolean isFormFilledByUser() {
        String user = getText(usernameInput);
        return user != null && user.length() >= 6 && getPasswordLength() > 0 && !vn.bsc.automation.core.driver.DeviceActions.isKeyboardShown();
    }

    public String getUsernameValue() {
        return getText(usernameInput);
    }

    public boolean isPasswordMasked() {
        org.openqa.selenium.WebElement f = find(passwordInput);
        if (vn.bsc.automation.core.config.Platform.ios()) {
            // no "password" attribute on iOS: masked = secure field, or an obscured value ("•••") / empty hint field
            String v = f.getAttribute("value");
            return "XCUIElementTypeSecureTextField".equals(f.getAttribute("type")) || v == null || v.matches("[•●]*")
                    || v.equals(f.getAttribute("label"));
        }
        return Boolean.parseBoolean(f.getAttribute("password"));
    }

    public boolean isUsernameFieldVisible() {
        return isVisible(usernameInput);
    }

    public boolean isPasswordFieldVisible() {
        return isVisible(passwordInput);
    }

    public boolean isForgotPasswordVisible() {
        return isVisible(forgotPasswordLink);
    }

    public boolean isLoginButtonVisible() {
        return isVisible(loginButton);
    }
}
