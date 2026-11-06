package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;
import vn.bsc.automation.screens.components.TpSlSheet;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * "Danh mục" - holdings of the selected sub-account.
 * Each holding = symbol View (e.g. "AAA", "MBB_WFT") + a row View
 * "&lt;giá TT&gt;\nKL: \n&lt;KL&gt;\nGiá vốn: \n&lt;giá vốn&gt;\n&lt;%&gt;\n&lt;lãi/lỗ&gt;". Empty -> "Không có dữ liệu".
 * Tapping a holding opens the TP/SL sheet ({@link TpSlSheet}).
 * UAT 06/11/2026: only "MR10+ BSC" holds stocks (AAA 852,800, DBC, MBB, MBB_WFT, PPH, PPH_WFT).
 */
public class PortfolioScreen extends BaseScreen {

    public static final String[] SUMMARY_LABELS = {"Tổng giá trị vốn", "Tổng giá trị thị trường", "Lãi/Lỗ"};
    private static final Pattern SYMBOL = Pattern.compile("^[A-Z0-9]{3,}(_WFT)?$");

    private final By summary = Locators.descStartsWith("Tổng giá trị vốn");

    @Override
    protected By anchor() {
        return summary;
    }

    public String getSummaryText() {
        return getDesc(summary);
    }

    public boolean isChartButtonVisible() {
        return isVisible(Locators.a11y("Xem biểu đồ"));
    }

    public boolean isEmpty() {
        return isVisible(Locators.a11y("Không có dữ liệu"), java.time.Duration.ofSeconds(2));
    }

    /**
     * Symbols currently listed (visible part of the list). The list loads asynchronously (also after a
     * sub-account switch): waits up to 15 s for symbols or "Không có dữ liệu".
     */
    public List<String> holdings() {
        @SuppressWarnings("unchecked")
        List<String>[] found = new List[]{List.of()};
        WaitUtils.condition(() -> {
            String src = driver().getPageSource();
            found[0] = UiNode.snapshot(src).stream()
                    .filter(n -> n.clickable() && SYMBOL.matcher(n.desc()).matches())
                    .map(UiNode::desc).collect(Collectors.toList());
            return !found[0].isEmpty() || Locators.nfc(src).contains("Không có dữ liệu");
        }, java.time.Duration.ofSeconds(15));
        return found[0];
    }

    /**
     * Taps the holding ROW of the symbol (the row View "&lt;giá&gt;\nKL: ..." whose bounds contain the symbol label):
     * an accessibility click on the small symbol label does not open the sheet.
     */
    @Step("Danh mục: mở TP/SL cho {symbol}")
    public TpSlSheet openTpSl(String symbol) {
        holdings(); // wait for the list
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        UiNode label = nodes.stream().filter(n -> symbol.equals(n.desc())).findFirst()
                .orElseThrow(() -> new AssertionError("Holding not listed: " + symbol));
        int cx = label.x() + label.width() / 2, cy = label.y() + label.height() / 2;
        UiNode row = nodes.stream()
                .filter(n -> n.clickable() && n.desc().contains("KL:"))
                .filter(n -> cx >= n.x() && cx <= n.x() + n.width() && cy >= n.y() && cy <= n.y() + n.height())
                .findFirst().orElse(label);
        GestureUtils.tap(row.x() + row.width() / 2, row.y() + row.height() / 2);
        return new TpSlSheet().waitUntilLoaded();
    }
}
