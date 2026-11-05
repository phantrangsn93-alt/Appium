package vn.bsc.automation.core.utils;

import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.bsc.automation.core.config.ConfigReader;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Pauses a test until a human performs an action on the device (e.g. typing username/password/PIN),
 * so secrets are never stored in code, config or logs.
 * <p>
 * Timeout: {@code manual.input.timeout} seconds (default 300).
 */
public final class ManualInput {

    private static final Logger LOG = LoggerFactory.getLogger(ManualInput.class);

    private ManualInput() {
    }

    public static Duration timeout() {
        return Duration.ofSeconds(ConfigReader.getInt("manual.input.timeout", 300));
    }

    /**
     * Prints the instruction and polls {@code done} every 500 ms until it returns true.
     *
     * @throws AssertionError if the user does not finish within the timeout
     */
    public static void waitForUser(String instruction, Supplier<Boolean> done) {
        Duration timeout = timeout();
        Allure.step("⏸ CHỜ NGƯỜI DÙNG: " + instruction);
        banner(instruction, timeout);
        Instant end = Instant.now().plus(timeout);
        Instant nextReminder = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(end)) {
            try {
                if (Boolean.TRUE.equals(done.get())) {
                    LOG.info("▶ Người dùng đã thao tác xong - tiếp tục test");
                    return;
                }
            } catch (Exception ignored) {
                // screen in transition
            }
            if (Instant.now().isAfter(nextReminder)) {
                LOG.warn("… vẫn đang chờ ({}s còn lại): {}", Duration.between(Instant.now(), end).toSeconds(), instruction);
                nextReminder = Instant.now().plusSeconds(30);
            }
            pause();
        }
        throw new AssertionError("Hết thời gian chờ người dùng (" + timeout.toSeconds() + "s): " + instruction);
    }

    private static void banner(String instruction, Duration timeout) {
        String line = "=".repeat(78);
        vn.bsc.automation.core.driver.DevicePool.Device d = vn.bsc.automation.core.driver.DevicePool.current();
        String device = d == null ? "" : " - THIẾT BỊ " + d.udid();
        LOG.warn("\n{}\n  ⏸  CẦN THAO TÁC TRÊN ĐIỆN THOẠI{} (tối đa {}s)\n  {}\n{}", line, device, timeout.toSeconds(), instruction, line);
    }

    /** Polling interval without Thread.sleep in test code. */
    private static void pause() {
        WaitUtils.condition(() -> false, Duration.ofMillis(500));
    }
}
