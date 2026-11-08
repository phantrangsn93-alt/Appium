package vn.bsc.automation.screens;

import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.data.IndexCard;
import vn.bsc.automation.data.StockRow;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/** "Bảng giá": watch-list selector, index cards, stock rows, news. */
public class PriceBoardScreen extends BaseScreen {

    private final By sortByVolume = Locators.a11y("Khối lượng");
    private final By sortByPrice = Locators.a11y("Giá");
    private final By stockRows = vn.bsc.automation.core.utils.Locators.xpath("//android.view.View[contains(@content-desc,'|') and "
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
    private List<vn.bsc.automation.core.utils.UiNode> selectorChips() {
        List<vn.bsc.automation.core.utils.UiNode> views = new vn.bsc.automation.screens.components.ContentInspector().content()
                .stream().filter(n -> n.clickable() && "android.view.View".equals(n.className()) && !n.desc().contains("\n"))
                .collect(Collectors.toList());
        int top = views.stream().mapToInt(vn.bsc.automation.core.utils.UiNode::y).min()
                .orElseThrow(() -> new AssertionError("Không thấy hàng bộ chọn của Bảng giá"));
        return views.stream().filter(n -> Math.abs(n.y() - top) < 10)
                .sorted(java.util.Comparator.comparingInt(vn.bsc.automation.core.utils.UiNode::x))
                .collect(Collectors.toList());
    }

    /**
     * The chip row scrolls horizontally (8 chips: Danh mục, Chỉ số, CP ngành, Phái sinh, Chứng quyền, ETFs, Lô lẻ,
     * Trái phiếu) and a chip's label changes with the selection. The row is scrolled to its start, then walked left
     * while collecting labels in order until chip {@code index} is fully on screen, then tapped.
     */
    private void tapChip(int index) {
        org.openqa.selenium.Dimension w = driver().manage().window().getSize();
        vn.bsc.automation.core.utils.UiNode first = selectorChips().get(0);
        int rowY = first.y(), y = first.y() + first.height() / 2;
        for (int i = 0; i < 3; i++) { // to the start of the row (slow drags: a fast one flings the row)
            dragRow(y, (int) (w.width * 0.25), (int) (w.width * 0.9));
        }
        List<String> order = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<vn.bsc.automation.core.utils.UiNode> chips = chipsOnRow(rowY);
            chips.forEach(c -> {
                if (!order.contains(c.desc())) {
                    order.add(c.desc());
                }
            });
            if (order.size() > index) {
                String label = order.get(index);
                java.util.Optional<vn.bsc.automation.core.utils.UiNode> c = chips.stream()
                        .filter(x -> x.desc().equals(label)).findFirst();
                if (c.isPresent() && c.get().x() + c.get().width() < w.width - 20 && c.get().width() > 60) {
                    vn.bsc.automation.core.utils.GestureUtils.tap(c.get().x() + c.get().width() / 2, y);
                    return;
                }
            }
            dragRow(y, (int) (w.width * 0.75), (int) (w.width * 0.45));
        }
        throw new AssertionError("Bảng giá: không bấm được bộ chọn thứ " + (index + 1) + " (thấy " + order + ")");
    }

    /** Slow horizontal drag on the chip row, then wait until the row reports its chips again (no fling). */
    private void dragRow(int y, int fromX, int toX) {
        vn.bsc.automation.core.utils.GestureUtils.swipe(new org.openqa.selenium.Point(fromX, y),
                new org.openqa.selenium.Point(toX, y), Duration.ofMillis(900));
        int rowY = y;
        WaitUtils.condition(() -> !chipsOnRow(rowY - 60).isEmpty(), Duration.ofSeconds(2));
    }

    /** Chips whose top edge is within the chip-row band (never the sort row A-Z / Giá / Khối lượng below it). */
    private List<vn.bsc.automation.core.utils.UiNode> chipsOnRow(int rowY) {
        return new vn.bsc.automation.screens.components.ContentInspector().content().stream()
                .filter(n -> n.clickable() && "android.view.View".equals(n.className()) && !n.desc().contains("\n")
                        && Math.abs(n.y() - rowY) < 70)
                .sorted(java.util.Comparator.comparingInt(vn.bsc.automation.core.utils.UiNode::x))
                .collect(Collectors.toList());
    }

    /** Taps chip {@code index}; picks {@code option} from its sheet when given (null = chip opens the list directly). */
    @io.qameta.allure.Step("Bảng giá - bộ chọn {index}: {option}")
    public void selectChip(int index, String option) {
        tapChip(index);
        if (option != null && !option.isEmpty()) {
            pickFromSheet(option);
        }
    }

    /** Label currently shown by chip 0 (watch-list), 1 (index) or 2 (industry). */
    public String selectorLabel(int index) {
        return selectorChips().get(index).desc();
    }

