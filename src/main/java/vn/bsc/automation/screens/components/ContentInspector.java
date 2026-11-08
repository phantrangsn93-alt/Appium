package vn.bsc.automation.screens.components;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * "Does this list / section / chart show data?" checks shared by Bảng giá, Thị trường and Tìm kiếm.
 * <p>
 * Data row = clickable node with a multi-line label (stock/news/fund rows: "AAA\n\n\nHOSE\nTên\n7.08..."),
 * excluding section headers ("Khối lượng lớn nhất\nXem thêm") and the news teaser. Empty markers: no row at all,
 * or the app's "Không có dữ liệu".
 */
public class ContentInspector extends BaseScreen {

    public static final String NO_DATA = "Không có dữ liệu";

    /** Verdict for one section. */
    public enum State { DATA, EMPTY, NOT_FOUND }

    @Override
    protected By anchor() {
        return Locators.a11y("Thị trường");
    }

    /** Snapshot of the visible content between the screen header and the bottom navigation bar. */
    public List<UiNode> content() {
        List<UiNode> all = UiNode.snapshot(driver().getPageSource());
        int navTop = all.stream().filter(n -> "Thị trường".equals(n.desc()) || n.desc().endsWith("\nThị trường"))
                .mapToInt(UiNode::y).min().orElse(Integer.MAX_VALUE);
        List<UiNode> out = new ArrayList<>();
        for (UiNode n : all) {
            if (n.y() < navTop && n.y() >= 0 && n.x() >= 0 && n.x() < windowWidth() && !n.desc().isEmpty()) {
                out.add(n);
            }
        }
        out.sort(Comparator.comparingInt(UiNode::y).thenComparingInt(UiNode::x));
        return out;
    }

    public static boolean isDataRow(UiNode n, Set<String> headers) {
        String d = n.desc();
        return n.clickable() && d.contains("\n") && !d.startsWith("Tin tức") && !d.contains("Tab ")
                && headers.stream().noneMatch(h -> d.equals(h) || d.startsWith(h + "\n"));
    }

    /** Waits up to {@code timeout} for at least one data row below {@code belowY}; returns the rows seen. */
    public List<UiNode> waitForRows(int belowY, Duration timeout) {
        List<UiNode> rows = new ArrayList<>();
        WaitUtils.condition(() -> {
            rows.clear();
            content().stream().filter(n -> n.y() > belowY && isDataRow(n, Set.of())).forEach(rows::add);
            return !rows.isEmpty();
        }, timeout);
        return rows;
    }

    /**
     * Verdict per section header, scrolling down as needed.
     * <ul>
     *   <li>DATA: the first node after the header is a data row;</li>
     *   <li>EMPTY: the app shows "Không có dữ liệu" under it, or - after {@code loadTimeout} - the next header
     *       follows directly / nothing follows it at the end of the page (rows load asynchronously, so "next header
     *       right below" is NOT conclusive while loading);</li>
     *   <li>NOT_FOUND: header never seen.</li>
     * </ul>
     */
    public Map<String, State> sections(List<String> headers, Duration loadTimeout) {
        Set<String> hs = Set.copyOf(headers);
        Map<String, State> result = new LinkedHashMap<>();
        headers.forEach(h -> result.put(h, State.NOT_FOUND));
        String previousPage = "";
        for (int scroll = 0; scroll <= 4; scroll++) {
            long deadline = System.currentTimeMillis() + loadTimeout.toMillis();
            Map<String, Verdict> v;
            // wait on this screenful until every visible header has a conclusive verdict (or the timeout)
            do {
                v = evaluate(content(), hs);
                boolean pending = v.values().stream().anyMatch(x -> x == Verdict.ADJACENT || x == Verdict.UNKNOWN);
                if (!pending && !v.isEmpty()) {
                    break;
                }
                WaitUtils.condition(() -> false, Duration.ofMillis(700));
            } while (System.currentTimeMillis() < deadline);
            List<UiNode> page = content();
            String signature = page.stream().map(UiNode::desc).reduce("", String::concat);
            boolean endOfPage = signature.equals(previousPage);
            v.forEach((h, x) -> {
                if (result.get(h) != State.NOT_FOUND) {
                    return;
                }
                switch (x) {
                    case DATA -> result.put(h, State.DATA);
                    case NO_DATA, ADJACENT -> result.put(h, State.EMPTY); // timeout passed: still nothing under it
                    case UNKNOWN -> {
                        if (endOfPage) {
                            result.put(h, State.EMPTY); // last header, nothing below it, page cannot scroll further
                        }
                    }
                }
            });
            if (result.values().stream().noneMatch(s -> s == State.NOT_FOUND) || endOfPage) {
                break;
            }
            previousPage = signature;
            GestureUtils.scrollBy(0.4);
        }
        return result;
    }

