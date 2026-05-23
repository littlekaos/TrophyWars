package TWBot.models;

public class Appeal {
    private int id;
    private String userId;
    private int strikeId;
    private String reason;
    private String status; // PENDING, APPROVED, DENIED
    private String reviewerId;
    private String reviewReason;
    private String date;
    private String reviewDate;

    public Appeal(int id, String userId, int strikeId, String reason, String status,
                  String reviewerId, String reviewReason, java.sql.Timestamp date, java.sql.Timestamp reviewDate) {
        this.id = id;
        this.userId = userId;
        this.strikeId = strikeId;
        this.reason = reason;
        this.status = status;
        this.reviewerId = reviewerId;
        this.reviewReason = reviewReason;
        this.date = date != null ? date.toString() : null;
        this.reviewDate = reviewDate != null ? reviewDate.toString() : null;
    }

    public int getId() { return id; }
    public String getUserId() { return userId; }
    public int getStrikeId() { return strikeId; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public String getReviewerId() { return reviewerId; }
    public String getReviewReason() { return reviewReason; }
    public String getDate() { return date; }
    public String getReviewDate() { return reviewDate; }
}