package vn.bsc.automation.core.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.bsc.automation.core.config.ConfigReader;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Pool of devices for parallel execution. Configure {@code devices=udid1:8200,udid2:8201}
 * (udid:UiAutomator2 systemPort - ports must be unique; iOS: {@code ios.devices=udid:wdaLocalPort}). Each test class borrows one free device for its
 * whole lifetime, so with N devices N classes run at the same time ({@code parallel="classes"}).
 * <p>
 * One Android device runs one app instance: parallelism always means more devices/emulators.
 */
public final class DevicePool {

    public record Device(String udid, int systemPort) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(DevicePool.class);
    private static final BlockingQueue<Device> FREE = new LinkedBlockingQueue<>();
    private static final ThreadLocal<Device> CURRENT = new ThreadLocal<>();
    private static final java.util.Set<Device> BORROWED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static {
        // iOS: ios.devices=udid:wdaLocalPort,... (WebDriverAgent ports must be unique, e.g. 8100, 8101)
        boolean ios = vn.bsc.automation.core.config.AppConfig.isIOS();
        String cfg = ConfigReader.get(ios ? "ios.devices" : "devices", "");
        if (cfg.isBlank()) {
            String udid = ios ? ConfigReader.get("ios.udid", "") : ConfigReader.get("device.udid", "");
            cfg = udid + ":" + (ios ? ConfigReader.getInt("ios.wda.port", 8100) : ConfigReader.getInt("system.port", 8200));
        }
        for (String d : cfg.split(",")) {
            String[] p = d.trim().split(":");
            FREE.add(new Device(p[0].trim(), p.length > 1 ? Integer.parseInt(p[1].trim()) : 8200));
        }
        LOG.info("Device pool: {}", FREE);
    }

    private DevicePool() {
    }

    public static int size() {
        return FREE.size() + (CURRENT.get() != null ? 1 : 0);
    }

    /** Blocks until a device is free (max 60 min). */
    public static Device acquire() {
        try {
            Device d = FREE.poll(60, TimeUnit.MINUTES);
            if (d == null) {
                throw new IllegalStateException("No free device within 60 minutes");
            }
            CURRENT.set(d);
            BORROWED.add(d);
            LOG.info("[{}] acquired device {}", Thread.currentThread().getName(), d.udid());
            return d;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public static void release() {
        Device d = CURRENT.get();
        if (d != null) {
            CURRENT.remove();
            BORROWED.remove(d);
            FREE.add(d);
            LOG.info("[{}] released device {}", Thread.currentThread().getName(), d.udid());
        }
    }

    /** Returns every borrowed device to the pool (end of suite with shared sessions). */
    public static void releaseAll() {
        BORROWED.forEach(d -> {
            if (!FREE.contains(d)) {
                FREE.add(d);
            }
        });
        BORROWED.clear();
        CURRENT.remove();
    }

    /** Device used by the current thread (null when the udid came from testng.xml parameters). */
    public static Device current() {
        return CURRENT.get();
    }
}
