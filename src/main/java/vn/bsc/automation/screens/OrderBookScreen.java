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
    /** Conditional orders (Stop Limit / Trailing Stop / OCO) right after placing: waiting for their trigger. */
    public static final Pattern STATUS_CONDITIONAL_PLACED = status("order.status.conditional.placed", "Chờ kích hoạt");

    /** Status an accepted order must show: "Chờ kích hoạt" (conditional) or "Đã gửi"/"Chờ gửi" (normal). */
    public static Pattern placedStatus(OrderSpec o) {
        return o.conditional() ? STATUS_CONDITIONAL_PLACED : STATUS_PLACED;
    }
    public static final Pattern STATUS_MODIFIED = status("order.status.modified", "Đã sửa");
    public static final Pattern STATUS_CANCELLED = status("order.status.cancelled", "Đã huỷ|Đã hủy");

    private static Pattern status(String key, String def) {
        String alt = Locators.nfc(vn.bsc.automation.core.config.ConfigReader.get(key, def));
        return Pattern.compile("(?:" + alt + ")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** What identifies an order in the book. {@code price} null = not checked (market / trailing). */
    public record OrderSpec(String symbol, boolean buy, long quantity, java.math.BigDecimal price, String priceLabel,
                            boolean conditional) {

        /**
         * Normal orders are matched on their price (or market price label); conditional orders are listed with
         * their TYPE instead of a price ("AAA | MUA | Stop Limit | 100 | ● Chờ kích hoạt").
         */
        public static OrderSpec of(PlacedOrder o) {
            boolean buy = o.source().side() == vn.bsc.automation.data.OrderCase.Side.BUY;
            if (o.source().inConditionalBook()) {
                return new OrderSpec(o.source().symbol(), buy, o.quantity(), null, conditionalLabel(o.source().orderType()), true);
            }
            return new OrderSpec(o.source().symbol(), buy, o.quantity(), o.price(),
                    o.source().isMarketType() ? o.source().orderType() : null, false);
        }

        public static String conditionalLabel(String type) {
            return switch (type) {
                case "STOP_LIMIT" -> "Stop Limit";
                case "TRAILING_STOP" -> "Trailing Stop";
                case "OCO" -> "OCO";
                case "LO" -> "Limit"; // LO-GTC row in the "Điều kiện" tab: "AAA | MUA | Limit | 100 | ● Chờ kích hoạt"
                default -> type;
            };
        }

        public OrderSpec withPriceAndQty(java.math.BigDecimal p, long q) {
            return new OrderSpec(symbol, buy, q, p, priceLabel, conditional);
        }

        @Override
        public String toString() {
            return (buy ? "MUA " : "BÁN ") + symbol + " x" + quantity + (price != null ? " @" + price.toPlainString() : "")
                    + (priceLabel != null ? " " + priceLabel : "");
        }
    }

    /** Lines of the row (NFC, zero-width chars removed by UiNode/Locators.nfc). */
    private static List<String> lines(UiNode row) {
        return java.util.Arrays.stream(row.desc().split("\n")).map(String::trim).collect(Collectors.toList());
    }

    /** Row matches symbol, side (M/B), quantity and - when given - price or market price label. */
    public static boolean rowMatches(UiNode row, OrderSpec o) {
        List<String> l = lines(row);
        if (l.isEmpty() || !l.get(0).equals(o.symbol())) {
            return false;
        }
        boolean side = o.buy() ? l.stream().anyMatch(x -> x.equals("M") || x.equalsIgnoreCase("Mua"))
                : l.stream().anyMatch(x -> x.equals("B") || x.equalsIgnoreCase("Bán"));
        boolean qty = l.contains(qtyText(o.quantity())) || l.contains(String.valueOf(o.quantity()));
        boolean price = true;
        if (o.price() != null || o.priceLabel() != null) {
            // ATO/ATC rows show either the type label or the price (production: "AAA | MUA | 7.46 | 100")
            price = (o.priceLabel() != null && l.contains(o.priceLabel()))
                    || (o.price() != null && priceTexts(o.price()).stream().anyMatch(l::contains));
        }
        return side && qty && price;
    }

    /** Price as it may be displayed: 7.53 / 7.5 / 7,530 (x1000) / 1,867.40. */
    static List<String> priceTexts(java.math.BigDecimal p) {
        java.text.DecimalFormat grouped2 = new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(Locale.US));
        List<String> out = new java.util.ArrayList<>(List.of(p.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                p.stripTrailingZeros().toPlainString(),
                grouped2.format(p),
                NumberFormat.getIntegerInstance(Locale.US).format(p.multiply(java.math.BigDecimal.valueOf(1000)))));
        if (p.compareTo(java.math.BigDecimal.valueOf(1000)) >= 0) {
            // price taken from a message in VND ("giá 7,460.00") while the book shows x1000 ("7.46")
            java.math.BigDecimal k = p.divide(java.math.BigDecimal.valueOf(1000));
            out.add(k.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            out.add(k.stripTrailingZeros().toPlainString());
        }
        return out;
    }

    /** Status line looks like "● Chờ kích hoạt" (dot prefix): match by find(), not on the whole line. */
    public static boolean hasStatus(UiNode row, Pattern status) {
        return lines(row).stream().anyMatch(x -> status.matcher(x).find());
    }

    /** Text of the row (for reports). */
    public static String text(UiNode row) {
        return row.desc().replace("\n", " | ");
    }

    public java.util.Optional<UiNode> find(OrderSpec o, Pattern status) {
        return orderRows().stream().filter(r -> rowMatches(r, o) && (status == null || hasStatus(r, status))).findFirst();
    }

    /**
     * Waits (refreshing the list via its filter) until a row of {@code o} with {@code status} appears.
     * Conditional orders are listed under the "Điều kiện" filter.
     */
    @Step("Sổ lệnh: chờ lệnh {o} với trạng thái {status}")
    public java.util.Optional<UiNode> waitFor(OrderSpec o, Pattern status, Duration timeout) {
        String filter = o.conditional() ? "Điều kiện" : "Tất cả";
        java.util.Optional<UiNode>[] hit = new java.util.Optional[]{java.util.Optional.empty()};
        int[] round = {0};
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> {
            if (round[0]++ % 4 == 0) {
                selectFilter(filter); // reload the list now and then, not on every poll (it would never settle)
                vn.bsc.automation.core.utils.WaitUtils.condition(() -> false, Duration.ofMillis(800));
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
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> !orderRows().isEmpty(), Duration.ofSeconds(4));
        return (int) orderRows().stream().filter(r -> rowMatches(r, o) && hasStatus(r, status)).count();
    }

    /** Legacy match used by the cancel-all safety check (symbol + quantity). */
    public boolean rowMatches(UiNode row, PlacedOrder o) {
        String d = row.desc();
        return d.startsWith(o.source().symbol()) && d.contains(qtyText(o.quantity()));
    }

    /** The ACTIVE row of the order (never an "Đã sửa"/"Đã huỷ" history row of the same order). */
    public UiNode findRow(PlacedOrder o) {
        OrderSpec spec = OrderSpec.of(o);
        // "Chờ" lists only live orders: in "Tất cả" identical cancelled rows of earlier runs hide the active one
        selectFilter(o.source().inConditionalBook() ? "Điều kiện" : "Chờ");
        // the list reloads after the filter tap: wait until the order's rows are there
        vn.bsc.automation.core.utils.WaitUtils.condition(
                () -> orderRows().stream().anyMatch(r -> rowMatches(r, spec)), Duration.ofSeconds(8));
        List<UiNode> rows = orderRows();
        return rows.stream().filter(r -> rowMatches(r, spec) && hasStatus(r, placedStatus(spec))).findFirst()
                .or(() -> rows.stream().filter(r -> rowMatches(r, spec)
                        && !hasStatus(r, STATUS_MODIFIED) && !hasStatus(r, STATUS_CANCELLED)).findFirst())
                .or(() -> rows.stream().filter(r -> rowMatches(r, o)
                        && !hasStatus(r, STATUS_MODIFIED) && !hasStatus(r, STATUS_CANCELLED)).findFirst())
                .orElseThrow(() -> new AssertionError("Order not found in Sổ lệnh: " + spec));
    }

    private static final Pattern PLACED_TIME = Pattern.compile("Giờ đặt\\n(\\d{2}:\\d{2}:\\d{2})");

    /** "Giờ đặt" of an EXPANDED row (tapping a row expands its details inline: TK, Giá, Giờ đặt, Kết thúc, Xem, Huỷ). */
    public static java.util.Optional<java.time.LocalTime> placedTime(UiNode row) {
        var m = PLACED_TIME.matcher(row.desc());
        return m.find() ? java.util.Optional.of(java.time.LocalTime.parse(m.group(1))) : java.util.Optional.empty();
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
        GestureUtils.swipe(new org.openqa.selenium.Point(x, from), new org.openqa.selenium.Point(x, Math.max(from - dist, (int) (h * 0.35))),
                Duration.ofMillis(900));
        int expectedY = row.y() - dist;
        String head = row.desc().split("\n")[0];
        return orderRows().stream().filter(r -> r.desc().startsWith(head) && r.desc().equals(row.desc()))
                .min(java.util.Comparator.comparingInt(r -> Math.abs(r.y() - expectedY))).orElse(row);
    }

    /** Taps a row and returns its expanded node (same first lines + details), or the row itself. */
    private UiNode expand(UiNode original) {
        final UiNode row = bringToUpperArea(original);
        GestureUtils.tap(row.x() + row.width() / 2, row.y() + Math.min(row.height(), 170) / 2);
        String head = row.desc().split("\n")[0];
        java.util.Optional<UiNode>[] got = new java.util.Optional[]{java.util.Optional.empty()};
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> {
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
            java.time.LocalTime t = o.placedAt().toLocalTime();
            for (int i = 0; i < candidates.size() && chosen == null; i++) {
                UiNode c = orderRows().stream().filter(r -> rowMatches(r, spec)
                        && !hasStatus(r, STATUS_MODIFIED) && !hasStatus(r, STATUS_CANCELLED)).skip(i).findFirst().orElse(null);
                if (c == null) {
                    break;
                }
                UiNode ex = expand(c);
                if (placedTime(ex).map(pt -> Math.abs(java.time.Duration.between(pt, t).getSeconds()) <= 90).orElse(false)) {
                    chosen = ex;
                }
            }
            if (chosen == null) {
                throw new AssertionError("Không xác định được lệnh của test theo Giờ đặt (" + t + "): " + spec);
            }
        } else {
            chosen = expand(first);
        }
        AllureUtils.attachScreenshot("Chi tiết lệnh " + o.source().id());
        AllureUtils.attachText("Dòng lệnh được chọn", text(chosen));
    }

    /**
     * Cleanup helper: cancels every active conditional row of {@code spec} placed at/after {@code from} today
     * (rows placed earlier are left untouched). Returns the number of cancel requests sent.
     */
    public int cancelConditionalPlacedFrom(OrderSpec spec, java.time.LocalTime from, int max) {
        int done = 0;
        for (int guard = 0; guard < max; guard++) {
            selectFilter("Điều kiện");
            vn.bsc.automation.core.utils.WaitUtils.condition(() -> !orderRows().isEmpty(), Duration.ofSeconds(6));
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
            vn.bsc.automation.screens.components.AlertDialog d = new vn.bsc.automation.screens.components.AlertDialog();
            if (d.isShown(Duration.ofSeconds(10))) {
                AllureUtils.attachText("Kết quả huỷ", d.getMessage());
                if (d.hasButton(vn.bsc.automation.screens.components.AlertDialog.BTN_CLOSE)) {
                    d.close();
                }
            }
            done++;
        }
        return done;
    }

    // ------------------------------------------------------------------ actions (Excel No.6 / 7 / 8)

    /** Price / quantity shown on the modify form right before "Sửa ..." (null price = no price stepper). */
    public record Modified(java.math.BigDecimal price, long quantity) {
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
        java.math.BigDecimal newPrice = o.price();
        long qty = newQuantity > 0 ? newQuantity : o.quantity();
        try {
            newPrice = form.getPrice();
            qty = form.getQuantity();
        } catch (AssertionError e) {
            log.info("Modify form: price/quantity not readable ({}), using expected values", e.getMessage());
        }
        // "Sửa mua" / "Sửa bán" (UAT) or "SỬA MUA : 1,396,000" (production 2.0.20)
        tap(Locators.descRegex("(?:" + Locators.anyForm("Sửa ") + "|" + Locators.anyForm("SỬA ") + ").+"));
        new OrderConfirmDialog().waitUntilLoaded();
        new OrderConfirmDialog().enterPinAndConfirm("Sửa lệnh " + o.source().id());
        return new Modified(newPrice, qty);
    }

    /** Excel No.7: Huỷ -> Xác nhận (+PIN). */
    @Step("Huỷ lệnh")
    public void cancel(PlacedOrder o) {
        openRow(o);
        tapRowButton("Huỷ"); // Button of the expanded row, not the "Huỷ" status filter
        new OrderConfirmDialog().waitUntilLoaded();
        new OrderConfirmDialog().enterPinAndConfirm("Huỷ lệnh " + o.source().id());
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
        new OrderConfirmDialog().enterPinAndConfirm("Huỷ tất cả");
    }

    private void waitForRows(Duration d) {
        vn.bsc.automation.core.utils.WaitUtils.condition(() -> !orderRows().isEmpty(), d);
    }
}
