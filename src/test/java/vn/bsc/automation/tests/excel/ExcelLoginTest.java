package vn.bsc.automation.tests.excel;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;
import vn.bsc.automation.base.BaseTest;
import vn.bsc.automation.flows.LoginFlow.AppState;
import vn.bsc.automation.screens.LoginScreen;
import vn.bsc.automation.screens.components.AppHeader;
import vn.bsc.automation.screens.components.BottomNavBar;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenarios from "Kịch bản kiểm thử sau khi update App.xlsx" - section "Đăng nhập/Đăng xuất/Đổi mật khẩu".
 * Username/password are typed by the tester on the phone while the test waits (never stored).
 */
@Epic("Kịch bản Excel - sau khi update App")
@Feature("Đăng nhập/Đăng xuất/Đổi mật khẩu")
public class ExcelLoginTest extends BaseTest {

    @Test(groups = {"excel", "manual-input"})
    @Severity(SeverityLevel.BLOCKER)
    @Description("""
            Excel No.1 - Đăng nhập
            1. Mở app BSC Smart Invest
            2. Chạm vào Menu
            3. Chạm vào nút Đăng nhập
            4. Chạm vào Đăng nhập bằng tài khoản khác
            5. Xóa hết thông tin ở username
            6. Nhập username/password (người kiểm thử nhập tay trên điện thoại)
            7. Chạm vào nút Đăng nhập
            Expected: Đăng nhập thành công""")
    public void excel01_loginWithOtherAccount() {
        // Precondition: start logged out (step 2 "Menu -> Đăng nhập" only exists in guest mode)
        if (loginFlow.detectState() == AppState.LOGGED_IN) {
            loginFlow.logout();
        }

        // Steps 2-7
        loginFlow.loginManually();

        // Expected: Đăng nhập thành công
        assertThat(new BottomNavBar().isDisplayed()).as("Vào màn hình chính").isTrue();
        assertThat(loginFlow.detectState()).as("Trạng thái đăng nhập").isEqualTo(AppState.LOGGED_IN);
        assertThat(new AppHeader().getAccountText()).as("Header hiển thị tài khoản (Tên - Mã TK)")
                .matches("(?s).+ - [0-9]{3}C[0-9A-Z]+.*");
        assertThat(new LoginScreen().isDisplayed(java.time.Duration.ofSeconds(1))).as("Không còn ở màn đăng nhập").isFalse();
    }
}
