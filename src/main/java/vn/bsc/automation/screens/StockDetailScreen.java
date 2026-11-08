package vn.bsc.automation.screens;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.NumberUtils;
import vn.bsc.automation.core.utils.UiNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stock detail ("chi tiết mã"), opened by tapping a stock row (Thị trường > Cổ phiếu, Bảng giá, Tìm kiếm).
 * Surveyed on UAT.1-master-UAT5 (06/11/2026):
 * <pre>
 * Header ImageView   "TLD\n|\nHOSE\n&lt;tên công ty&gt;"          price View "7.90\n0.00 (0.00%)"
 * Tabs               Tổng quan | Lịch sử | Tin tức | Phân tích | Lịch sự kiện   (Locators.tab)
 * Price table        label View + value View: Trần, Tham chiếu, Sàn, Cao nhất, Thấp nhất, Room NN,
 *                    Tổng GT (Tỷ), Tổng KL, NN Mua, NN Bán, EPS, Book Value, P/E, P/B
 * Buttons            "Mua" / "Bán" (Button) -> Giao dịch > Đặt lệnh with the symbol pre-selected
 * Order book         Dư mua / Dư bán %, rows KL mua | Giá mua | Giá bán | KL bán (3 levels)
 * Sub tabs           "Khối lượng khớp" | "Lịch sử khớp lệnh"
 * </pre>
 * NOTE (06/11/2026): "Mua" switched the order form to another sub-account (MR90 BSC ĐB) and the form showed a
 * different ceiling/ref/floor (8.66/8.10/7.54) than this screen (8.45/7.90/7.35) - reported to BA. Tests that
 * place orders from here must select the sub-account explicitly and must not assume both screens agree.
 */
public class StockDetailScreen extends BaseScreen {

    public static final String[] TABS = {"Tổng quan", "Lịch sử", "Tin tức", "Phân tích", "Lịch sự kiện"};

    private final By buyButton = Locators.classWithDesc("android.widget.Button", "Mua");
    private final By sellButton = Locators.classWithDesc("android.widget.Button", "Bán");

    @Override
    protected By anchor() {
        return Locators.tab("Phân tích");
    }

    /** Header text, e.g. "TLD\n|\nHOSE\nCông ty Cổ phần ...". */
    public String getHeader() {
        return getDesc(vn.bsc.automation.core.utils.Locators.xpath("(//android.widget.ImageView[contains(@content-desc,'|')])[1]"));
    }

    public BigDecimal ceiling() {
        return price("Trần");
    }

    public BigDecimal reference() {
        return price("Tham chiếu");
    }

    public BigDecimal floor() {
        return price("Sàn");
    }

    /** Value of a label in the price table (value = next node with a desc after the label node). */
    public String value(String label) {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        String want = Locators.nfc(label);
        for (int i = 0; i < nodes.size() - 1; i++) {
            if (want.equals(nodes.get(i).desc())) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    if (!nodes.get(j).desc().isBlank()) {
                        return nodes.get(j).desc();
                    }
                }
            }
        }
        throw new AssertionError("Label not found on stock detail: " + label);
    }

    private BigDecimal price(String label) {
        return NumberUtils.parse(value(label));
    }

    public void openTab(String name) {
        tapTab(Locators.tab(name));
    }

    @Step("Chi tiết mã: bấm Mua")
    public OrderFormScreen tapBuy() {
        tap(buyButton);
        return new OrderFormScreen().waitUntilLoaded();
    }

    @Step("Chi tiết mã: bấm Bán")
    public OrderFormScreen tapSell() {
        tap(sellButton);
        return new OrderFormScreen().waitUntilLoaded();
    }
}
