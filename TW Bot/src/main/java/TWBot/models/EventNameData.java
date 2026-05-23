package TWBot.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class EventNameData implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String userId;
    private final String name;
    private final long timestamp;

    public EventNameData(String userId, String name, long timestamp) {
        this.userId = userId;
        this.name = name;
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedDate() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}