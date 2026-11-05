package vn.bsc.automation.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/** Reads JSON test data from {@code src/test/resources/testdata}. Data is kept out of test code. */
public final class TestDataReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentHashMap<String, JsonNode> CACHE = new ConcurrentHashMap<>();

    private TestDataReader() {
    }

    public static JsonNode read(String fileName) {
        return CACHE.computeIfAbsent(fileName, f -> {
            String path = "testdata/" + f;
            try (InputStream in = TestDataReader.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Test data not found: " + path);
                }
                return MAPPER.readTree(in);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot parse " + path, e);
            }
        });
    }

    public static <T> T read(String fileName, String node, Class<T> type) {
        try {
            return MAPPER.treeToValue(read(fileName).get(node), type);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot map " + fileName + "#" + node, e);
        }
    }
}