    private enum Verdict { DATA, NO_DATA, ADJACENT, UNKNOWN }

    /** Per visible header: what follows it on the current screenful. */
    private static Map<String, Verdict> evaluate(List<UiNode> nodes, Set<String> headers) {
        Map<String, Verdict> out = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            Optional<String> h = headerOf(nodes.get(i), headers);
            if (h.isEmpty()) {
                continue;
            }
            Verdict s = Verdict.UNKNOWN;
            for (int j = i + 1; j < nodes.size() && s == Verdict.UNKNOWN; j++) {
                UiNode n = nodes.get(j);
                if (n.y() < nodes.get(i).y() + nodes.get(i).height() / 2) {
                    continue; // same row as the header (e.g. "Xem thêm")
                }
                if (NO_DATA.equals(n.desc())) {
                    s = Verdict.NO_DATA;
                } else if (headerOf(n, headers).isPresent()) {
                    s = Verdict.ADJACENT;
                } else if (isDataRow(n, headers)) {
                    s = Verdict.DATA;
                }
            }
            out.put(h.get(), s);
        }
        return out;
    }

    private static Optional<String> headerOf(UiNode n, Set<String> headers) {
        return headers.stream().filter(h -> n.desc().equals(h) || n.desc().startsWith(h + "\n")).findFirst();
    }

    /**
     * Share of strongly coloured pixels (chart bars/lines) inside a screen band - charts are drawn on a canvas and
     * expose no values to accessibility. Only the 15%-60% horizontal band is sampled: skips the Y-axis labels and
     * the floating promo banner on the right.
     */
    public double colouredPixelRatio(int top, int bottom) {
        try {
            byte[] png = driver().getScreenshotAs(OutputType.BYTES);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            double scale = (double) img.getWidth() / windowWidth(); // iOS: screenshot pixels vs points
            int y0 = (int) (top * scale), y1 = Math.min(img.getHeight(), (int) (bottom * scale));
            int x0 = (int) (img.getWidth() * 0.15), x1 = (int) (img.getWidth() * 0.60);
            long coloured = 0, total = 0;
            for (int y = y0; y < y1; y += 2) {
                for (int x = x0; x < x1; x += 2) {
                    int p = img.getRGB(x, y);
                    int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
                    if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 90) {
                        coloured++;
                    }
                    total++;
                }
            }
            return total == 0 ? 0 : (double) coloured / total;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot analyse screenshot", e);
        }
    }

    /** Taps the on-screen node whose label equals {@code label} (ignores copies kept off-screen by Flutter). */
    public boolean tapVisible(String label) {
        Optional<UiNode> n = content().stream().filter(x -> label.equals(x.desc())).findFirst();
        n.ifPresent(x -> GestureUtils.tap(x.x() + x.width() / 2, x.y() + x.height() / 2));
        return n.isPresent();
    }

    private int width = -1;

    private int windowWidth() {
        if (width < 0) {
            Dimension s = driver().manage().window().getSize();
            width = s.getWidth();
        }
        return width;
    }
}