    @io.qameta.allure.Step("Bảng giá - danh mục: {name}")
    public void selectWatchlist(String name) {
        tapChip(0);
        By option = Locators.a11y(name);
        WaitUtils.visible(Locators.a11y("Chọn danh mục"), Duration.ofSeconds(5));
        tap(option);
        WaitUtils.waitInvisible(Locators.a11y("Chọn danh mục"), Duration.ofSeconds(5));
    }

    @io.qameta.allure.Step("Bảng giá - chỉ số: {name}")
    public void selectIndex(String name) {
        selectChip(1, name);
    }

    @io.qameta.allure.Step("Bảng giá - ngành: {name}")
    public void selectIndustry(String name) {
        selectChip(2, name);
    }

    @io.qameta.allure.Step("Bảng giá - tab con: {name}")
    public void selectSubTab(String name) {
        if (!new vn.bsc.automation.screens.components.ContentInspector().tapVisible(name)) {
            throw new AssertionError("Không thấy tab con " + name);
        }
    }

    /** Option sheets list Buttons above a "Đóng" button; scroll inside the sheet until the option is visible. */
    private void pickFromSheet(String name) {
        By close = Locators.classWithDesc("android.widget.Button", "Đóng");
        WaitUtils.visible(close, Duration.ofSeconds(5));
        By option = Locators.classWithDesc("android.widget.Button", name);
        for (int dir : new int[]{1, -1}) {
            for (int i = 0; i < 8; i++) {
                List<vn.bsc.automation.core.utils.UiNode> buttons = vn.bsc.automation.core.utils.UiNode
                        .snapshot(driver().getPageSource()).stream()
                        .filter(n -> n.className().endsWith("Button") && n.height() > 20 && !"Đóng".equals(n.desc()))
                        .collect(Collectors.toList());
                java.util.Optional<vn.bsc.automation.core.utils.UiNode> hit = buttons.stream()
                        .filter(n -> name.equals(n.desc()) && n.height() > 60).findFirst();
                if (hit.isPresent()) {
                    vn.bsc.automation.core.utils.UiNode h = hit.get();
                    vn.bsc.automation.core.utils.GestureUtils.tap(h.x() + h.width() / 2, h.y() + h.height() / 2);
                    WaitUtils.waitInvisible(close, Duration.ofSeconds(5));
                    return;
                }
                int top = buttons.stream().mapToInt(vn.bsc.automation.core.utils.UiNode::y).min().orElse(1000);
                int bottom = buttons.stream().mapToInt(n -> n.y() + n.height()).max().orElse(1800);
                int x = buttons.isEmpty() ? 540 : buttons.get(0).x() + buttons.get(0).width() / 2;
                int from = dir > 0 ? bottom - 40 : top + 40, to = dir > 0 ? top + 40 : bottom - 40;
                vn.bsc.automation.core.utils.GestureUtils.swipe(new org.openqa.selenium.Point(x, from),
                        new org.openqa.selenium.Point(x, to), Duration.ofMillis(500));
            }
        }
        if (isVisible(option, Duration.ofSeconds(1))) {
            tap(option);
            return;
        }
        tap(close);
        throw new AssertionError("Không có lựa chọn \"" + name + "\" trong danh sách");
    }

    /**
     * Data rows of the current list (stock rows / recommendation rows), waiting up to {@code timeout} for them to
     * load. Empty list = no data.
     */
    public List<vn.bsc.automation.core.utils.UiNode> dataRows(Duration timeout) {
        vn.bsc.automation.screens.components.ContentInspector ci = new vn.bsc.automation.screens.components.ContentInspector();
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
    @io.qameta.allure.Step("Bảng giá - mở bảng giá chi tiết")
    public void openDetailBoard() {
        org.openqa.selenium.Rectangle menu = find(Locators.a11y("Mở menu di chuyển")).getRect();
        int w = driver().manage().window().getSize().getWidth();
        vn.bsc.automation.core.utils.GestureUtils.tap(menu.getX() - (int) (w * 0.05), menu.getY() + menu.getHeight() / 2);
    }

    /** Rows of the detailed board: "TC\nTrần\nSàn\n...\nMÃ\n..." (non-clickable, many columns). */
    public List<List<String>> detailRows(Duration timeout) {
        List<List<String>> rows = new java.util.ArrayList<>();
        WaitUtils.condition(() -> {
            rows.clear();
            vn.bsc.automation.core.utils.UiNode.snapshot(driver().getPageSource()).stream()
                    .map(vn.bsc.automation.core.utils.UiNode::desc)
                    .filter(d -> d.split("\n", -1).length >= 10 && d.matches("(?s)[0-9.,]+\n[0-9.,]+\n[0-9.,]+\n.*"))
                    .forEach(d -> rows.add(java.util.Arrays.asList(d.split("\n", -1))));
            return !rows.isEmpty();
        }, timeout);
        return rows;
    }
}
