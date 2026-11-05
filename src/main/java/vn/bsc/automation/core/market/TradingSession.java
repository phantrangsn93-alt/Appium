package vn.bsc.automation.core.market;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether "now" is inside a trading session of an exchange, from {@code config/sessions.properties}.
 * Session names: ATO, CONTINUOUS, ATC, PLO, TRADING (= ATO | CONTINUOUS | ATC), ANY (always open).
 */
public final class TradingSession {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Properties P = new Properties();
    private static final Set<LocalDate> HOLIDAYS;

    static {
        try (InputStream in = TradingSession.class.getClassLoader().getResourceAsStream("config/sessions.properties")) {
            if (in == null) {
                throw new IllegalStateException("config/sessions.properties not found");
            }
            P.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        HOLIDAYS = Arrays.stream(P.getProperty("holidays", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(LocalDate::parse).collect(Collectors.toSet());
    }

    private TradingSession() {
    }

    public static boolean isTradingDay(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !HOLIDAYS.contains(d);
    }

    /** True when {@code now} is inside ANY of the given sessions of the exchange. */
    public static boolean isOpen(String exchange, List<String> sessions, ZonedDateTime now) {
        if (sessions.contains("ANY")) {
            return true;
        }
        ZonedDateTime t = now.withZoneSameInstant(VN);
        if (!isTradingDay(t.toLocalDate())) {
            return false;
        }
        for (String s : sessions) {
            List<String> names = "TRADING".equals(s) ? List.of("ATO", "CONTINUOUS", "ATC") : List.of(s);
            for (String n : names) {
                if (inRanges(P.getProperty(exchange + "." + n, ""), t.toLocalTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isOpenNow(String exchange, List<String> sessions) {
        return isOpen(exchange, sessions, ZonedDateTime.now(VN));
    }

    static boolean inRanges(String ranges, LocalTime time) {
        for (String r : ranges.split(",")) {
            String[] p = r.trim().split("-");
            if (p.length == 2 && !time.isBefore(LocalTime.parse(p[0].trim())) && time.isBefore(LocalTime.parse(p[1].trim()))) {
                return true;
            }
        }
        return false;
    }
}
