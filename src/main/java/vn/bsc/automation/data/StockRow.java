package vn.bsc.automation.data;

/**
 * Row of the price board. Source content-desc example:
 * <pre>AAA\n|\nHOSE\nCông ty Cổ phần Nhựa An Phát Xanh\n7.08\n909,600\n-0.09\n-1.26%</pre>
 */
public record StockRow(String symbol, String exchange, String companyName, String price, String volume,
                       String change, String changePercent, String raw) {

    public static StockRow parse(String desc) {
        String[] p = desc.split("\n");
        if (p.length < 8) {
            return new StockRow(p[0].trim(), "", "", "", "", "", "", desc);
        }
        return new StockRow(p[0].trim(), p[2].trim(), p[3].trim(), p[4].trim(), p[5].trim(),
                p[6].trim(), p[7].trim(), desc);
    }
}
