package TWBot.models;

import java.sql.Timestamp;

public class VoiceChannelRecord {
    private int id;
    private String channelId;
    private String channelName;
    private String creatorId;
    private String creatorName;
    private String guildId;
    private String guildName;
    private String categoryId;
    private int userLimit;
    private String channelType;
    private Timestamp createdAt;
    private Timestamp deletedAt;
    private boolean isActive;

    public VoiceChannelRecord(int id, String channelId, String channelName, String creatorId,
                              String creatorName, String guildId, String guildName, String categoryId,
                              int userLimit, String channelType, Timestamp createdAt,
                              Timestamp deletedAt, boolean isActive) {
        this.id = id;
        this.channelId = channelId;
        this.channelName = channelName;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.guildId = guildId;
        this.guildName = guildName;
        this.categoryId = categoryId;
        this.userLimit = userLimit;
        this.channelType = channelType;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
        this.isActive = isActive;
    }

    // Getters
    public int getId() { return id; }
    public String getChannelId() { return channelId; }
    public String getChannelName() { return channelName; }
    public String getCreatorId() { return creatorId; }
    public String getCreatorName() { return creatorName; }
    public String getGuildId() { return guildId; }
    public String getGuildName() { return guildName; }
    public String getCategoryId() { return categoryId; }
    public int getUserLimit() { return userLimit; }
    public String getChannelType() { return channelType; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getDeletedAt() { return deletedAt; }
    public boolean isActive() { return isActive; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public void setGuildId(String guildId) { this.guildId = guildId; }
    public void setGuildName(String guildName) { this.guildName = guildName; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public void setUserLimit(int userLimit) { this.userLimit = userLimit; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public void setDeletedAt(Timestamp deletedAt) { this.deletedAt = deletedAt; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return "VoiceChannelRecord{" +
                "id=" + id +
                ", channelId='" + channelId + '\'' +
                ", channelName='" + channelName + '\'' +
                ", creatorName='" + creatorName + '\'' +
                ", channelType='" + channelType + '\'' +
                ", createdAt=" + createdAt +
                ", isActive=" + isActive +
                '}';
    }
}
