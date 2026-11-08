package vn.bsc.automation.data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One row of {@code testdata/menu_transactions.csv} (rows are executed top -&gt; bottom, like the menu).
 * <pre>
 * id          unique key
 * enabled     true/false
 * menuItem    MenuItem enum name (CHUYEN_TIEN, NOP_RUT_TIEN_PHAI_SINH, CHUYEN_CHUNG_KHOAN ...)
 * option      SheetOption enum name for bottom-sheet items (CHUYEN_TIEN_NOI_BO ...), empty for direct screens
 * amount      amount (VND) or quantity (shares) typed into the first input of the form
 * note        free text (reason when disabled)
 * </pre>
 */
public record MenuTransactionCase(String id, boolean enabled, String menuItem, String option, String amount, String note) {

    public static List<MenuTransactionCase> load(String resource) {
        return CsvReader.read(resource).stream().map(MenuTransactionCase::from).collect(Collectors.toList());
    }

    static MenuTransactionCase from(Map<String, String> r) {
        return new MenuTransactionCase(r.get("id"), !"false".equalsIgnoreCase(r.getOrDefault("enabled", "true")),
                r.get("menuItem"), r.getOrDefault("option", ""), r.getOrDefault("amount", ""), r.getOrDefault("note", ""));
    }

    @Override
    public String toString() {
        return id + " [" + (option.isBlank() ? menuItem : option) + " " + amount + "]";
    }
}
