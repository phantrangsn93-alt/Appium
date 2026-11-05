package vn.bsc.automation.data;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One row of {@code testdata/orders.csv}.
 *
 * <pre>
 * id        unique key, referenced by order_actions.csv (e.g. EX02_LO)
 * enabled   true/false
 * excelNo   row number in the Excel scenario file (traceability)
 * subAccount sub-account name prefix as shown in the app (Thường_BSC, Phaisinh, MR90_BSC ...)
 * side      BUY | SELL
 * exchange  HOSE | HNX | UPCOM | DERIVATIVE
 * symbol    stock / derivative code
 * orderType LO | ATO | ATC | MTL | MOK | MAK | PLO | MARKET (UPCOM: market tab, price = ceiling) | ODDLOT (LO, qty &lt; 100)
 * price     REF | CEIL | FLOOR | &lt;number x1000&gt; - only for LO / ODDLOT
 * quantity  integer
 * session   when the order is VALID: ATO | CONTINUOUS | ATC | PLO | TRADING (any of ATO/CONTINUOUS/ATC) | ANY
 *           Inside the session -> expect success.
 * outOfSession what is expected OUTSIDE the session:
 *           REJECT (default) = system shows an error -> test PASSES with that message
 *           ACCEPT           = order is accepted (e.g. LO queued for the next session)
 *           ANY              = both are fine (message recorded)
 * </pre>
 */
public record OrderCase(String id, boolean enabled, String excelNo, String description, String subAccount,
                        Side side, String exchange, String symbol, String orderType, String price, long quantity,
                        List<String> sessions, OutOfSession outOfSession) {

    public enum Side { BUY, SELL }

    public enum OutOfSession { REJECT, ACCEPT, ANY }

    public static final List<String> MARKET_TYPES = List.of("ATO", "ATC", "MTL", "MOK", "MAK", "PLO");

    public static OrderCase from(Map<String, String> r) {
        return new OrderCase(
                r.get("id"),
                !"false".equalsIgnoreCase(r.getOrDefault("enabled", "true")),
                r.getOrDefault("excelNo", ""),
                r.getOrDefault("description", ""),
                r.get("subAccount"),
                Side.valueOf(r.get("side").toUpperCase(Locale.ROOT)),
                r.get("exchange").toUpperCase(Locale.ROOT),
                r.get("symbol").toUpperCase(Locale.ROOT),
                r.get("orderType").toUpperCase(Locale.ROOT),
                r.getOrDefault("price", "").toUpperCase(Locale.ROOT),
                Long.parseLong(r.get("quantity")),
                List.of(r.getOrDefault("session", "ANY").toUpperCase(Locale.ROOT).split("\\|")),
                OutOfSession.valueOf(blankTo(r.get("outOfSession"), "REJECT").toUpperCase(Locale.ROOT)));
    }

    private static String blankTo(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    public static List<OrderCase> load(String resource) {
        return CsvReader.read(resource).stream().map(OrderCase::from).collect(Collectors.toList());
    }

    public boolean isMarketType() {
        return MARKET_TYPES.contains(orderType) || "MARKET".equals(orderType);
    }

    public boolean isOddLot() {
        return "ODDLOT".equals(orderType);
    }

    @Override
    public String toString() { // shown as the test name in reports
        return id + " [" + side + " " + orderType + " " + symbol + "@" + exchange + " x" + quantity + "]";
    }
}
