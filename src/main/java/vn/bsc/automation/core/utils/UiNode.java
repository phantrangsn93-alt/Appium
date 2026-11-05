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
            for (int i = 0; i < all.getLength(); i++) {
                Element e = (Element) all.item(i);
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
}
