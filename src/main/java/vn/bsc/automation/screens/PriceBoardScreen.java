package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import org.slf4j.LoggerFactory;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.IndexCard;
import vn.bsc.automation.data.StockRow;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import io.qameta.allure.Step;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.screens.components.ContentInspector;

/** "Bảng giá": watch-list selector, index cards, stock rows, news. */
public class PriceBoardScreen extends BaseScreen {

    private final By sortByVolume = Locators.a11y("Khối lượng");
    private final By sortByPrice = Locators.a11y("Giá");
    private static final Pattern STOCK_ROW =
            Pattern.compile("^[A-Z0-9]{3,10}\\s*\\n\\s*\\|\\s*\\n\\s*(HOSE|HNX|UPCOM)\\s*\\n");

    private final By stockRows = Locators.xpath("//android.view.View[contains(@content-desc,'|') and "
            + "(contains(@content-desc,'HOSE') or contains(@content-desc,'HNX') or contains(@content-desc,'UPCOM'))]");

    /** Sort row (A-Z / Giá / Khối lượng) or, for the "Khuyến nghị" watch-list, its sub-tabs (BSC10/30/50). */
    @Override
    protected By anchor() {
        return Locators.descMatchesAny("Khối lượng", "BSC10");
    }

    public boolean isWatchlistSelectorVisible(String name) {
        return isVisible(Locators.a11y(name));
    }

    public IndexCard getIndexCard(String indexName) {
        return IndexCard.parse(getDesc(Locators.descStartsWith(indexName)));
    }

    public List<StockRow> getStockRows() {
        WaitUtils.allPresent(stockRows, Duration.ofSeconds(15));
        return findAll(stockRows).stream()
                .map(this::getDesc)
                .map(Locators::nfc)
                // strict row shape "AAA\n|\nHOSE\n...": news rows ("TNG\nHNX | 23/11/2026") and list containers
                // also contain "|" and an exchange name
                .filter(d -> STOCK_ROW.matcher(d).find())
                .map(StockRow::parse)
                .collect(Collectors.toList());
    }

    public boolean isSortOptionVisible(String label) {
        return isVisible(Locators.a11y(label));
    }

    public void sortByPrice() {
        tap(sortByPrice);
    }

    public void sortByVolume() {
        tap(sortByVolume);
    }

    public boolean isNewsSectionVisible() {
        // news is below the stock list: scroll down to it
        return isVisible(Locators.descStartsWith("Tin tức"), Duration.ofSeconds(3)) || isVisibleWithScroll(Locators.descStartsWith("Tin tức"));
    }

    // ---------------------------------------------------------------- selectors (watch-list / index / industry)

    /** Selector chips on the top row, left to right: [watch-list, index, industry] (labels change with the choice). */
    private List<UiNode> selectorChips() {
        List<UiNode> views = new ContentInspector().content()
                .stream().filter(n -> n.clickable() && "android.view.View".equals(n.className()) && !n.desc().contains("\n"))
                .collect(Collectors.toList());
        int top = views.stream().mapToInt(UiNode::y).min()
                .orElseThrow(() -> new AssertionError("Không thấy hàng bộ chọn của Bảng giá"));
        return views.stream().filter(n -> Math.abs(n.y() - top) < 10)
                .sorted(Comparator.comparingInt(UiNode::x))
                .collect(Collectors.toList());
    }

    /**
     * The chip row scrolls horizontally (8 chips: Danh mục, Chỉ số, CP ngành, Phái sinh, Chứng quyền, ETFs, Lô lẻ,
     * Trái phiếu) and a chip's label changes with the selection. The row is scrolled to its start, then walked left
     * while collecting labels in order until chip {@code index} is fully on screen, then tapped.
     */
    /**
     * Drags the chip row back to its first chip, only as far as needed: stops as soon as a drag no longer moves the
     * row (already at the start = one probing drag at most). Slow drags: a fast one flings the row.
     */
    private void chipRowToStart(int rowY, int y) {
        Dimension w = driver().manage().window().getSize();
        String before = chipsOnRow(rowY).stream().map(c -> c.desc() + "@" + c.x()).collect(Collectors.joining("|"));
        for (int i = 0; i < 3; i++) {
            dragRow(y, (int) (w.width * 0.25), (int) (w.width * 0.9));
            String now = chipsOnRow(rowY).stream().map(c -> c.desc() + "@" + c.x()).collect(Collectors.joining("|"));
            if (now.equals(before)) {
                return;
            }
            before = now;
        }
    }

    /** Chip tapped last (index + centre): choosing an option relabels the chip but does not move it. */
    private int lastChipIndex = -1, lastChipX, lastChipY;

