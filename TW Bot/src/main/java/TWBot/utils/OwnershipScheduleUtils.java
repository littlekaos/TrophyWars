package TWBot.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class OwnershipScheduleUtils {

    public static final int PAYMENT_INTERVAL_MONTHS = 3;
    public static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    public static final ZonedDateTime BASELINE_DATE = ZonedDateTime.of(2026, 2, 16, 0, 0, 0, 0, EST_ZONE);

    private OwnershipScheduleUtils() {
    }

    public static ZonedDateTime normalizeBaseline(ZonedDateTime lastPingDate) {
        if (lastPingDate.getYear() == 2026
                && lastPingDate.getMonthValue() == 2
                && lastPingDate.getDayOfMonth() == 25) {
            return BASELINE_DATE;
        }
        return lastPingDate;
    }

    public static ZonedDateTime getNextDueDate(ZonedDateTime now) {
        ZonedDateTime dueDate = BASELINE_DATE;
        ZonedDateTime today = now.truncatedTo(ChronoUnit.DAYS);

        while (dueDate.isBefore(today)) {
            dueDate = dueDate.plusMonths(PAYMENT_INTERVAL_MONTHS);
        }

        return dueDate;
    }

    public static List<ZonedDateTime> getUpcomingDueDates(ZonedDateTime now, int count) {
        List<ZonedDateTime> dueDates = new ArrayList<>();
        ZonedDateTime dueDate = getNextDueDate(now);

        for (int i = 0; i < count; i++) {
            dueDates.add(dueDate);
            dueDate = dueDate.plusMonths(PAYMENT_INTERVAL_MONTHS);
        }

        return dueDates;
    }
}
