package TWBot.models;

public class UserData {
    private final String userName;
    private final String displayName;
    private final String nickname;
    private final String eventName;
    private final String userId;

    public UserData(String userName, String displayName, String nickname, String eventName, String userId) {
        this.userName = userName;
        this.displayName = displayName;
        this.nickname = nickname;
        this.eventName = eventName;
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEventName() {
        return eventName;
    }

    public String getUserId() {
        return userId;
    }
}