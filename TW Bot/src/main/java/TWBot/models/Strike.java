package TWBot.models;

public class Strike {
    private int id;
    private String userId;
    private String reason;
    private String moderatorId;
    private String date;

    public Strike(int id, String userId, String reason, String moderatorId, java.sql.Timestamp date) {
        this.id = id;
        this.userId = userId;
        this.reason = reason;
        this.moderatorId = moderatorId;
        this.date = date.toString();
    }

    public int getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public String getModeratorId() {
        return moderatorId;
    }

    public String getDate() {
        return date;
    }
}