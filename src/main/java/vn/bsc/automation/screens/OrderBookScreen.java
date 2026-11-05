package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.data.OrderRegistry.PlacedOrder;
import vn.bsc.automation.screens.components.OrderConfirmDialog;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * "Sổ lệnh": summary, status filters, order rows, Sửa / Huỷ / Huỷ tất cả.
 * <p>
 * Order rows could not be inspected yet (no order existed while exploring; the back-end was in batch).
 * Row matching is therefore text-based (symbol + quantity in the row's content-desc) and every action
 * attaches screenshot + page source so locators can be finalised from the first real run.
 * Destructive actions are guarded: they only ever target orders registered by this test run.
 */
public class OrderBookScreen extends BaseScreen {

    public static final String[] FILTERS = {"Tất cả", "Chờ", "Khớp", "Huỷ", "Điều kiện"};
    public static final String[] SUMMARY_LABELS = {"Tổng mua", "Tổng bán", "Giá trị ròng (Mua - Bán)"};

    private static final Pattern SYMBOL_LINE = Pattern.compile("^[A-Z0-9]{3,10}$");
    private final By summary = Locators.descStartsWith("Tổng mua");
    private final By cancelAllButton = Locators.classWithDesc("android.widget.Button", "Huỷ tất cả");

    @Override
    protected By anchor() {
        return summary;
    }

    public String getSummaryText() {
        return getDesc(summary);
    }

    public boolean isFilterVisible(String filter) {
        return isVisible(Locators.a11y(filter));
    }

    @Step("Lọc sổ lệnh: {filter}")
    public void selectFilter(String filter) {
        tap(Locators.a11y(filter));
    }

    public boolean isCancelAllButtonVisible() {
        return isVisible(cancelAllButton);
    }

    // ------------------------------------------------------------------ rows

    private static String qtyText(long qty) {
        return NumberFormat.getIntegerInstance(Locale.US).format(qty); // 1,000
    }

    /** Rows = clickable nodes whose content-desc starts with a symbol line. */
    public List<UiNode> orderRows() {
        return UiNode.snapshot(driver().getPageSource()).stream()
                .filter(n -> n.clickable() && SYMBOL_LINE.matcher(n.desc().split("\n")[0].trim()).matches()
                        && n.desc().contains("\n"))
                .collect(Collectors.toList());
    }

    public boolean rowMatches(UiNode row, PlacedOrder o) {
        String d = row.desc();
        return d.startsWith(o.source().symbol()) && d.contains(qtyText(o.quantity()));
    }

    public UiNode findRow(PlacedOrder o) {
        return orderRows().stream().filter(r -> rowMatches(r, o)).findFirst()
                .orElseThrow(() -> new AssertionError("Order not found in Sổ lệnh: " + o.source()));
    }

    @Step("Chọn lệnh trong sổ lệnh")
    public void openRow(PlacedOrder o) {
        UiNode r = findRow(o);
        GestureUtils.tap(r.x() + r.width() / 2, r.y() + r.height() / 2);
        AllureUtils.attachScreenshot("Chi tiết lệnh " + o.source().id());
        AllureUtils.attachPageSource("Chi tiết lệnh " + o.source().id() + " (page source)");
    }

    // ------------------------------------------------------------------ actions (Excel No.6 / 7 / 8)

    /** Excel No.6: Sửa -> price "+" x steps -> "Sửa ..." -> Xác nhận (+PIN). */
    @Step("Sửa lệnh: giá {priceSteps} bước")
    public void modify(PlacedOrder o, int priceSteps, OrderFormScreen form) {
        openRow(o);
        tap(Locators.classWithDesc("android.widget.Button", "Sửa"));
        for (int i = 0; i < Math.abs(priceSteps); i++) {
            if (priceSteps > 0) {
                form.increasePrice();
            } else {
                form.decreasePrice();
            }
        }
        tap(Locators.descRegex(Locators.anyForm("Sửa ") + ".+")); // "Sửa mua" / "Sửa bán" (Excel: "Sửa bán")
        new OrderConfirmDialog().waitUntilLoaded();
        new OrderConfirmDialog().enterPinManuallyAndConfirm("Sửa lệnh " + o.source().id());
    }

    /** Excel No.7: Huỷ -> Xác nhận (+PIN). */
    @Step("Huỷ lệnh")
    public void cancel(PlacedOrder o) {
        openRow(o);
        tap(Locators.classWithDesc("android.widget.Button", "Huỷ")); // Button, not the "Huỷ" status filter
        new OrderConfirmDialog().waitUntilLoaded();
        new OrderConfirmDialog().enterPinManuallyAndConfirm("Huỷ lệnh " + o.source().id());
    }

    /**
     * Excel No.8: Huỷ tất cả -> "Huỷ ... mã" -> Xác nhận (+PIN).
     * SAFETY: refuses (AssertionError, nothing cancelled) if any pending row is not an order of this run.
     */
    @Step("Huỷ tất cả (chỉ lệnh do test tạo)")
    public void cancelAllOnlyTestOrders(List<PlacedOrder> testOrders) {
        selectFilter("Chờ");
        waitForRows(Duration.ofSeconds(5));
        List<UiNode> pendingRows = orderRows();
        AllureUtils.attachPageSource("Sổ lệnh - lệnh chờ trước khi Huỷ tất cả");
        List<String> foreign = pendingRows.stream()
                .filter(r -> testOrders.stream().noneMatch(o -> rowMatches(r, o)))
                .map(r -> r.desc().replace('\n', ' '))
                .collect(Collectors.toList());
        if (!foreign.isEmpty()) {
            throw new AssertionError("DỪNG AN TOÀN: có lệnh chờ không do test tạo, không bấm Huỷ tất cả: " + foreign);
        }
        if (pendingRows.isEmpty()) {
            throw new AssertionError("Không có lệnh chờ để Huỷ tất cả");
        }
        tap(cancelAllButton);
        tap(Locators.descRegex(Locators.anyForm("Huỷ ") + "[0-9]+" + Locators.anyForm(" mã"))); // "Huỷ <n> mã"
        new OrderConfirmDialog().waitUntilLoaded();
        new OrderConfirmDialog().enterPinManuallyAndConfirm("Huỷ tất cả");
    }

    private void waitForRows(Duration d) {
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> !orderRows().isEmpty(), d);
    }
}
