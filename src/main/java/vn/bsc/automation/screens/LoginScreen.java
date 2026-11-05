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

    private final By usernameInput = By.xpath("//android.widget.EditText[@password='false']");
    private final By passwordInput = By.xpath("//android.widget.EditText[@password='true']");
    private final By loginButton = Locators.classWithDesc("android.widget.Button", "Đăng nhập");
    private final By forgotPasswordLink = Locators.a11y("Quên mật khẩu?");
    private final By otherAccountLink = Locators.a11y("Đăng nhập tài khoản khác");
    /** Custody code such as 002C123456 shown in remembered mode. */
    private final By rememberedAccount = By.xpath("//android.view.View[@clickable='false' and "
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
        hideKeyboard(); // also closes the account autocomplete
        return this;
    }

    @Step("Nhập mật khẩu")
    public LoginScreen enterPassword(String password) {
        type(passwordInput, password);
        return this;
    }

    @Step("Bấm Đăng nhập")
    public void tapLogin() {
        hideKeyboard();
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
        el.click();
        el.clear();
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
        return user != null && user.length() >= 6 && getPasswordLength() > 0 && !driver().isKeyboardShown();
    }

    public String getUsernameValue() {
        return getText(usernameInput);
    }

    public boolean isPasswordMasked() {
        return Boolean.parseBoolean(find(passwordInput).getAttribute("password"));
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
