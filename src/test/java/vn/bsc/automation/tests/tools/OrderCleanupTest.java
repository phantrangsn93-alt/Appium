package vn.bsc.automation.tests.tools;

import io.qameta.allure.Description;
import org.testng.SkipException;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.screens.OrderBookScreen;
import vn.bsc.automation.screens.OrderBookScreen.OrderSpec;

import java.time.LocalTime;

/**
 * Maintenance tool (not part of the regression): cancels conditional test orders left pending, identified by
 * symbol / side / quantity / type AND "Giờ đặt" >= {@code -Dcleanup.from=HH:mm:ss} (today).
 * Example: mvn test -Dsuite=testng-cleanup.xml -Dcleanup.from=11:20:00 -Dcleanup.symbol=AAA -Dcleanup.qty=100
 */
public class OrderCleanupTest extends LoggedInBaseTest {

    @Test(groups = "tools")
    @Description("Huỷ lệnh điều kiện do test tạo thừa (theo Giờ đặt)")
    public void cancelLeftoverConditionalOrders() {
        String from = ConfigReader.get("cleanup.from");
        if (from == null) {
            throw new SkipException("Thiếu -Dcleanup.from=HH:mm:ss");
        }
        String symbol = ConfigReader.get("cleanup.symbol", "AAA");
        long qty = Long.parseLong(ConfigReader.get("cleanup.qty", "100"));
        boolean buy = !"SELL".equalsIgnoreCase(ConfigReader.get("cleanup.side", "BUY"));
        OrderBookScreen book = navigator.openTrading().openOrderBook();
        int total = 0;
        for (String type : ConfigReader.get("cleanup.types", "Stop Limit,Trailing Stop,OCO").split(",")) {
            total += book.cancelConditionalPlacedFrom(new OrderSpec(symbol, buy, qty, null, type.trim(), true),
                    LocalTime.parse(from), 20);
        }
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Cleanup: đã gửi {} yêu cầu huỷ", total);
    }
}
