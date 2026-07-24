package TWBot.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserCache {

    private final Map<String, UserDetails> userDetailsCache = new ConcurrentHashMap<>(1000);
    private JDA jda;

    public static class UserDetails {
        public final String id;
        public final String tag;
        public final String mention;

        public UserDetails(User user) {
            this.id = user.getId();
            this.tag = user.getName();
            this.mention = user.getAsMention();
        }
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    public void cacheUser(User user) {
        if (user == null) return;
        userDetailsCache.put(user.getId(), new UserDetails(user));
    }

    public User retrieveUser(String userId) {
        try {
            if (jda == null) {
                return null;
            }

            User user = jda.getUserById(userId);

            if (user != null) {
                cacheUser(user);
                return user;
            }

            for (Guild guild : jda.getGuilds()) {
                try {
                    net.dv8tion.jda.api.entities.Member member = guild.getMemberById(userId);
                    if (member != null) {
                        user = member.getUser();
                        cacheUser(user);
                        return user;
                    }
                } catch (Exception ignored) {}
            }

            try {
                user = jda.retrieveUserById(userId).complete();
                if (user != null) {
                    cacheUser(user);
                    return user;
                }
            } catch (Exception ignored) {}

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getUserInfo(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "Unknown User";
        }

        UserDetails details = userDetailsCache.get(userId);
        if (details != null) {
            return details.tag + " (ID: " + details.id + ")";
        }

        User user = retrieveUser(userId);
        if (user != null) {
            return user.getName() + " (ID: " + user.getId() + ")";
        }

        return "User ID: " + userId;
    }

    public String getPlainUserInfo(String userId) {
        return getUserInfo(userId);
    }

    /** Non-blocking: never hits Discord REST from the event thread. */
    public String getCachedUserInfo(String userId) {
        if (userId == null || userId.isEmpty()) return "Unknown user";
        UserDetails details = userDetailsCache.get(userId);
        if (details != null) return details.tag + " (ID: " + details.id + ")";
        if (jda != null) {
            User user = jda.getUserById(userId);
            if (user != null) {
                cacheUser(user);
                return user.getName() + " (ID: " + user.getId() + ")";
            }
        }
        return "User ID: " + userId;
    }

    public String[] getUserDisplayInfo(String userId) {
        if (userId == null || userId.isEmpty()) {
            return new String[]{"Unknown User", "Unknown"};
        }

        User user = retrieveUser(userId);
        if (user != null) {
            return new String[]{user.getName(), user.getId()};
        }

        UserDetails details = userDetailsCache.get(userId);
        if (details != null) {
            return new String[]{details.tag, details.id};
        }

        return new String[]{"Unknown", userId};
    }

    public String getMention(String userId) {
        UserDetails details = userDetailsCache.get(userId);
        if (details != null) {
            return details.mention;
        }

        User user = retrieveUser(userId);
        if (user != null) {
            return user.getAsMention();
        }

        return "<@" + userId + ">";
    }

    public int getCacheSize() {
        return userDetailsCache.size();
    }

    public UserDetails getUserDetails(String userId) {
        return userDetailsCache.get(userId);
    }
}
