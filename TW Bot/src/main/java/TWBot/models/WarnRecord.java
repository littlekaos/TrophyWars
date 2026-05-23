package TWBot.models;

import java.io.Serializable;

public class WarnRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String moderatorId;
    private String reason;
    private long timestamp;

    public WarnRecord(String userId, String moderatorId, String reason, long timestamp) {
        this.userId = userId;
        this.moderatorId = moderatorId;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public String getModeratorId() {
        return moderatorId;
    }

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
