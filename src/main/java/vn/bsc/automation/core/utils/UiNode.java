package vn.bsc.automation.core.utils;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight node parsed from a page-source snapshot.
 * Use when reading many values from a screen whose content refreshes in real time
 * (one consistent snapshot, no stale elements, one round-trip instead of N).
 */
public record UiNode(String className, String desc, String text, boolean clickable, int x, int y, int width, int height) {

    private static final Pattern BOUNDS = Pattern.compile("\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]");

    public static List<UiNode> snapshot(String pageSource) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList all = f.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(pageSource.getBytes(StandardCharsets.UTF_8)))
                    .getElementsByTagName("*");
            List<UiNode> nodes = new ArrayList<>(all.getLength());
            int appW = Integer.MAX_VALUE, appH = Integer.MAX_VALUE;
            for (int i = 0; i < all.getLength(); i++) {
                Element e = (Element) all.item(i);
                if (e.hasAttribute("type") && e.getAttribute("type").startsWith("XCUIElementType")) {
                    if ("XCUIElementTypeApplication".equals(e.getAttribute("type")) && e.hasAttribute("width")) {
                        appW = Integer.parseInt(e.getAttribute("width"));
                        appH = Integer.parseInt(e.getAttribute("height"));
                    }
                    UiNode n = fromIos(e, appW, appH);
                    if (n != null) {
                        nodes.add(n);
                    }
                    continue;
                }
                Matcher m = BOUNDS.matcher(e.getAttribute("bounds"));
                if (!m.matches()) {
                    continue;
                }
                int x1 = Integer.parseInt(m.group(1)), y1 = Integer.parseInt(m.group(2));
                int x2 = Integer.parseInt(m.group(3)), y2 = Integer.parseInt(m.group(4));
                nodes.add(new UiNode(e.getAttribute("class"), Locators.nfc(e.getAttribute("content-desc")),
                        Locators.nfc(e.getAttribute("text")),
                        Boolean.parseBoolean(e.getAttribute("clickable")), x1, y1, x2 - x1, y2 - y1));
            }
            return nodes;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse page source", e);
        }
    }

    /**
     * iOS (XCUITest) node: label -&gt; desc, value -&gt; text, type -&gt; Android role (see
     * {@link vn.bsc.automation.core.config.Platform#androidRole}), x/y/width/height in points (the same unit as
     * W3C actions and window size on iOS). Zero-size nodes are skipped; the hit-test based "visible" flag is ignored.
     * clickable ~ accessibility element (iOS has no clickable attribute; tappable Flutter rows are StaticText).
     */
    private static UiNode fromIos(Element e, int appW, int appH) {
        // NOT the "visible" flag: a full-screen ad container makes real widgets report visible=false (hit-test);
        // nodes are kept when their frame is on screen (callers filter by position like on Android)
        if (!e.hasAttribute("width")) {
            return null;
        }
        String type = e.getAttribute("type");
        int w = Integer.parseInt(e.getAttribute("width")), h = Integer.parseInt(e.getAttribute("height"));
        int x = Integer.parseInt(e.getAttribute("x")), y = Integer.parseInt(e.getAttribute("y"));
        if (w <= 0 || h <= 0 || x >= appW || y >= appH || x + w <= 0 || y + h <= 0) {
            return null; // zero-size or entirely outside the app window
        }
        // Flutter exposes tappable rows (stock / news rows) as accessible StaticText on iOS: accessible = clickable
        boolean clickable = "true".equals(e.getAttribute("accessible"));
        return new UiNode(vn.bsc.automation.core.config.Platform.androidRole(type), Locators.nfc(e.getAttribute("label")),
                Locators.nfc(e.getAttribute("value")), clickable,
                x, y, w, h);
    }
}
