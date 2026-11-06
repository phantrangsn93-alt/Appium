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
 * subAccount sub-account TYPE: CO_SO | MARGIN | PHAI_SINH (real name from config, see {@link SubAccount});
 *           a literal name prefix as shown in the app is still accepted
 * side      BUY | SELL
 * exchange  HOSE | HNX | UPCOM | DERIVATIVE
 * symbol    stock / derivative code
 * orderType LO | ATO | ATC | MTL | MOK | MAK | PLO | MARKET (UPCOM: market tab, price = ceiling) | ODDLOT (LO, qty &lt; 100)
 *           conditional ("Điều kiện"): STOP_LIMIT | TRAILING_STOP | OCO
 * price     LO / ODDLOT: limit price; STOP_LIMIT: "Giá đặt" (empty = keep the app's auto value);
 *           OCO: "Giá đặt" of the LO leg. Syntax: see {@link PriceSpec} (REF | CEIL | FLOOR | HINT[+-n] | number)
 * quantity  integer
 * session   when the order is VALID: ATO | CONTINUOUS | ATC | PLO | TRADING (any of ATO/CONTINUOUS/ATC) | ANY
 *           Inside the session -> expect success.
 * outOfSession what is expected OUTSIDE the session:
 *           REJECT (default) = system shows an error -> test PASSES with that message
 *           ACCEPT           = order is accepted (e.g. LO queued for the next session)
 *           ANY              = both are fine (message recorded)
 * validity  DAY (default, "Trong ngày") | GTC ("Nhiều ngày", e.g. LO-GTC)
 * triggerPrice STOP_LIMIT / OCO: "Giá kích hoạt" ({@link PriceSpec}, e.g. HINT+0.5)
 * amplitude TRAILING_STOP: "Biên độ" (e.g. 0.1)
 * priceStep TRAILING_STOP: "Bước giá" (e.g. 0.1)
 * </pre>
 */
public record OrderCase(String id, boolean enabled, String excelNo, String description, String subAccount,
                        Side side, String exchange, String symbol, String orderType, String price, long quantity,
                        List<String> sessions, OutOfSession outOfSession, Validity validity,
                        String triggerPrice, String amplitude, String priceStep) {

    public enum Side { BUY, SELL }

    public enum OutOfSession { REJECT, ACCEPT, ANY }

    public enum Validity { DAY, GTC }

    public static final List<String> MARKET_TYPES = List.of("ATO", "ATC", "MTL", "MOK", "MAK", "PLO");
    public static final List<String> CONDITIONAL_TYPES = List.of("STOP_LIMIT", "TRAILING_STOP", "OCO");

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
                OutOfSession.valueOf(blankTo(r.get("outOfSession"), "REJECT").toUpperCase(Locale.ROOT)),
                Validity.valueOf(blankTo(r.get("validity"), "DAY").toUpperCase(Locale.ROOT)),
                r.getOrDefault("triggerPrice", "").toUpperCase(Locale.ROOT),
                r.getOrDefault("amplitude", ""),
                r.getOrDefault("priceStep", ""));
    }

    private static String blankTo(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    public static List<OrderCase> load(String resource) {
        return CsvReader.read(resource).stream().map(OrderCase::from).collect(Collectors.toList());
    }

    /** Real sub-account name prefix to select in the app (type resolved through config). */
    public String subAccountName() {
        return SubAccount.resolve(subAccount);
    }

    public boolean isMarketType() {
        return MARKET_TYPES.contains(orderType) || "MARKET".equals(orderType);
    }

    public boolean isConditional() {
        return CONDITIONAL_TYPES.contains(orderType);
    }

    public boolean isOddLot() {
        return "ODDLOT".equals(orderType);
    }

    public boolean isMultiDay() {
        return validity == Validity.GTC;
    }

    @Override
    public String toString() { // shown as the test name in reports
        return id + " [" + side + " " + orderType + (isMultiDay() ? "-GTC" : "") + " " + symbol + "@" + exchange
                + " x" + quantity + "]";
    }
}
