package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.data.OrderRegistry.PlacedOrder;
import vn.bsc.automation.data.OrderSpec;
import vn.bsc.automation.screens.components.OrderConfirmDialog;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import org.openqa.selenium.Point;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.core.config.Platform;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.screens.components.AlertDialog;

import static vn.bsc.automation.data.OrderSpec.hasStatus;
import static vn.bsc.automation.data.OrderSpec.rowMatches;
import static vn.bsc.automation.data.OrderSpec.text;

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

    /**
     * Rows = nodes whose content-desc starts with a symbol line ("AAA\nMUA\n..."). Identified by content, NOT by
     * clickable: while the list reloads (after a filter tap) every row is briefly reported clickable=false.
     */
    public List<UiNode> orderRows() {
        return UiNode.snapshot(driver().getPageSource()).stream()
                .filter(n -> n.desc().contains("\n") && SYMBOL_LINE.matcher(n.desc().split("\n")[0].trim()).matches())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ statuses (config, see config.properties)

    public static final Pattern STATUS_PLACED = status("order.status.placed", "Đã gửi|Chờ gửi");
    /** Normal order placed IN the matching session (business rule 14/11/2026). */
    public static final Pattern STATUS_SENT = status("order.status.sent", "Đã gửi");
    /** Normal order accepted OUTSIDE the session, queued for the next one. */
    public static final Pattern STATUS_QUEUED = status("order.status.queued", "Chờ gửi");
    /** Conditional orders (Stop Limit / Trailing Stop / OCO) right after placing: waiting for their trigger. */
    public static final Pattern STATUS_CONDITIONAL_PLACED = status("order.status.conditional.placed", "Chờ kích hoạt");

    /** Status an accepted order must show: "Chờ kích hoạt" (conditional) or "Đã gửi"/"Chờ gửi" (normal). */
    public static Pattern placedStatus(OrderSpec o) {
        return o.conditional() ? STATUS_CONDITIONAL_PLACED : STATUS_PLACED;
    }
    public static final Pattern STATUS_MODIFIED = status("order.status.modified", "Đã sửa");
    public static final Pattern STATUS_CANCELLED = status("order.status.cancelled", "Đã huỷ|Đã hủy");

    private static Pattern status(String key, String def) {
        String alt = Locators.nfc(ConfigReader.get(key, def));
        return Pattern.compile("(?:" + alt + ")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public Optional<UiNode> find(OrderSpec o, Pattern status) {
        return orderRows().stream().filter(r -> rowMatches(r, o) && (status == null || hasStatus(r, status))).findFirst();
    }

    /**
     * Waits (refreshing the list via its filter) until a row of {@code o} with {@code status} appears.
     * Conditional orders are listed under the "Điều kiện" filter.
     */
    @Step("Sổ lệnh: chờ lệnh {o} với trạng thái {status}")
    public Optional<UiNode> waitFor(OrderSpec o, Pattern status, Duration timeout) {
        String filter = o.conditional() ? "Điều kiện" : "Tất cả";
        Optional<UiNode>[] hit = new Optional[]{Optional.empty()};
        int[] round = {0};
        WaitUtils.condition(() -> {
            if (round[0]++ % 4 == 0) {
                selectFilter(filter); // reload the list now and then, not on every poll (it would never settle)
                WaitUtils.pause(Duration.ofMillis(800));
            }
            hit[0] = find(o, status);
            return hit[0].isPresent();
        }, timeout);
        AllureUtils.attachScreenshot("Sổ lệnh - " + o);
        if (hit[0].isEmpty()) {
            AllureUtils.attachPageSource("Sổ lệnh - không thấy " + o + " (page source)");
        }
        return hit[0];
    }

    /** Number of rows of {@code o} with {@code status} (list reloaded via its filter). */
    public int count(OrderSpec o, Pattern status) {
        selectFilter(o.conditional() ? "Điều kiện" : "Tất cả");
        WaitUtils.condition(() -> !orderRows().isEmpty(), Duration.ofSeconds(4));
        return (int) orderRows().stream().filter(r -> rowMatches(r, o) && hasStatus(r, status)).count();
    }


    /** The ACTIVE row of the order (never an "Đã sửa"/"Đã huỷ" history row of the same order). */
    public UiNode findRow(PlacedOrder o) {
        OrderSpec spec = OrderSpec.of(o);
        // "Chờ" lists only live orders: in "Tất cả" identical cancelled rows of earlier runs hide the active one
        selectFilter(o.source().inConditionalBook() ? "Điều kiện" : "Chờ");
        // the list reloads after the filter tap: wait until the order's rows are there
        WaitUtils.condition(
                () -> orderRows().stream().anyMatch(r -> rowMatches(r, spec)), Duration.ofSeconds(8));
        List<UiNode> rows = orderRows();
        return rows.stream().filter(r -> rowMatches(r, spec) && hasStatus(r, placedStatus(spec))).findFirst()
                .or(() -> rows.stream().filter(r -> rowMatches(r, spec)
                        && !hasStatus(r, STATUS_MODIFIED) && !hasStatus(r, STATUS_CANCELLED)).findFirst())
                .orElseThrow(() -> new AssertionError("Order not found in Sổ lệnh: " + spec));
    }

    private static final Pattern PLACED_TIME = Pattern.compile("Giờ đặt\\n(\\d{2}:\\d{2}:\\d{2})");

    /** "Giờ đặt" of an EXPANDED row (tapping a row expands its details inline: TK, Giá, Giờ đặt, Kết thúc, Xem, Huỷ). */
    public static Optional<LocalTime> placedTime(UiNode row) {
        var m = PLACED_TIME.matcher(row.desc());
        return m.find() ? Optional.of(LocalTime.parse(m.group(1))) : Optional.empty();
    }

    /** Buttons of an expanded row ("Xem", "Huỷ", "Sửa") may sit below the fold for rows near the bottom. */
    private void tapRowButton(String label) {
        By b = Locators.classWithDesc("android.widget.Button", label);
        scrollTo(b);
        tap(b);
    }

    /**
     * Rows near the bottom expand below the fold and the list collapses when scrolled afterwards: move the row
     * into the upper area FIRST (swipe on the left "Mã CK" column), then expand it - its buttons are then visible.
     */
    private UiNode bringToUpperArea(UiNode row) {
        int h = driver().manage().window().getSize().height;
        if (row.y() < h * 0.45) {
            return row;
        }
        int dist = (int) (row.y() - h * 0.30);
        int x = Math.max(30, row.x() + 40), from = (int) (h * 0.82);
        GestureUtils.swipe(new Point(x, from), new Point(x, Math.max(from - dist, (int) (h * 0.35))),
                Duration.ofMillis(900));
        int expectedY = row.y() - dist;
        String head = row.desc().split("\n")[0];
        return orderRows().stream().filter(r -> r.desc().startsWith(head) && r.desc().equals(row.desc()))
                .min(Comparator.comparingInt(r -> Math.abs(r.y() - expectedY))).orElse(row);
    }

    /** Taps a row and returns its expanded node (same first lines + details), or the row itself. */
    private UiNode expand(UiNode original) {
        final UiNode row = bringToUpperArea(original);
        GestureUtils.tap(row.x() + row.width() / 2, row.y() + Math.min(row.height(), 170) / 2);
        String head = row.desc().split("\n")[0];
        Optional<UiNode>[] got = new Optional[]{Optional.empty()};
        WaitUtils.condition(() -> {
            got[0] = orderRows().stream().filter(r -> r.desc().startsWith(head) && placedTime(r).isPresent()
                    && Math.abs(r.y() - row.y()) < 40).findFirst();
            return got[0].isPresent();
        }, Duration.ofSeconds(3));
        return got[0].orElse(row);
    }

    /**
     * Opens (expands) the ACTIVE row of the order. Several identical rows (same symbol/side/qty/type) are told
     * apart by "Giờ đặt" vs the time the test placed the order (±90 s).
     */
    @Step("Chọn lệnh trong sổ lệnh")
    public void openRow(PlacedOrder o) {
        UiNode first = findRow(o);
        OrderSpec spec = OrderSpec.of(o);
        List<UiNode> candidates = orderRows().stream().filter(r -> rowMatches(r, spec)
                && !hasStatus(r, STATUS_MODIFIED) && !hasStatus(r, STATUS_CANCELLED)).collect(Collectors.toList());
        UiNode chosen = null;
        if (candidates.size() > 1 && o.placedAt() != null) {
            LocalTime t = o.placedAt().toLocalTime();
            for (int i = 0; i < candidates.size() && chosen == null; i++) {
                UiNode c = orderRows().stream().filter(r -> rowMatches(r, spec)
                        && !hasStatus(r, STATUS_MODIFIED) && !hasStatus(r, STATUS_CANCELLED)).skip(i).findFirst().orElse(null);
                if (c == null) {
                    break;
                }
                UiNode ex = expand(c);
                if (placedTime(ex).map(pt -> Math.abs(Duration.between(pt, t).getSeconds()) <= 90).orElse(false)) {
                    chosen = ex;
                }
            }
            if (chosen == null) {
                throw new AssertionError("Không xác định được lệnh của test theo Giờ đặt (" + t + "): " + spec);
            }
        } else {
            chosen = expand(first);
            if (o.placedAt() != null) {
                LocalTime t = o.placedAt().toLocalTime();
                Optional<LocalTime> pt = placedTime(chosen);
                if (pt.isPresent() && Math.abs(Duration.between(pt.get(), t).getSeconds()) > 90) {
                    throw new AssertionError("DỪNG AN TOÀN: dòng " + text(chosen) + " có Giờ đặt " + pt.get()
                            + " khác giờ test đặt lệnh " + t + " - không phải lệnh của test");
                }
                if (pt.isEmpty()) {
                    log.warn("Không đọc được Giờ đặt của dòng duy nhất khớp {} - tiếp tục theo mã/chiều/KL/giá", spec);
                }
            }
        }
        AllureUtils.attachScreenshot("Chi tiết lệnh " + o.source().id());
        AllureUtils.attachText("Dòng lệnh được chọn", text(chosen));
    }

    /**
     * Cleanup helper: cancels every active conditional row of {@code spec} placed at/after {@code from} today
     * (rows placed earlier are left untouched). Returns the number of cancel requests sent.
     */
    public int cancelConditionalPlacedFrom(OrderSpec spec, LocalTime from, int max) {
        int done = 0;
        for (int guard = 0; guard < max; guard++) {
            selectFilter("Điều kiện");
            WaitUtils.condition(() -> !orderRows().isEmpty(), Duration.ofSeconds(6));
            UiNode target = null;
            List<UiNode> rows = orderRows().stream().filter(r -> rowMatches(r, spec) && hasStatus(r, STATUS_CONDITIONAL_PLACED))
                    .collect(Collectors.toList());
            for (int i = 0; i < rows.size() && target == null; i++) {
                UiNode c = orderRows().stream().filter(r -> rowMatches(r, spec) && hasStatus(r, STATUS_CONDITIONAL_PLACED))
                        .skip(i).findFirst().orElse(null);
                if (c == null) {
                    break;
                }
                UiNode ex = expand(c);
                if (placedTime(ex).map(pt -> !pt.isBefore(from)).orElse(false)) {
                    target = ex;
                }
            }
            if (target == null) {
                break;
            }
            log.info("Cleanup: huỷ {}", text(target));
            AllureUtils.attachText("Huỷ lệnh thừa", text(target));
            tapRowButton("Huỷ");
            OrderConfirmDialog confirm = new OrderConfirmDialog().waitUntilLoaded();
            confirm.enterPinAndConfirm("Dọn lệnh thừa");
            AlertDialog d = new AlertDialog();
            if (d.isShown(Duration.ofSeconds(10))) {
                AllureUtils.attachText("Kết quả huỷ", d.getMessage());
                if (d.hasButton(AlertDialog.BTN_CLOSE)) {
                    d.close();
                }
            }
            done++;
        }
        return done;
    }

    // ------------------------------------------------------------------ actions (Excel No.6 / 7 / 8)

    /** Price / quantity shown on the modify form right before "Sửa ..." (null price = no price stepper). */
    public record Modified(BigDecimal price, long quantity) {
    }

    /**
     * Excel "Sửa lệnh": Sửa -&gt; [price "+" x steps] [Khối lượng = newQuantity] -&gt; "Sửa ..." -&gt; Xác nhận (+PIN).
     * {@code newQuantity} 0 = keep. The modify form reuses the order-form steppers (not yet verified on a real
     * order: no order could be placed while surveying).
     */
    @Step("Sửa lệnh: giá {priceSteps} bước, KL mới {newQuantity}")
    public Modified modify(PlacedOrder o, int priceSteps, long newQuantity, OrderFormScreen form) {
        openRow(o);
        tapRowButton("Sửa");
        AllureUtils.attachScreenshot("Form sửa lệnh " + o.source().id());
        AllureUtils.attachPageSource("Form sửa lệnh " + o.source().id() + " (page source)");
        if (newQuantity > 0) {
            form.enterQuantity(newQuantity);
        }
        for (int i = 0; i < Math.abs(priceSteps); i++) {
            if (priceSteps > 0) {
                form.increasePrice();
            } else {
                form.decreasePrice();
            }
        }
        BigDecimal newPrice = o.price();
        long qty = newQuantity > 0 ? newQuantity : o.quantity();
        try {
            newPrice = form.getPrice();
            qty = form.getQuantity();
        } catch (AssertionError e) {
            log.info("Modify form: price/quantity not readable ({}), using expected values", e.getMessage());
        }
        // "Sửa mua" / "Sửa bán" (UAT) or "SỬA MUA : 1,396,000" (production 2.0.20)
        tap(Locators.descRegex("(?:" + Locators.anyForm("Sửa ") + "|" + Locators.anyForm("SỬA ") + ").+"));
        OrderConfirmDialog confirm = new OrderConfirmDialog().waitUntilLoaded();
        confirm.verifyShows(o.source().symbol(), qty, "Sửa lệnh " + o.source().id());
        confirm.enterPinAndConfirm("Sửa lệnh " + o.source().id());
        return new Modified(newPrice, qty);
    }

    /** Excel No.7: Huỷ -> Xác nhận (+PIN). */
    @Step("Huỷ lệnh")
    public void cancel(PlacedOrder o) {
        openRow(o);
        tapRowButton("Huỷ"); // Button of the expanded row, not the "Huỷ" status filter
        OrderConfirmDialog confirm = new OrderConfirmDialog().waitUntilLoaded();
        confirm.verifyShows(o.source().symbol(), o.quantity(), "Huỷ lệnh " + o.source().id());
        confirm.enterPinAndConfirm("Huỷ lệnh " + o.source().id());
    }

    /**
     * Excel No.8: Huỷ tất cả -> "Huỷ ... mã" -> Xác nhận (+PIN).
     * SAFETY: refuses (AssertionError, nothing cancelled) if any pending row is not an order of this run.
     */
    @Step("Huỷ tất cả (chỉ lệnh do test tạo)")
    public void cancelAllOnlyTestOrders(List<PlacedOrder> testOrders) {
        selectFilter("Chờ");
        waitForRows(Duration.ofSeconds(5));
        List<OrderSpec> specs = testOrders.stream().map(OrderSpec::of).collect(Collectors.toList());
        List<UiNode> pendingRows = allRowsOfList();
        AllureUtils.attachPageSource("Sổ lệnh - lệnh chờ trước khi Huỷ tất cả");
        List<String> foreign = pendingRows.stream()
                .filter(r -> specs.stream().noneMatch(sp -> rowMatches(r, sp))) // symbol + side + quantity + price
                .map(r -> r.desc().replace('\n', ' '))
                .distinct().collect(Collectors.toList());
        if (!foreign.isEmpty()) {
            throw new AssertionError("DỪNG AN TOÀN: có lệnh chờ không do test tạo, không bấm Huỷ tất cả: " + foreign);
        }
        if (pendingRows.isEmpty()) {
            throw new AssertionError("Không có lệnh chờ để Huỷ tất cả");
        }
        tap(cancelAllButton);
        // "Huỷ <n> mã": n (orders or symbols) can never exceed the number of test orders when all pending are ours
        By confirmAll = Locators.descRegex(Locators.anyForm("Huỷ ") + "[0-9]+" + Locators.anyForm(" mã"));
        String label = Locators.nfc(find(confirmAll).getAttribute(Platform.labelAttr()));
        Matcher n = Pattern.compile("([0-9]+)").matcher(label == null ? "" : label);
        long testPending = testOrders.stream().filter(o -> !o.source().inConditionalBook()).count();
        if (n.find() && Long.parseLong(n.group(1)) > testPending) {
            pressBack();
            throw new AssertionError("DỪNG AN TOÀN: app sẽ huỷ " + n.group(1) + " (" + label + ") nhưng test chỉ có "
                    + testPending + " lệnh chờ - không huỷ");
        }
        AllureUtils.attachText("Huỷ tất cả", label + " | lệnh của test: " + testPending);
        tap(confirmAll);
        new OrderConfirmDialog().waitUntilLoaded();
        new OrderConfirmDialog().enterPinAndConfirm("Huỷ tất cả");
    }

    /**
     * Every row of the current list, scrolling only when it runs below the "Huỷ tất cả" bar; stops when a scroll
     * brings nothing new (bottom reached), then returns to the top.
     */
    private List<UiNode> allRowsOfList() {
        List<UiNode> all = new ArrayList<>(orderRows());
        int barY = UiNode.snapshot(driver().getPageSource()).stream()
                .filter(x -> Locators.nfc(x.desc()).matches("Hu[ỷy] tất cả|Hủy tất cả")).mapToInt(UiNode::y).min()
                .orElse(driver().manage().window().getSize().height);
        boolean fits = all.isEmpty() || all.stream().mapToInt(r -> r.y() + r.height()).max().getAsInt() < barY - 40;
        int scrolls = 0;
        if (!fits) {
            List<String> before = all.stream().map(r -> r.desc() + "@" + r.y()).collect(Collectors.toList());
            for (; scrolls < 8; scrolls++) {
                GestureUtils.scrollBy(0.5);
                List<UiNode> now = orderRows();
                List<String> sig = now.stream().map(r -> r.desc() + "@" + r.y()).collect(Collectors.toList());
                if (sig.equals(before)) {
                    break; // bottom reached
                }
                all.addAll(now);
                before = sig;
            }
            for (int i = 0; i <= scrolls; i++) {
                GestureUtils.scrollUp();
            }
        }
        return all;
    }
    private void waitForRows(Duration d) {
        WaitUtils.condition(() -> !orderRows().isEmpty(), d);
    }
}
