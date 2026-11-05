package vn.bsc.automation.data;

/**
 * Selected-symbol card on the order form. Two formats exist:
 * <pre>
 *   stock:      AAA\n | \nHOSE\n7.08\nCông ty cổ phần Nhựa An Phát Xanh\n-0.09 (-1.26%)
 *   derivative: 41I1GB000\n1,867.40\nVN30F2611\n+5.40 (+0.29%)
 * </pre>
 */
public record SelectedStock(String symbol, String exchange, String price, String companyName, String change,
                            String raw) {

    public static SelectedStock parse(String desc) {
        String[] p = desc.split("\n");
        boolean stock = p.length > 1 && "|".equals(p[1].trim());
        if (!stock) { // derivative contract: no exchange label
            return new SelectedStock(p[0].trim(), "DERIVATIVE", at(p, 1), at(p, 2), at(p, 3), desc);
        }
        return new SelectedStock(p[0].trim(), at(p, 2), at(p, 3), at(p, 4), at(p, 5), desc);
    }

    private static String at(String[] p, int i) {
        return p.length > i ? p[i].trim() : "";
    }
}
