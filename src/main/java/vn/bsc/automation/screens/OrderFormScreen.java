package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.NumberUtils;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.SelectedStock;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.NumericKeypad;
import vn.bsc.automation.screens.components.OrderConfirmDialog;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * "Đặt lệnh" form.
 * <p>
 * SAFETY: this is a production trading app. This screen object intentionally exposes NO method that
 * submits a real order. The only way to tap MUA/BÁN is {@link #tapSubmitExpectingValidation()}, which
 * refuses to run unless quantity == 0 (a value the app always rejects client-side).
 */
public class OrderFormScreen extends BaseScreen {

    public enum Side { BUY, SELL }

    public enum OrderType {
        LIMIT("Limit"), MARKET("Market"), CONDITIONAL("Điều kiện");

        private final String label;

        OrderType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Pattern DECIMAL_2 = Pattern.compile("^\\d+(,\\d{3})*\\.\\d{2}$");

    private final By buyTab = Locators.a11y("Lệnh mua");
    private final By sellTab = Locators.a11y("Lệnh bán");
    private final By symbolInput = Locators.editText(0);
    // Label becomes "MUA : 757,000" (order value) once a quantity is entered -> prefix match
    private final By buyButton = Locators.descStartsWith("MUA");
    // Rendered by the app in NFD form ("A" + U+0301); Locators.a11y matches NFC and NFD.
    private final By sellButton = Locators.descStartsWith("BÁN");
    private final By infoContainer = Locators.descContains("Sức mua:");
    private final By limitType = Locators.a11y(OrderType.LIMIT.label());
    private final By validityRadios = Locators.byClass("android.widget.RadioButton");

    /**
     * Price & quantity rows have no semantics labels: [ - ] [value] [ + ].
     * They are located geometrically (works in Limit and Market mode, any screen size):
     * the QUANTITY row is the stepper row right above the "25%" button, the PRICE row (Limit) the one above it.
     */
    private enum Row { PRICE, QUANTITY }

    private static final Pattern VALUE = Pattern.compile("^[0-9][0-9,]*(\\.[0-9]+)?$");

    /** The 3 nodes [minus, value, plus] of a stepper row, from one page-source snapshot. */
    private List<UiNode> stepperRow(Row row) {
        return stepperRowFromBottom(row == Row.QUANTITY ? 0 : 1,
                row == Row.PRICE ? "Price stepper not shown (Market order type?)" : "Quantity stepper not found");
    }

    /**
     * Stepper rows [minus, value, plus] counted from the bottom (0 = quantity, the row right above "25%").
     * Conditional forms have more rows (OCO: 4) and push "25%" below the fold -> scrolls down until it is visible.
     */
    private List<UiNode> stepperRowFromBottom(int idx, String notFound) {
        // small scrolls (15% of the window): a big scroll pushes the top rows (OCO: Giá đặt LO) out of view
        for (int attempt = 0; attempt < 6; attempt++) {
            List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
            java.util.OptionalInt pct = nodes.stream().filter(n -> "25%".equals(n.desc())).mapToInt(UiNode::y).findFirst();
            if (pct.isEmpty()) {
                GestureUtils.scrollBy(0.15);
                continue;
            }
            int pctTop = pct.getAsInt();
            // stepper rows = value nodes above "25%" that have a description-less [-] and [+] on the same line
            List<List<UiNode>> rows = nodes.stream()
                    .filter(n -> n.clickable() && n.y() < pctTop && VALUE.matcher(n.desc()).matches())
                    .sorted(Comparator.comparingInt(UiNode::y).reversed())
                    .map(v -> rowAround(nodes, v))
                    .filter(r -> r != null)
                    .collect(Collectors.toList());
            if (rows.size() > idx) {
                return rows.get(idx);
            }
            GestureUtils.scrollBy(-0.1); // the top rows scrolled out of view: back up a little ("25%" stays visible)
        }
        throw new AssertionError(notFound + " (or '25%' button not visible)");
    }

    /** [minus, value, plus] when the value has +/- buttons on its line, else null. */
    private static List<UiNode> rowAround(List<UiNode> nodes, UiNode value) {
        int cy = value.y() + value.height() / 2;
        List<UiNode> sameRow = nodes.stream()
                .filter(n -> n.clickable() && n.desc().isEmpty() && n.y() < cy && n.y() + n.height() > cy)
                .collect(Collectors.toList());
        UiNode minus = sameRow.stream().filter(n -> n.x() + n.width() <= value.x() + 5)
                .max(Comparator.comparingInt(UiNode::x)).orElse(null);
        UiNode plus = sameRow.stream().filter(n -> n.x() >= value.x() + value.width() - 5)
                .min(Comparator.comparingInt(UiNode::x)).orElse(null);
        return minus == null || plus == null ? null : List.of(minus, value, plus);
    }

    /** Evaluates a form check, treating a transient overlay (AssertionError: row not found) as "not yet". */
    private static boolean safe(java.util.function.Supplier<Boolean> check) {
        try {
            return check.get();
        } catch (AssertionError e) {
            return false;
        }
    }

    private static void tapNode(UiNode n) {
        GestureUtils.tap(n.x() + n.width() / 2, n.y() + n.height() / 2);
    }

    @Override
    protected By anchor() {
        return buyTab;
    }

    // ------------------------------------------------------------------ side

    @Step("Chọn Lệnh mua")
    public OrderFormScreen selectBuy() {
        tap(buyTab);
        WaitUtils.isVisible(buyButton, Duration.ofSeconds(5));
        return this;
    }

    @Step("Chọn Lệnh bán")
    public OrderFormScreen selectSell() {
        tap(sellTab);
        WaitUtils.isVisible(sellButton, Duration.ofSeconds(5));
        return this;
    }

    public Side currentSide() {
        hideKeyboard();
        try {
            if (isVisibleWithScroll(buyButton)) {
                return Side.BUY;
            }
            if (isVisible(sellButton, Duration.ofSeconds(2))) {
                return Side.SELL;
            }
            throw new AssertionError("Neither MUA nor BÁN button is visible");
        } finally {
            scrollToTop(); // never leave the form scrolled (next steps need the top of the form)
        }
    }

    /** Scrolls back until the Mua/Bán tabs are visible. */
    public OrderFormScreen scrollToTop() {
        for (int i = 0; i < 4 && !isVisible(buyTab, Duration.ofMillis(800)); i++) {
            GestureUtils.scrollUp();
        }
        return this;
    }

    // ------------------------------------------------------------------ symbol

    private static By suggestion(String symbol) {
        return vn.bsc.automation.core.utils.Locators.xpath("//android.view.View[starts-with(@content-desc,'" + symbol + "\n') and contains(@content-desc,' | ')]");
    }

    /**
     * Selected-symbol card = first multi-line node below the "Lệnh mua/Lệnh bán" tabs. Formats:
     * stock "AAA\n | \nHOSE\n7.08\n<name>\n<change>", derivative "41I1GB000\n1,867.40\nVN30F2611\n<change>".
     */
    private UiNode selectedCardNode() {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        int tabsBottom = nodes.stream().filter(n -> "Lệnh mua".equals(n.desc()))
                .mapToInt(n -> n.y() + n.height()).findFirst().orElseThrow(() -> new AssertionError("Mua/Bán tabs not visible"));
        return nodes.stream()
                .filter(n -> n.clickable() && n.y() >= tabsBottom && n.desc().contains("\n")
                        && n.desc().split("\n")[0].trim().matches("[A-Z0-9]{3,12}"))
                .min(Comparator.comparingInt(UiNode::y))
                .orElseThrow(() -> new AssertionError("No symbol selected on the order form"));
    }

    @Step("Nhập mã chứng khoán: {symbol}")
    public OrderFormScreen typeSymbol(String symbol) {
        if (!isVisible(symbolInput, Duration.ofSeconds(2))) {
            // A symbol is already selected: tapping its card re-opens the search input.
            tapNode(selectedCardNode());
        }
        type(symbolInput, symbol);
        return this;
    }

    public boolean isSuggestionVisible(String symbol) {
        return isVisible(suggestion(symbol), Duration.ofSeconds(10));
    }

    public String getSuggestionText(String symbol) {
        return getDesc(suggestion(symbol));
    }

    @Step("Chọn mã gợi ý: {symbol}")
    public OrderFormScreen chooseSuggestion(String symbol) {
        tap(suggestion(symbol));
        hideKeyboard();
        failOnAppError("chọn mã " + symbol);
        if (!WaitUtils.condition(() -> safe(() -> getSelectedStock().symbol().equals(symbol)), Duration.ofSeconds(10))) {
            throw new AssertionError("Không chọn được mã " + symbol + " trên form đặt lệnh");
        }
        return this;
    }

    public OrderFormScreen selectSymbol(String symbol) {
        return typeSymbol(symbol).chooseSuggestion(symbol);
    }

    public SelectedStock getSelectedStock() {
        return SelectedStock.parse(selectedCardNode().desc());
    }

    // ------------------------------------------------------------------ market info

    /**
     * Ceiling / reference / floor prices shown under the stock card: the first three "x.xx" values
     * located above the order-type row.
     */
    public List<BigDecimal> getCeilingRefFloor() {
        scrollToTop();
        List<List<BigDecimal>> holder = new java.util.ArrayList<>();
        boolean loaded = WaitUtils.condition(() -> safe(() -> {
            List<BigDecimal> v = readCeilingRefFloor();
            holder.clear();
            holder.add(v);
            return v.get(1).signum() > 0; // 0.00 = market data not loaded yet
        }), Duration.ofSeconds(15));
        if (!loaded) {
            throw new AssertionError("Không lấy được giá Trần/TC/Sàn (vẫn 0.00) cho mã đang chọn");
        }
        return holder.get(0);
    }

    private List<BigDecimal> readCeilingRefFloor() {
        find(limitType);
        // Prices refresh in real time -> read one consistent page-source snapshot instead of live
        // elements (avoids StaleElementReference while iterating).
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        int limitTop = nodes.stream().filter(n -> OrderType.LIMIT.label().equals(n.desc()))
                .findFirst().orElseThrow(() -> new AssertionError("'Limit' not found")).y();
        List<BigDecimal> prices = nodes.stream()
                .filter(n -> n.clickable() && n.y() < limitTop)
                .filter(n -> DECIMAL_2.matcher(n.desc()).matches())
                .sorted(Comparator.comparingInt(UiNode::y).thenComparingInt(UiNode::x))
                .map(n -> NumberUtils.parse(n.desc()))
                .collect(Collectors.toList());
        if (prices.size() < 3) {
            throw new AssertionError("Expected ceiling/ref/floor prices, found: " + prices);
        }
        return prices.subList(0, 3);
    }

    /** Value following a label inside the info container, e.g. "Sức mua:" -> "3,108,400". */
    private String infoValue(String label) {
        List<String> lines = Arrays.asList(getDesc(infoContainer).split("\n"));
        int i = lines.indexOf(label);
        if (i < 0 || i + 1 >= lines.size()) {
            throw new AssertionError("Label '" + label + "' not found in order info: " + lines);
        }
        return lines.get(i + 1).trim();
    }

    public long getBuyingPower() {
        return NumberUtils.parseLong(infoValue("Sức mua:"));
    }

    public String getMatchedVolumeText() {
        return infoValue("KL khớp lệnh:");
    }

    /**
     * "KL Tối đa" = maximum quantity for the current side. On the SELL side it is the available stock
     * balance of the symbol in the sub-account. Its value is a separate node (not inside the info container):
     * the integer just right of the label, on the line under the ceiling/ref/floor prices.
     */
    public long getMaxQuantity() {
        scrollToTop();
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        int limitTop = nodes.stream().filter(n -> OrderType.LIMIT.label().equals(n.desc()))
                .mapToInt(UiNode::y).findFirst().orElseThrow(() -> new AssertionError("Order type row not found"));
        int crfBottom = nodes.stream().filter(n -> n.y() < limitTop && DECIMAL_2.matcher(n.desc()).matches())
                .mapToInt(n -> n.y() + n.height()).max().orElse(0);
        int halfWidth = driver().manage().window().getSize().width / 2;
        return nodes.stream()
                .filter(n -> n.y() >= crfBottom && n.y() < limitTop && n.x() < halfWidth)
                .filter(n -> n.desc().matches("^[0-9][0-9,]*$"))
                .min(java.util.Comparator.comparingInt(UiNode::y))
                .map(n -> NumberUtils.parseLong(n.desc()))
                .orElseThrow(() -> new AssertionError("'KL Tối đa' value not found"));
    }

    /** Balance that an order of this side consumes: BUY -> Sức mua (VND), SELL -> KL Tối đa (shares). */
    public long getAvailableBalance(Side side) {
        return side == Side.BUY ? getBuyingPower() : getMaxQuantity();
    }

    // ------------------------------------------------------------------ order type / validity

    @Step("Chọn loại lệnh: {type}")
    public OrderFormScreen selectOrderType(OrderType type) {
        tap(Locators.a11y(type.label()));
        return this;
    }

    public boolean isOrderTypeVisible(OrderType type) {
        return isVisible(Locators.a11y(type.label()));
    }

    /**
     * "Điều kiện" order types (dropdown "Kiểu lệnh", bottom sheet of Buttons + "Đóng"). Fields per type
     * (surveyed 06/11/2026, hints are for BUY / SELL, x = reference price):
     * <pre>
     * STOP_LIMIT     Giá kích hoạt (&gt; x / &lt; x), Giá đặt, Khối lượng
     * TRAILING_STOP  Biên độ (≤ ...), Bước giá, Khối lượng
     * OCO            LO: Giá đặt (≤ x / ≥ x) + Stop Limit: Giá kích hoạt (&gt; x / &lt; x), Giá đặt; Khối lượng
     * </pre>
     * All types: Hiệu lực Trong ngày / Nhiều ngày + "Đến ngày".
     */
    public enum ConditionalType {
        STOP_LIMIT("Stop Limit"), TRAILING_STOP("Trailing Stop"), OCO("OCO");

        private final String label;

        ConditionalType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final By CONDITIONAL_TYPE = Locators.descMatchesAny("Stop Limit", "Trailing Stop", "OCO");

    @Step("Chọn lệnh điều kiện: {type}")
    public OrderFormScreen selectConditionalType(ConditionalType type) {
        selectOrderType(OrderType.CONDITIONAL);
        if (!type.label().equals(getConditionalType())) {
            tap(CONDITIONAL_TYPE);
            By option = Locators.classWithDesc("android.widget.Button", type.label());
            tap(option);
            WaitUtils.waitInvisible(option, Duration.ofSeconds(5));
            if (!WaitUtils.condition(() -> type.label().equals(getConditionalType()), Duration.ofSeconds(5))) {
                throw new AssertionError("Không chọn được kiểu lệnh điều kiện " + type.label());
            }
        }
        return this;
    }

    /** Current "Kiểu lệnh" of a conditional order (Stop Limit / Trailing Stop / OCO). */
    public String getConditionalType() {
        return getDesc(CONDITIONAL_TYPE);
    }

    /**
     * Value fields of a conditional order. Each is a stepper row [- value +] without its own label; position
     * counted from the bottom of the stepper block (0 = Khối lượng), surveyed 06/11/2026:
     * <pre>
     * STOP_LIMIT     2 Giá kích hoạt   1 Giá đặt (auto-filled by the app after the trigger, e.g. 8.03 -&gt; 8.13)
     * TRAILING_STOP  2 Biên độ         1 Bước giá
     * OCO            3 Giá đặt (LO)    2 Giá kích hoạt   1 Giá đặt (Stop Limit)
     * </pre>
     */
    public enum CondField {
        TRIGGER, ORDER_PRICE, AMPLITUDE, PRICE_STEP, LO_PRICE;

        int rowFromBottom(ConditionalType type) {
            return switch (type) {
                case STOP_LIMIT -> switch (this) {
                    case TRIGGER -> 2;
                    case ORDER_PRICE -> 1;
                    default -> throw new IllegalArgumentException(this + " not in Stop Limit");
                };
                case TRAILING_STOP -> switch (this) {
                    case AMPLITUDE -> 2;
                    case PRICE_STEP -> 1;
                    default -> throw new IllegalArgumentException(this + " not in Trailing Stop");
                };
                case OCO -> switch (this) {
                    case LO_PRICE -> 3;
                    case TRIGGER -> 2;
                    case ORDER_PRICE -> 1;
                    default -> throw new IllegalArgumentException(this + " not in OCO");
                };
            };
        }
    }

    private ConditionalType currentConditionalType() {
        String label = getConditionalType();
        return java.util.Arrays.stream(ConditionalType.values()).filter(t -> t.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("Not a conditional order: " + label));
    }

    public BigDecimal getConditional(CondField field) {
        int row = field.rowFromBottom(currentConditionalType());
        return NumberUtils.parse(stepperRowFromBottom(row, field + " row not found").get(1).desc());
    }

    /** Types a value (x1000, e.g. "8.03") into a conditional-order field with the app keypad. */
    @Step("Lệnh điều kiện - nhập {field}: {value}")
    public OrderFormScreen enterConditional(CondField field, String value) {
        int row = field.rowFromBottom(currentConditionalType());
        tapNode(stepperRowFromBottom(row, field + " row not found").get(1));
        NumericKeypad keypad = new NumericKeypad().waitUntilLoaded();
        keypad.enter(value);
        BigDecimal want = new BigDecimal(value);
        if (!WaitUtils.condition(() -> safe(() -> getConditional(field).compareTo(want) == 0), Duration.ofSeconds(5))) {
            throw new AssertionError(field + " trên form khác giá trị đã nhập " + value);
        }
        return this;
    }

    /**
     * Suggested value shown under a field label in the form, e.g. hint("Giá kích hoạt", 0) -&gt; 7.53 for "&gt; 7.53".
     * {@code occurrence} distinguishes repeated labels (OCO: "Giá đặt" #0 = LO leg, #1 = Stop Limit leg).
     * Returns null when the label has no numeric hint.
     */
    public BigDecimal hint(String label, int occurrence) {
        String want = Locators.nfc(label);
        String container = UiNode.snapshot(driver().getPageSource()).stream()
                .map(UiNode::desc).filter(d -> d.contains("Hiệu lực") && d.contains("Khối lượng"))
                .findFirst().orElseThrow(() -> new AssertionError("Order form container not found"));
        String[] lines = container.split("\n");
        int seen = 0;
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].trim().equals(want) && seen++ == occurrence) {
                String h = lines[i + 1].replaceAll("[^0-9.,]", "");
                return h.isEmpty() ? null : NumberUtils.parse(h);
            }
        }
        throw new AssertionError("Label not on the form: " + label + " #" + occurrence);
    }

    private static final Pattern DATE = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");

    /**
     * "Đến ngày" of a multi-day / conditional order (dd/MM/yyyy). Tapping it opens a date picker
     * (3 SeekBars day/month/year + "Huỷ bỏ" / "Đồng ý"). WARNING (06/11/2026): changing the sub-account while the
     * picker is open leaves it stuck on screen (neither button works) - only an app restart clears it.
     */
    public String getExpiryDate() {
        return UiNode.snapshot(driver().getPageSource()).stream()
                .filter(n -> n.clickable() && DATE.matcher(n.desc()).matches())
                .map(UiNode::desc).findFirst()
                .orElseThrow(() -> new AssertionError("'Đến ngày' not shown (validity is not multi-day?)"));
    }

    /** index 0 = "Trong ngày", 1 = "Nhiều ngày" (RadioButtons without content-desc) */
    public boolean isValidityChecked(int index) {
        return Boolean.parseBoolean(validityRadio(index).getAttribute(vn.bsc.automation.core.config.Platform.checkedAttr()));
    }

    @Step("Chọn hiệu lực index {index}")
    public OrderFormScreen selectValidity(int index) {
        org.openqa.selenium.WebElement radio = validityRadio(index);
        if (!Boolean.parseBoolean(radio.getAttribute(vn.bsc.automation.core.config.Platform.checkedAttr()))) {
            vn.bsc.automation.core.driver.DeviceActions.tap(radio);
            WaitUtils.condition(() -> Boolean.parseBoolean(validityRadio(index).getAttribute(vn.bsc.automation.core.config.Platform.checkedAttr())), Duration.ofSeconds(3));
        }
        return this;
    }

    /** The radios sit below the fold on conditional forms -> scroll them into view first. */
    private org.openqa.selenium.WebElement validityRadio(int index) {
        for (int i = 0; i < 4 && findAll(validityRadios).size() <= index; i++) {
            GestureUtils.scrollBy(0.2);
        }
        List<org.openqa.selenium.WebElement> radios = findAll(validityRadios);
        if (radios.size() <= index) {
            throw new AssertionError("Hiệu lực radio #" + index + " not found");
        }
        return radios.get(index);
    }

    // ------------------------------------------------------------------ price / quantity

    public BigDecimal getPrice() {
        return NumberUtils.parse(stepperRow(Row.PRICE).get(1).desc());
    }

    public long getQuantity() {
        return NumberUtils.parseLong(stepperRow(Row.QUANTITY).get(1).desc());
    }

    @Step("Tăng giá 1 bước")
    public OrderFormScreen increasePrice() {
        BigDecimal before = getPrice();
        tapNode(stepperRow(Row.PRICE).get(2));
        WaitUtils.condition(() -> safe(() -> getPrice().compareTo(before) != 0), Duration.ofSeconds(3));
        return this;
    }

    @Step("Giảm giá 1 bước")
    public OrderFormScreen decreasePrice() {
        BigDecimal before = getPrice();
        tapNode(stepperRow(Row.PRICE).get(0));
        WaitUtils.condition(() -> safe(() -> getPrice().compareTo(before) != 0), Duration.ofSeconds(3));
        return this;
    }

    @Step("Tăng khối lượng 1 bước")
    public OrderFormScreen increaseQuantity() {
        long before = getQuantity();
        tapNode(stepperRow(Row.QUANTITY).get(2));
        WaitUtils.condition(() -> safe(() -> getQuantity() != before), Duration.ofSeconds(3));
        return this;
    }

    @Step("Giảm khối lượng 1 bước")
    public OrderFormScreen decreaseQuantity() {
        long before = getQuantity();
        tapNode(stepperRow(Row.QUANTITY).get(0));
        WaitUtils.condition(() -> safe(() -> getQuantity() != before), Duration.ofSeconds(3));
        return this;
    }

    /** Types a price (x1000) with the app keypad, e.g. "7.08". */
    @Step("Nhập giá: {price}")
    public OrderFormScreen enterPrice(String price) {
        tapNode(stepperRow(Row.PRICE).get(1));
        new NumericKeypad().waitUntilLoaded();
        new NumericKeypad().enter(price);
        if (!WaitUtils.condition(() -> safe(() -> getPrice().compareTo(new BigDecimal(price)) == 0), Duration.ofSeconds(5))) {
            throw new AssertionError("Giá trên form khác giá đã nhập " + price);
        }
        return this;
    }

    /** Types a quantity with the app keypad (supports odd lots, e.g. 10). */
    @Step("Nhập khối lượng: {qty}")
    public OrderFormScreen enterQuantity(long qty) {
        tapNode(stepperRow(Row.QUANTITY).get(1));
        NumericKeypad keypad = new NumericKeypad();
        if (keypad.isDisplayed(Duration.ofSeconds(3))) {
            keypad.enter(String.valueOf(qty));
        } else if (qty % 100 == 0) {           // fallback: lot steps with "+"
            resetQuantity();
            for (long i = 0; i < qty / 100; i++) {
                increaseQuantity();
            }
        } else {
            throw new AssertionError("Quantity keypad not shown - cannot enter odd lot " + qty);
        }
        if (!WaitUtils.condition(() -> safe(() -> getQuantity() == qty), Duration.ofSeconds(5))) {
            throw new AssertionError("Khối lượng trên form khác KL đã nhập " + qty);
        }
        return this;
    }

    /**
     * If the app shows an error dialog (e.g. "Lỗi :ACCOUNTID_NOT_EXIT" while the back-end is in batch),
     * capture it and fail with the app's message instead of continuing blindly.
     */
    private void failOnAppError(String step) {
        AlertDialog d = new AlertDialog();
        if (d.isShown(Duration.ofSeconds(1))) {
            String msg = d.getMessage();
            vn.bsc.automation.core.utils.AllureUtils.attachScreenshot("Lỗi hệ thống khi " + step);
            if (d.hasButton(AlertDialog.BTN_CLOSE)) {
                d.close();
            }
            throw new AssertionError("Hệ thống báo lỗi khi " + step + ": " + msg);
        }
    }

    // ------------------------------------------------------------------ sub-account / market price type

    private static final Pattern SUB_ACCOUNT = Pattern.compile("^.+ - [0-9A-Z]{10}$");

    /** Current sub-account label in the header, e.g. "Thường_BSC - 0001000005". */
    public String getSubAccount() {
        return UiNode.snapshot(driver().getPageSource()).stream()
                .filter(n -> n.clickable() && SUB_ACCOUNT.matcher(n.desc()).matches())
                .min(Comparator.comparingInt(UiNode::y))
                .map(UiNode::desc).orElseThrow(() -> new AssertionError("Sub-account selector not found"));
    }

    /** Excel steps "Chạm vào drop down list tiểu khoản" + "Chọn tiểu khoản ...". {@code name} = prefix, e.g. "Thường_BSC". */
    @Step("Chọn tiểu khoản: {name}")
    public OrderFormScreen selectSubAccount(String name) {
        String current = getSubAccount();
        if (current.startsWith(Locators.nfc(name) + " - ")) {
            return this;
        }
        tap(Locators.a11y(current));
        tap(Locators.descStartsWith(name + " - "));
        failOnAppError("chọn tiểu khoản " + name);
        if (!WaitUtils.condition(() -> getSubAccount().startsWith(Locators.nfc(name) + " - "), Duration.ofSeconds(10))) {
            throw new AssertionError("Không chọn được tiểu khoản " + name);
        }
        return this;
    }

    /**
     * Market order: taps "Market" then picks the price type from the dropdown (HOSE: ATO/ATC/MTL,
     * HNX: ATC/MTL/MOK/MAK/PLO). {@code type} "MARKET" = Market tab only (UPCOM: price = ceiling, no dropdown).
     */
    @Step("Chọn lệnh thị trường: {type}")
    public OrderFormScreen selectMarketPriceType(String type) {
        selectOrderType(OrderType.MARKET);
        if ("MARKET".equals(type)) {
            return this;
        }
        By current = Locators.descMatchesAny("ATO", "ATC", "MTL", "MOK", "MAK", "PLO");
        boolean hasValue = isVisible(current, Duration.ofSeconds(2));
        if (!hasValue || !type.equals(getDesc(current))) {
            if (hasValue) {
                tap(current);
            } else {
                // Seen on device: after switching Limit -> Market the dropdown can be EMPTY (no default type)
                log.warn("Market price-type dropdown is empty - opening it by position");
                tapMarketDropdownArea();
            }
            By option = Locators.classWithDesc("android.widget.Button", type);
            if (!isVisible(option, Duration.ofSeconds(5))) {
                throw new AssertionError("Loại lệnh " + type + " không có cho mã này (dropdown không có lựa chọn)");
            }
            tap(option);
            WaitUtils.waitInvisible(option, Duration.ofSeconds(5)); // dropdown closed
            if (!WaitUtils.condition(() -> type.equals(getDesc(current)), Duration.ofSeconds(5))) {
                throw new AssertionError("Không chọn được loại lệnh " + type);
            }
        }
        return this;
    }

    /** Taps the (empty) price-type dropdown: the line between the order-type row and the quantity row. */
    private void tapMarketDropdownArea() {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        UiNode market = nodes.stream().filter(n -> OrderType.MARKET.label().equals(n.desc())).findFirst()
                .orElseThrow(() -> new AssertionError("Market tab not found"));
        UiNode conditional = nodes.stream().filter(n -> OrderType.CONDITIONAL.label().equals(n.desc())).findFirst()
                .orElse(market);
        UiNode qtyMinus = stepperRow(Row.QUANTITY).get(0);
        int y = (market.y() + market.height() + qtyMinus.y()) / 2;
        int x = (market.x() + conditional.x() + conditional.width()) / 2;
        GestureUtils.tap(x, y);
    }

    public String getMarketPriceType() {
        return getDesc(Locators.descMatchesAny("ATO", "ATC", "MTL", "MOK", "MAK", "PLO"));
    }

    @Step("Đưa khối lượng về 0")
    public OrderFormScreen resetQuantity() {
        for (int i = 0; i < 200 && getQuantity() > 0; i++) {
            decreaseQuantity();
        }
        return this;
    }

    @Step("Chọn {percent}% sức mua/KL tối đa")
    public OrderFormScreen tapPercent(int percent) {
        tap(Locators.a11y(percent + "%"));
        return this;
    }

    public boolean isPercentButtonVisible(int percent) {
        return isVisible(Locators.a11y(percent + "%"));
    }

    // ------------------------------------------------------------------ submit (validation only)

    /**
     * Taps MUA/BÁN ONLY when quantity is 0, to verify client-side validation.
     * Throws if quantity &gt; 0 so a real order can never be sent by automation.
     */
    @Step("Bấm MUA/BÁN với khối lượng = 0 (kiểm tra validate)")
    public AlertDialog tapSubmitExpectingValidation() {
        long qty = getQuantity();
        if (qty != 0) { // ALWAYS - independent of order.submit.enabled (real orders only via submitRealOrder)
            throw new IllegalStateException("SAFETY GUARD: refusing to tap submit with quantity=" + qty
                    + ". Validation checks must never place real orders.");
        }
        By submit = currentSide() == Side.BUY ? buyButton : sellButton;
        scrollTo(submit);
        tap(submit);
        return new AlertDialog().waitUntilLoaded();
    }

    /**
     * REAL ORDER: taps MUA/BÁN with the current form. Allowed only when {@code order.submit.enabled=true}
     * (explicit opt-in per run). Returns the confirmation dialog (PIN) - or null when the app rejects the
     * order immediately (error dialog, e.g. wrong session / insufficient buying power).
     */
    @Step("Chạm vào nút MUA/BÁN (lệnh thật)")
    public OrderConfirmDialog submitRealOrder() {
        if (!AppConfig.orderSubmitEnabled()) {
            throw new IllegalStateException("SAFETY GUARD: real orders are disabled. Run with -Dorder.submit.enabled=true");
        }
        By submit = currentSide() == Side.BUY ? buyButton : sellButton;
        scrollTo(submit);
        tap(submit);
        OrderConfirmDialog confirm = new OrderConfirmDialog();
        AlertDialog error = new AlertDialog();
        WaitUtils.condition(() -> confirm.isDisplayed(Duration.ofMillis(300)) || error.isShown(Duration.ofMillis(300)),
                Duration.ofSeconds(15));
        // Seen on UAT: the "Xác nhận đặt lệnh" sheet opens and an error dialog (e.g. E_SYSTEM_ERROR) pops up ON TOP
        // of it. The modal hides the sheet's semantics, so close the error first, then look for the sheet again.
        if (error.isShown(Duration.ofMillis(500))) {
            String msg = error.getMessage();
            vn.bsc.automation.core.utils.AllureUtils.attachScreenshot("Lỗi khi mở xác nhận lệnh: " + msg);
            vn.bsc.automation.core.utils.AllureUtils.attachText("Lỗi hiển thị trên màn xác nhận lệnh", msg);
            log.warn("Error dialog over the order confirmation: {}", msg);
            lastPreConfirmError = msg;
            if (error.hasButton(AlertDialog.BTN_CLOSE)) {
                error.close();
            }
        } else {
            lastPreConfirmError = null;
        }
        return confirm.isDisplayed(Duration.ofSeconds(3)) ? confirm : null;
    }

    private String lastPreConfirmError;

    /** Error that popped up over the confirmation sheet during the last {@link #submitRealOrder()} (null = none). */
    public String lastPreConfirmError() {
        return lastPreConfirmError;
    }

    public boolean isSubmitButtonVisible() {
        return isVisible(buyButton, Duration.ofSeconds(2)) || isVisible(sellButton, Duration.ofSeconds(2));
    }
}
