package vn.bsc.automation.screens;

import java.util.Map;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.utils.Locators;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.openqa.selenium.WebElement;
import vn.bsc.automation.core.driver.DeviceActions;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.ManualInput;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.screens.components.OrderConfirmDialog;

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

    /**
     * Types the amount with REAL key events (Flutter fields ignore setText - the value would look filled but be
     * empty for the app) and closes the keyboard with the IME "done" action (never BACK: it would leave the form).
     */
    @Step("Nhập số tiền / khối lượng: {amount}")
    public TransactionFormScreen typeAmount(String amount) {
        WebElement field = find(AMOUNT);
        DeviceActions.tap(field);
        DeviceActions.typeKeys(field, amount);
        try {
            if (DeviceActions.isKeyboardShown()) {
                DeviceActions.keyboardDone();
            }
        } catch (Exception ignored) {
            // keyboard already closed
        }
        return this;
    }

    /**
     * Dropdown field "label\nvalue" with an empty value: open it, wait (options load slowly on production) and pick
     * the first option. Returns the chosen option label (null when the field already had a value).
     */
    @Step("Chọn lựa chọn đầu tiên cho: {label}")
    public String selectFirstOptionIfEmpty(String label) {
        By field = Locators.field(label);
        String value = getDesc(find(field));
        String current = value.contains("\n") ? value.substring(value.indexOf('\n') + 1).trim() : "";
        if (!current.isEmpty()) {
            return null;
        }
        tap(field);
        By close = Locators.classWithDesc("android.widget.Button", "Đóng");
        List<UiNode> options = new ArrayList<>();
        WaitUtils.condition(() -> {
            options.clear();
            if (!isVisible(close, Duration.ofMillis(300))) {
                return false;
            }
            UiNode.snapshot(driver().getPageSource()).stream()
                    .filter(n -> n.clickable() && n.desc().contains("\n") && !n.className().endsWith("Button"))
                    .forEach(options::add);
            return !options.isEmpty();
        }, Duration.ofSeconds(15));
        if (options.isEmpty()) {
            AllureUtils.attachScreenshot("Không có lựa chọn cho " + label);
            throw new AssertionError("Không có lựa chọn nào cho \"" + label + "\" sau 15 giây");
        }
        UiNode first = options.stream()
                .min(Comparator.comparingInt(UiNode::y)).get();
        GestureUtils.tap(first);
        WaitUtils.waitInvisible(close, Duration.ofSeconds(5));
        log.info("{}: đã chọn lựa chọn đầu tiên", label); // option holds an account number: not logged
        return first.desc();
    }

    public String amountText() {
        return getText(AMOUNT);
    }

    /**
     * REAL transaction: form "Xác nhận" -> summary sheet "Xác nhận thông tin" -> its "Xác nhận"
     * (+ PIN when the app asks for it; "Đã xác thực" = PIN remembered for the session).
     */
    @Step("Xác nhận giao dịch")
    public void submit() {
        scrollTo(CONFIRM);
        tap(CONFIRM);
        By summary = Locators.descStartsWith("Xác nhận thông tin");
        // The "Chọn kênh nhận mã SMS-OTP" picker opens OVER the summary sheet and hides its title (Android and iOS)
        WaitUtils.condition(() -> isVisible(summary, Duration.ofMillis(300)) || otpOnScreen(), Duration.ofSeconds(8));
        if (otpOnScreen()) {
            AllureUtils.attachScreenshot("Xác nhận thông tin (OTP) - " + title());
            // Done ONLY when the app shows the result (dialog with "Đóng"), or when the OTP step and the summary
            // stay gone for 3 s in a row: screen transitions while the tester picks the channel / types the code
            // briefly show neither, and must not end the wait (that cut the tester off on 25/11/2026).
            By resultClose = Locators.classWithDesc("android.widget.Button", "Đóng");
            int[] closedPolls = {0};
            ManualInput.waitForUser("Giao dịch " + title()
                            + ": chọn kênh nhận mã, bấm Lấy OTP, nhập OTP rồi bấm Xác nhận trên điện thoại",
                    () -> {
                        if (isVisible(resultClose, Duration.ofMillis(200))) {
                            return true;
                        }
                        boolean closed = !otpOnScreen() && !isVisible(summary, Duration.ofMillis(200));
                        closedPolls[0] = closed ? closedPolls[0] + 1 : 0;
                        return closedPolls[0] >= 6; // ManualInput polls every 500 ms -> 3 s
                    });
            return;
        }
        if (isVisible(summary, Duration.ofSeconds(1))) {
            AllureUtils.attachScreenshot("Xác nhận thông tin - " + title());
            // Production: the summary sheet itself asks for an OTP ("Lấy OTP" + input). The tester requests and types
            // it and taps "Xác nhận"; the test only waits for the sheet to close.
            By otp = Locators.descMatchesAny("Lấy OTP", "Lấy mã OTP");
            if (isVisible(otp, Duration.ofSeconds(2))
                    || isVisible(Locators.editText(0), Duration.ofSeconds(1))) {
                ManualInput.waitForUser("Giao dịch " + title()
                                + ": bấm Lấy OTP, nhập OTP rồi bấm Xác nhận trên điện thoại",
                        () -> !isVisible(summary, Duration.ofMillis(300)));
                return;
            }
            List<UiNode> nodes =
                    UiNode.snapshot(driver().getPageSource());
            UiNode btn = nodes.stream()
                    .filter(n -> "Xác nhận".equals(n.desc()) && n.clickable())
                    .max(Comparator.comparingInt(UiNode::y))
                    .orElseThrow(() -> new AssertionError("Nút Xác nhận trên màn Xác nhận thông tin không thấy"));
            GestureUtils.tap(btn);
            // PIN step (only when the app asks for it)
            OrderConfirmDialog pin = new OrderConfirmDialog();
            if (isVisible(Locators.passwordField(), Duration.ofSeconds(2))
                    || isVisible(Locators.descMatchesAny("Lấy OTP", "Lấy mã OTP"), Duration.ofSeconds(1))) {
                pin.enterPinAndConfirm("Giao dịch " + title()); // PIN/OTP typed by the tester when not in .env
            }
        }
    }

    /** OTP step on screen: channel picker or the "Lấy OTP" input (raw, NFC-normalised page source). */
    private boolean otpOnScreen() {
        String src = Locators.nfc(driver().getPageSource());
        return src.contains("Chọn kênh nhận mã") || src.contains("Lấy OTP") || src.contains("Lấy mã OTP");
    }

    /** Submits the transaction (REAL on the connected environment). */
    @Step("Bấm Xác nhận")
    public void tapConfirm() {
        tap(CONFIRM);
    }

    /**
     * Balance labels the transaction forms use for the money that the transaction takes from (surveyed 25/11/2026):
     * Chuyển tiền "Số tiền tối đa được chuyển", Nộp tiền phái sinh "Số dư" (sending sub-account), Rút tiền phái sinh
     * "Tiền mặt khả dụng" (derivative account). The others are the usual names for the same figure.
     */
    public static final List<String> BALANCE_LABELS = List.of("Số tiền tối đa được chuyển", "Tiền mặt khả dụng",
            // Giải toả: the money it can release (before "Số dư khả dụng", which the same form also shows)
            "Số tiền có thể giải toả", "Số tiền có thể giải tỏa",
            "Số dư khả dụng", "Số tiền khả dụng", "Số tiền có thể rút", "Số tiền có thể nộp",
            "Số tiền có thể phong toả", "Số tiền có thể phong tỏa", "Số dư tiền", "Số dư");

    /** Label + amount of the balance the form shows for the source of the money; null when it shows none. */
    public Map.Entry<String, Long> sourceBalance() {
        for (String label : BALANCE_LABELS) {
            String v = valueOrNull(label);
            if (v != null) {
                return Map.entry(label, Long.parseLong(v));
            }
        }
        return null;
    }

    /**
     * Chuyển chứng khoán: the "Mã CK" field opens a list of the symbols HELD by the sending sub-account (surveyed
     * 25/11/2026: MR90_BSC -> BSI, TNG). Picks {@code wanted}, or the first held symbol when null; returns it.
     */
    @Step("Chọn mã chứng khoán đang có trong tiểu khoản chuyển")
    public String pickHeldSymbol(String wanted) {
        UiNode label = UiNode.snapshot(driver().getPageSource()).stream()
                .filter(n -> n.desc().equals("Mã CK") || n.desc().startsWith("Mã CK\n")).findFirst()
                .orElseThrow(() -> new AssertionError("Chuyển chứng khoán: không thấy ô Mã CK"));
        int w = driver().manage().window().getSize().getWidth();
        GestureUtils.tap((int) (w * 0.9), label.y() + label.height() * 2); // the field row (search icon) under the label
        List<UiNode> options = new ArrayList<>();
        WaitUtils.condition(() -> {
            options.clear();
            UiNode.snapshot(driver().getPageSource()).stream()
                    .filter(n -> n.clickable() && n.y() > label.y() && n.desc().matches("[A-Z0-9]{3,10}"))
                    .forEach(options::add);
            return !options.isEmpty();
        }, Duration.ofSeconds(8));
        if (options.isEmpty()) {
            throw new AssertionError("Chuyển chứng khoán: tiểu khoản chuyển không có mã chứng khoán nào trong danh sách");
        }
        UiNode pick = wanted == null ? options.get(0)
                : options.stream().filter(o -> o.desc().equals(wanted)).findFirst()
                .orElseThrow(() -> new AssertionError("Mã " + wanted + " không còn trong danh sách mã của tiểu khoản chuyển"));
        String symbol = pick.desc();
        for (int attempt = 0; attempt < 2; attempt++) {
            WaitUtils.pause(Duration.ofMillis(500)); // the list finishes opening: an earlier tap is swallowed
            Optional<UiNode> o = UiNode.snapshot(driver().getPageSource()).stream()
                    .filter(n -> n.clickable() && n.y() > label.y() && symbol.equals(n.desc())).findFirst();
            if (o.isEmpty()) {
                break; // list closed: the symbol was taken
            }
            GestureUtils.tap(o.get());
            if (WaitUtils.condition(() -> valueOrNull("Khối lượng có thể chuyển") != null, Duration.ofSeconds(6))) {
                return symbol;
            }
        }
        if (valueOrNull("Khối lượng có thể chuyển") == null) {
            throw new AssertionError("Chuyển chứng khoán: đã chọn mã " + symbol + " nhưng không hiện Khối lượng có thể chuyển");
        }
        return symbol;
    }

    /** "Khối lượng có thể chuyển" of the chosen symbol (Chuyển chứng khoán); null when not shown yet. */
    public Long transferableQuantity() {
        String v = valueOrNull("Khối lượng có thể chuyển");
        return v == null ? null : Long.parseLong(v);
    }

    private String valueOrNull(String label) {
        try {
            String v = valueBelow(label).replaceAll("[^0-9]", "");
            return v.isEmpty() ? null : v;
        } catch (AssertionError e) {
            return null;
        }
    }

    /** Value of the first of these "label\nvalue" fields present on the form (null when none). */
    public String firstFieldValue(String... labels) {
        for (String l : labels) {
            if (isVisible(Locators.field(l), Duration.ofSeconds(1))) {
                return fieldValue(l);
            }
        }
        return null;
    }
}
