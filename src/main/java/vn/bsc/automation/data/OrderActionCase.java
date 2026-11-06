package vn.bsc.automation.data;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One row of {@code testdata/order_actions.csv} - actions on orders placed by {@link OrderCase} rows.
 *
 * <pre>
 * id          unique key
 * enabled     true/false
 * excelNo     row number in the Excel scenario file
 * action      MODIFY | CANCEL | CANCEL_ALL
 * targetId    id of the order in orders.csv (MODIFY / CANCEL). Empty for CANCEL_ALL.
 * priceSteps  MODIFY: number of taps on the price "+" (negative = "-"), 0/empty = keep the price
 * newQuantity MODIFY: new quantity (Excel "Sửa Khối lượng: 200"), 0/empty = keep the quantity
 * </pre>
 */
public record OrderActionCase(String id, boolean enabled, String excelNo, String description, Action action,
                              String targetId, int priceSteps, long newQuantity) {

    public enum Action { MODIFY, CANCEL, CANCEL_ALL }

    public static OrderActionCase from(Map<String, String> r) {
        String steps = r.getOrDefault("priceSteps", "");
        String qty = r.getOrDefault("newQuantity", "");
        return new OrderActionCase(
                r.get("id"),
                !"false".equalsIgnoreCase(r.getOrDefault("enabled", "true")),
                r.getOrDefault("excelNo", ""),
                r.getOrDefault("description", ""),
                Action.valueOf(r.get("action").toUpperCase(Locale.ROOT)),
                r.getOrDefault("targetId", ""),
                steps.isBlank() ? 0 : Integer.parseInt(steps),
                qty.isBlank() ? 0 : Long.parseLong(qty));
    }

    public static List<OrderActionCase> load(String resource) {
        return CsvReader.read(resource).stream().map(OrderActionCase::from).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return id + " [" + action + (targetId.isBlank() ? "" : " " + targetId) + "]";
    }
}
