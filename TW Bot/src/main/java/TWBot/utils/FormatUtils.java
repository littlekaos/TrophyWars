package TWBot.utils;

public class FormatUtils {
    public static long parseDurationToMinutes(String duration) {
        if (duration == null || duration.isEmpty()) {
            return -1;
        }

        String input = duration.toLowerCase().trim();
        long multiplier = 1;

        if (input.endsWith("w")) {
            multiplier = 10080;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("d")) {
            multiplier = 1440;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("h")) {
            multiplier = 60;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 1;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("s")) {
            try {
                return (long) Math.ceil(Long.parseLong(input.substring(0, input.length() - 1)) / 60.0);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        try {
            long value = Long.parseLong(input);
            long totalMinutes = value * multiplier;
            
            if (totalMinutes > 40320) { // Max 28 days for timeouts
                return 40320;
            }
            if (totalMinutes < 1) {
                return -1;
            }
            return totalMinutes;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
