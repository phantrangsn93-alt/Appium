package vn.bsc.automation.tests.login;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.assertj.core.api.SoftAssertions;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.BaseTest;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.data.TestDataReader;
import vn.bsc.automation.screens.LoginScreen;
import vn.bsc.automation.screens.MenuScreen;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.AppHeader;
import vn.bsc.automation.screens.components.BottomNavBar;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("BSC Mobile Trading")
@Feature("Đăng nhập / Đăng xuất")
public class LoginTest extends BaseTest {

    private final LoginScreen loginScreen = new LoginScreen();
    private final AlertDialog dialog = new AlertDialog();

    /** Tests that need the real account from .env. */
    private static final java.util.Set<String> NEEDS_ACCOUNT = java.util.Set.of(
            "loginWithValidAccount", "logoutCancelKeepsSession", "logoutConfirmReturnsToLogin");

    /**
     * Each test starts from the login screen with no dialog.
     * Without .env credentials: account-based tests are skipped, and we never log out an existing
     * session (it could not be restored for the following suites).
     */
    @BeforeMethod(alwaysRun = true)
    public void toLoginScreen(java.lang.reflect.Method m) {
        boolean hasAccount = AppConfig.hasCredentials();
        if (!hasAccount && NEEDS_ACCOUNT.contains(m.getName())) {
            throw new SkipException("BSC_USERNAME / BSC_PASSWORD not set in .env");
        }
        if (dialog.isShown(Duration.ofMillis(500)) && dialog.hasButton(AlertDialog.BTN_CLOSE)) {
            dialog.close();
        }
        if (m.getName().startsWith("logout")) {
            return;
        }
        if (!hasAccount && loginFlow.detectState() == vn.bsc.automation.flows.LoginFlow.AppState.LOGGED_IN) {
            throw new SkipException("App is logged in and .env has no account: refusing to log out a session "
                    + "that could not be restored");
        }
        loginFlow.ensureLoggedOut();
    }

    /** Leave the app logged in for the next suites. */
    @AfterClass(alwaysRun = true)
    public void restoreSession() {
        if (AppConfig.hasCredentials()) {
            loginFlow.ensureLoggedIn();
        }
    }

    @Test(groups = {"smoke", "regression", "login"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_LOGIN_01 - Màn hình đăng nhập hiển thị đủ thành phần, mật khẩu được che")
    public void loginScreenShowsAllElements() {
        loginScreen.useFullForm();
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(loginScreen.isUsernameFieldVisible()).as("Ô tài khoản").isTrue();
        soft.assertThat(loginScreen.isPasswordFieldVisible()).as("Ô mật khẩu").isTrue();
        soft.assertThat(loginScreen.isForgotPasswordVisible()).as("Link Quên mật khẩu?").isTrue();
        soft.assertThat(loginScreen.isLoginButtonVisible()).as("Nút Đăng nhập").isTrue();
        soft.assertThat(loginScreen.isPasswordMasked()).as("Mật khẩu được che").isTrue();
        soft.assertAll();
    }

    @DataProvider(name = "invalidLogins")
    public Object[][] invalidLogins() {
        List<Object[]> rows = new ArrayList<>();
        for (JsonNode n : TestDataReader.read("login.json").get("invalidLogins")) {
            rows.add(new Object[]{
                    n.get("id").asText(), n.get("title").asText(),
                    n.get("username").asText(), n.get("password").asText(),
                    n.get("expectedMessage").isNull() ? null : n.get("expectedMessage").asText()
            });
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(groups = {"regression", "login"}, priority = 2, dataProvider = "invalidLogins")
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_LOGIN_02..04 - Đăng nhập không hợp lệ hiển thị thông báo lỗi và không vào được app")
    public void loginWithInvalidData(String id, String title, String username, String password, String expectedMessage) {
        loginScreen.useFullForm().enterUsername(username).enterPassword(password).tapLogin();

        assertThat(dialog.isShown(Duration.ofSeconds(15)))
                .as("[%s] %s: phải hiển thị thông báo lỗi", id, title).isTrue();
        String message = dialog.getMessage();
        AllureUtils.attachText(id + " - error message", message);
        assertThat(message).as("[%s] nội dung thông báo", id).isNotBlank();
        if (expectedMessage != null) {
            assertThat(message).as("[%s] nội dung thông báo", id).contains(expectedMessage);
        }
        dialog.close();
        assertThat(loginScreen.isDisplayed(Duration.ofSeconds(5)))
                .as("[%s] vẫn ở màn hình đăng nhập", id).isTrue();
        assertThat(new BottomNavBar().isDisplayed(Duration.ofSeconds(2)))
                .as("[%s] không được vào màn hình chính", id).isFalse();
    }

    @Test(groups = {"smoke", "regression", "login"}, priority = 3)
    @Severity(SeverityLevel.BLOCKER)
    @Description("TC_LOGIN_05 - Đăng nhập thành công bằng tài khoản hợp lệ")
    public void loginWithValidAccount() {
        loginFlow.loginWithValidAccount();

        assertThat(new BottomNavBar().isDisplayed()).as("Bottom navigation hiển thị").isTrue();
        assertThat(new AppHeader().showsAccount(AppConfig.username()))
                .as("Header hiển thị đúng mã tài khoản").isTrue();
    }

    @Test(groups = {"regression", "login"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_LOGIN_06 - Huỷ đăng xuất (chọn 'Không') vẫn giữ phiên đăng nhập")
    public void logoutCancelKeepsSession() {
        loginFlow.ensureLoggedIn();
        MenuScreen menu = new AppHeader().openMenu();
        AlertDialog confirm = menu.tapLogout();

        assertThat(confirm.getMessage()).contains("Bạn có muốn đăng xuất không?");
        assertThat(confirm.hasButton(AlertDialog.BTN_YES)).isTrue();
        assertThat(confirm.hasButton(AlertDialog.BTN_NO)).isTrue();

        confirm.tapButton(AlertDialog.BTN_NO);
        assertThat(menu.isDisplayed(Duration.ofSeconds(5))).as("Vẫn ở Menu sau khi huỷ").isTrue();
        menu.close();
        assertThat(new BottomNavBar().isDisplayed()).as("Vẫn đăng nhập").isTrue();
    }

    @Test(groups = {"regression", "login"}, priority = 5)
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_LOGIN_07 - Đăng xuất thành công, không còn phiên, màn đăng nhập nhớ tài khoản")
    public void logoutConfirmReturnsToLogin() {
        loginFlow.ensureLoggedIn();
        loginFlow.logout();

        assertThat(new AppHeader().showsAccount(AppConfig.username()))
                .as("Không còn hiển thị tài khoản sau khi đăng xuất").isFalse();
        LoginScreen screen = loginFlow.ensureLoggedOut();
        assertThat(screen.isDisplayed()).as("Màn hình đăng nhập").isTrue();
        String remembered = screen.isRememberedMode() ? screen.getRememberedAccount() : screen.getUsernameValue();
        assertThat(remembered).as("Tài khoản được ghi nhớ").contains(AppConfig.username());
    }
}
