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

    /** Submits the transaction (REAL on the connected environment). */
    @Step("Bấm Xác nhận")
    public void tapConfirm() {
        tap(CONFIRM);
    }
}