    private void tapChip(int index) {
        Dimension w = driver().manage().window().getSize();
        UiNode first = selectorChips().get(0);
        int rowY = first.y(), y = first.y() + first.height() / 2;
        // same chip as last time and still at the same place (only its label changed): tap it, no horizontal drag
        if (index == lastChipIndex) {
            Optional<UiNode> same = chipsOnRow(rowY).stream()
                    .filter(c -> Math.abs(c.centerX() - lastChipX) < 30 && Math.abs(c.centerY() - lastChipY) < 30
                            && c.x() >= 0 && c.x() + c.width() < w.width - 20)
                    .findFirst();
            if (same.isPresent()) {
                GestureUtils.tap(same.get());
                return;
            }
        }
        chipRowToStart(rowY, y);
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<UiNode> chips = chipsOnRow(rowY);
            chips.forEach(c -> {
                if (!order.contains(c.desc())) {
                    order.add(c.desc());
                }
            });
            if (order.size() > index) {
                String label = order.get(index);
                Optional<UiNode> c = chips.stream()
                        .filter(x -> x.desc().equals(label)).findFirst();
                if (c.isPresent() && c.get().x() + c.get().width() < w.width - 20 && c.get().width() > 60) {
                    GestureUtils.tap(c.get().x() + c.get().width() / 2, y);
                    lastChipIndex = index;
                    lastChipX = c.get().centerX();
                    lastChipY = y;
                    return;
                }
            }
            dragRow(y, (int) (w.width * 0.75), (int) (w.width * 0.45));
        }
        throw new AssertionError("Bảng giá: không bấm được bộ chọn thứ " + (index + 1) + " (thấy " + order + ")");
    }

    /** Slow horizontal drag on the chip row, then wait until the row reports its chips again (no fling). */
    private void dragRow(int y, int fromX, int toX) {
        GestureUtils.swipe(new Point(fromX, y),
                new Point(toX, y), Duration.ofMillis(900));
        int rowY = y;
        WaitUtils.condition(() -> !chipsOnRow(rowY - 60).isEmpty(), Duration.ofSeconds(2));
    }

    /** Chips whose top edge is within the chip-row band (never the sort row A-Z / Giá / Khối lượng below it). */
    private List<UiNode> chipsOnRow(int rowY) {
        return new ContentInspector().content().stream()
                .filter(n -> n.clickable() && "android.view.View".equals(n.className()) && !n.desc().contains("\n")
                        && Math.abs(n.y() - rowY) < 70)
                .sorted(Comparator.comparingInt(UiNode::x))
                .collect(Collectors.toList());
    }

    /** Taps chip {@code index}; picks {@code option} from its sheet when given (null = chip opens the list directly). */
    @Step("Bảng giá - bộ chọn {index}: {option}")
    public void selectChip(int index, String option) {
        tapChip(index);
        if (option != null && !option.isEmpty()) {
            pickFromSheet(option);
        }
    }

    /** Label currently shown by chip 0 (watch-list), 1 (index) or 2 (industry). */
    public String selectorLabel(int index) {
        UiNode first = selectorChips().get(0);
        chipRowToStart(first.y(), first.centerY()); // a previous test may have left the row scrolled (showed "Chứng quyền" first)
        return selectorChips().get(index).desc();
    }

    @Step("Bảng giá - danh mục: {name}")
    public void selectWatchlist(String name) {
        tapChip(0);
        By option = Locators.a11y(name);
        WaitUtils.visible(Locators.a11y("Chọn danh mục"), Duration.ofSeconds(5));
        tap(option);
        WaitUtils.waitInvisible(Locators.a11y("Chọn danh mục"), Duration.ofSeconds(5));
    }

    @Step("Bảng giá - chỉ số: {name}")
    public void selectIndex(String name) {
        selectChip(1, name);
    }

    @Step("Bảng giá - ngành: {name}")
    public void selectIndustry(String name) {
        selectChip(2, name);
    }

    @Step("Bảng giá - tab con: {name}")
    public void selectSubTab(String name) {
        if (!new ContentInspector().tapVisible(name)) {
            throw new AssertionError("Không thấy tab con " + name);
        }
    }

    /** Option sheets list Buttons above a "Đóng" button; scroll inside the sheet until the option is visible. */
    private void pickFromSheet(String name) {
        By close = Locators.classWithDesc("android.widget.Button", "Đóng");
        WaitUtils.visible(close, Duration.ofSeconds(5));
        By option = Locators.classWithDesc("android.widget.Button", name);
        String wanted = Locators.nfc(name);
        Set<String> seen = new LinkedHashSet<>(); // reported when the option is missing
        // down first, then up; each direction stops as soon as the list no longer moves (end of the list reached)
        for (int dir : new int[]{1, -1}) {
            String previous = null;
            for (int i = 0; i < 8; i++) {
                List<UiNode> buttons = UiNode
                        .snapshot(driver().getPageSource()).stream()
                        .filter(n -> n.className().endsWith("Button") && n.height() > 20 && !"Đóng".equals(Locators.nfc(n.desc())))
                        .collect(Collectors.toList());
                // a row cut by the sheet edge is shorter than the others: tap only fully visible rows
                // (relative to the row height - Android reports px, iOS points)
                int rowHeight = buttons.stream().mapToInt(UiNode::height).max().orElse(0);
                buttons.forEach(n -> seen.add(Locators.nfc(n.desc()).trim()));
                Optional<UiNode> hit = buttons.stream()
                        .filter(n -> wanted.equals(Locators.nfc(n.desc()).trim()) && n.height() >= rowHeight * 0.8)
                        .findFirst();
                if (hit.isPresent()) {
                    GestureUtils.tap(hit.get());
                    WaitUtils.waitInvisible(close, Duration.ofSeconds(5));
                    return;
                }
                String now = buttons.stream().map(n -> n.desc() + "@" + n.y()).collect(Collectors.joining("|"));
                if (now.equals(previous)) {
                    break; // the swipe did not move the list: nothing more in this direction
                }
                previous = now;
                int top = buttons.stream().mapToInt(UiNode::y).min().orElse(1000);
                int bottom = buttons.stream().mapToInt(n -> n.y() + n.height()).max().orElse(1800);
                int x = buttons.isEmpty() ? 540 : buttons.get(0).centerX();
                int margin = Math.max(rowHeight / 2, 20);
                int from = dir > 0 ? bottom - margin : top + margin, to = dir > 0 ? top + margin : bottom - margin;
                GestureUtils.swipe(new Point(x, from), new Point(x, to), Duration.ofMillis(500));
                WaitUtils.pause(Duration.ofMillis(300)); // list settles before the next read
            }
        }
        if (isVisible(option, Duration.ofSeconds(1))) {
            tap(option);
            return;
        }
        tap(close);
        WaitUtils.waitInvisible(close, Duration.ofSeconds(5)); // the sheet must be gone before the next option
        throw new AssertionError("Không có lựa chọn \"" + name + "\" trong danh sách. Các lựa chọn đang có: " + seen);
    }

    /**
     * Data rows of the current list (stock rows / recommendation rows), waiting up to {@code timeout} for them to
     * load. Empty list = no data.
     */
    public List<UiNode> dataRows(Duration timeout) {
        ContentInspector ci = new ContentInspector();
        // rows start below the sort row (A-Z / Giá / Khối lượng) or the recommendation sub-tabs (BSC10...)
        // below the index cards ("VN INDEX\n...(Tỷ VND)" are clickable multi-line nodes too) and the sort row
        int below = ci.content().stream()
                .filter(n -> n.desc().equals("A-Z") || n.desc().equals("Mã") || n.desc().equals("Khối lượng")
                        || n.desc().contains("(Tỷ VND)"))
                .mapToInt(n -> n.y() + n.height()).max().orElse(0);
        return ci.waitForRows(below, timeout);
    }

    // ---------------------------------------------------------------- detailed board

    /**
     * Opens the detailed price board: the icon left of the menu button has no accessibility node of its own (it lies
     * over the account header), so it is tapped by position relative to the menu button.
     */
    @Step("Bảng giá - mở bảng giá chi tiết")
    public void openDetailBoard() {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        UiNode menu = nodes.stream().filter(n -> "Mở menu di chuyển".equals(n.desc())).findFirst()
                .orElseThrow(() -> new AssertionError("Bảng giá: không thấy nút menu trên tiêu đề"));
        int w = driver().manage().window().getSize().getWidth();
        // the detail-board icon is the tappable element right before the menu button on the header row (no label)
        Optional<UiNode> icon = nodes.stream()
                .filter(n -> n.clickable() && n != menu && !n.desc().equals(menu.desc())
                        && Math.abs(n.centerY() - menu.centerY()) < menu.height() / 2
                        && n.x() + n.width() <= menu.x() + 4 && n.x() > w / 2 && n.width() < w / 5)
                .max(Comparator.comparingInt(UiNode::x));
        if (icon.isPresent()) {
            GestureUtils.tap(icon.get());
        } else {
            LoggerFactory.getLogger(PriceBoardScreen.class)
                    .warn("Icon bảng giá chi tiết không có phần tử riêng - chạm theo vị trí cạnh nút menu");
            GestureUtils.tap(menu.x() - (int) (w * 0.05), menu.centerY());
        }
        if (detailRows(Duration.ofSeconds(8)).isEmpty() && isVisible(Locators.a11y("A-Z"), Duration.ofSeconds(1))) {
            throw new AssertionError("Không mở được bảng giá chi tiết (vẫn ở Bảng giá sau khi bấm icon cạnh menu)");
        }
    }

    /** Rows of the detailed board: "TC\nTrần\nSàn\n...\nMÃ\n..." (non-clickable, many columns). */
    public List<List<String>> detailRows(Duration timeout) {
        List<List<String>> rows = new ArrayList<>();
        WaitUtils.condition(() -> {
            rows.clear();
            UiNode.snapshot(driver().getPageSource()).stream()
                    .map(UiNode::desc)
                    .filter(d -> d.split("\n", -1).length >= 10 && d.matches("(?s)[0-9.,]+\n[0-9.,]+\n[0-9.,]+\n.*"))
                    .forEach(d -> rows.add(Arrays.asList(d.split("\n", -1))));
            return !rows.isEmpty();
        }, timeout);
        return rows;
    }
}
