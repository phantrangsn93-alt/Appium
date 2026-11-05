package vn.bsc.automation.data;

/**
 * Market index card. Source content-desc example:
 * <pre>VN INDEX\n1,740.73\n+7.71 (+0.44%)\n15,829.8 (Tỷ VND)</pre>
 */
public record IndexCard(String name, String value, String change, String turnover, String raw) {

    public static IndexCard parse(String desc) {
        String[] p = desc.split("\n");
        return new IndexCard(
                p[0].trim(),
                p.length > 1 ? p[1].trim() : "",
                p.length > 2 ? p[2].trim() : "",
                p.length > 3 ? p[3].trim() : "",
                desc);
    }
}
