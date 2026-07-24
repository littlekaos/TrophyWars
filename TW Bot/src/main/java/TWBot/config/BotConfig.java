package TWBot.config;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BotConfig {

    private static Dotenv dotenv;

    static {
        dotenv = loadDotenv();
    }

    public static final String MEMBER_ROLE_ID = "1099474082422063145";
    public static final String WELCOME_CHANNEL_ID = "1304664994205667411";
    public static final String GENERAL_CHAT_CHANNEL_ID = "1304664994205667411";
    public static final String NAME_LOG_CHANNEL = "1472822017471418639";
    public static final String EVENT_NAME_CHANNEL = "1312877194753871925";
    public static final String BOT_COMMANDS_CHANNEL = "1312877194753871925";

    private static final List<String> AUTO_REACTION_CHANNELS = Arrays.asList(
            "1269416717994426528",
            "1261562170190332004",
            "1197710900426190910",
            "1099482369884434613",
            "1099698123623895102",
            "1442559445422047232",
            "1099662653196083210",
            "1099666617580916767",
            "1310054737097523220"
    );

    public static final String TW_EMOJI_NAME = "tw";
    public static final String TW_EMOJI_ID = "1312888646180798504";
    public static final String TW_EMOJI_MENTION = "<:" + TW_EMOJI_NAME + ":" + TW_EMOJI_ID + ">";

    private static final Map<String, EmojiConfig> CHANNEL_EMOJI_MAP = createChannelEmojiMap();

    private static Map<String, EmojiConfig> createChannelEmojiMap() {
        Map<String, EmojiConfig> map = new HashMap<>();

        map.put("1099482369884434613", new EmojiConfig("👍", null, null));
        map.put("1099698123623895102", new EmojiConfig(null, "tw", "1312888646180798504"));
        map.put("1442559445422047232", new EmojiConfig(null, "tw", "1312888646180798504"));
        map.put("1099662653196083210", new EmojiConfig(null, "tw", "1312888646180798504"));
        map.put("1099666617580916767", new EmojiConfig(null, "JSE", "839166137396363265"));
        map.put("1310054737097523220", new EmojiConfig(null, "ZRU", "995054086405754911"));

        map.put("1269416717994426528", new EmojiConfig(null, "tw", "1312888646180798504"));
        map.put("1261562170190332004", new EmojiConfig(null, "tw", "1312888646180798504"));
        map.put("1197710900426190910", new EmojiConfig(null, "tw", "1312888646180798504"));

        return map;
    }

    public static class EmojiConfig {
        public final String unicodeEmoji;
        public final String customEmojiName;
        public final String customEmojiId;

        public EmojiConfig(String unicodeEmoji, String customEmojiName, String customEmojiId) {
            this.unicodeEmoji = unicodeEmoji;
            this.customEmojiName = customEmojiName;
            this.customEmojiId = customEmojiId;
        }
    }

    private static Dotenv loadDotenv() {
        String envFilePath = System.getenv("ENV_FILE_PATH");
        if (envFilePath != null && !envFilePath.isEmpty()) {
            System.out.println("Trying ENV_FILE_PATH: " + envFilePath);
            try {
                Dotenv loaded = Dotenv.configure()
                        .filename(envFilePath)
                        .ignoreIfMissing()
                        .load();
                if (loaded.get("BOT_TOKEN") != null || loaded.get("DATABASE_PATH") != null) {
                    System.out.println("Loaded .env from ENV_FILE_PATH");
                    return loaded;
                }
            } catch (Exception e) {
                System.out.println("Error loading from ENV_FILE_PATH: " + e.getMessage());
            }
        }

        List<String> directories = new ArrayList<>();
        directories.add(".");
        directories.add("..");
        directories.add("../");
        directories.add("../../");
        directories.add("/");
        directories.add("/home");
        directories.add("/home/container");
        directories.add("/app");
        directories.add("/bot");
        directories.add("/srv");
        directories.add(System.getProperty("user.home"));
        directories.add(System.getProperty("user.dir"));
        directories.add(System.getProperty("java.io.tmpdir"));
        directories.add("./src/main/java/TWBot");
        directories.add("./src/main/java/twbot");
        directories.add("./src/main/resources");
        directories.add("./TW Bot");
        directories.add("TW Bot");

        System.out.println("Searching for .env file in " + directories.size() + " directories...");
        
        for (String dir : directories) {
            try {
                System.out.println("  Checking: " + dir);
                Dotenv loaded = Dotenv.configure()
                        .directory(dir)
                        .ignoreIfMissing()
                        .load();

                if (loaded.get("BOT_TOKEN") != null || loaded.get("DATABASE_PATH") != null) {
                    System.out.println("Found .env file at: " + dir);
                    return loaded;
                }
            } catch (Exception e) {
                System.out.println("    Error: " + e.getMessage());
            }
        }

        System.out.println("WARNING: Could not find .env file in any directory!");
        return Dotenv.configure().ignoreIfMissing().load();
    }

    public static final String GUILD_ID = "1304664994205667408";
    public static final String OWNER_USER_ID = "529480987525251082";
    /** Optional fixed snowflake for the owner admin role; blank = resolve/create by name. */
    public static final String OWNER_ADMIN_ROLE_ID = "";
    public static final String OWNER_ADMIN_ROLE_NAME = "Bot Owner";

    // Roles
    public static final String SERVER_OWNERSHIP_ROLE_ID = "1426617224730247278";
    public static final String BRYCES_CUTE_ROLE_ID = "1412165735681228850";
    public static final String MANAGER_ROLE_ID = "1312875561412198410";
    public static final String CO_MANAGER_ROLE_ID = "1312890262879604828";
    public static final String OVERSEER_ROLE_ID = "1312875614054907906";
    public static final String OCEANIA_LEAD_ROLE_ID = "1455622410647638159";
    public static final String ASIA_LEAD_ROLE_ID = "1455620705726238742";
    public static final String MODERATOR_ROLE_ID = "1316100289358725180";
    public static final String STAFF_ROLE_ID = "1316026821581471784";
    public static final String NEWS_REPORTER_ROLE_ID = "1461839040990023916";
    public static final String MUTE_ROLE_ID = "1472808871524565115";

    public static final String FAMILY_FRIENDS_ROLE_ID = "1313992497667510322";
    public static final String EVENT_HOST_ROLE_ID = "1312883000895078492";
    public static final String CANVA_TEAM_ROLE_ID = "1313111702161723435";
    public static final String EVENT_ANNOUNCEMENT_TEAM_ROLE_ID = "1312910690633121813";

    // Channels
    public static final String MOD_LOG_CHANNEL_ID = "1468368657162702969"; // tw-moderation-logs
    /** Server activity logs (joins, edits, roles, voice, etc). Resolve by ID or name fallback. */
    public static final String SERVER_LOG_CHANNEL_ID = "1468368658589024450";
    public static final String STAFF_NOTIFICATION_CHANNEL_ID = "1455394393598071037";
    public static final String MANAGER_CHAT_CHANNEL_ID = "1312885935020441682";
    public static final String ADMIN_CHAT_CHANNEL_ID = "1312885935020441682";
    public static final String STAFF_CHAT_CHANNEL_ID = "1313111417406226513";
    public static final String STAFF_STRIKES_CHANNEL_ID = "1472814682245955627"; // staff-strikes
    public static final String STAFF_STRIKE_LOG_CHANNEL_ID = "1474429991365116054"; // staff-strikes-logging

    // Message IDs
    public static final String DEMOTION_LIST_MESSAGE_ID = "1474509068688425196";
    public static final String VERIFICATION_REACTION_ROLES_MESSAGE_ID = "1466274041563447430";

    public static final int TEMP_DEMOTION_DAYS = 2;

    private static final List<String> MOD_ROLES = Arrays.asList(
            SERVER_OWNERSHIP_ROLE_ID,
            BRYCES_CUTE_ROLE_ID,
            MANAGER_ROLE_ID,
            CO_MANAGER_ROLE_ID,
            OVERSEER_ROLE_ID,
            MODERATOR_ROLE_ID,
            STAFF_ROLE_ID,
            NEWS_REPORTER_ROLE_ID
    );

    private static final List<String> ADMIN_ROLES = Arrays.asList(
            SERVER_OWNERSHIP_ROLE_ID,
            BRYCES_CUTE_ROLE_ID,
            MANAGER_ROLE_ID,
            CO_MANAGER_ROLE_ID,
            OVERSEER_ROLE_ID
    );

    public static final List<String> MANAGEMENT_ROLE_IDS = Arrays.asList(
            SERVER_OWNERSHIP_ROLE_ID,
            BRYCES_CUTE_ROLE_ID,
            MANAGER_ROLE_ID,
            CO_MANAGER_ROLE_ID,
            OVERSEER_ROLE_ID
    );

    public static final List<String> STAFF_ROLE_IDS = Arrays.asList(
            CO_MANAGER_ROLE_ID,
            OVERSEER_ROLE_ID,
            OCEANIA_LEAD_ROLE_ID,
            ASIA_LEAD_ROLE_ID,
            CANVA_TEAM_ROLE_ID,
            EVENT_ANNOUNCEMENT_TEAM_ROLE_ID,
            STAFF_ROLE_ID,
            EVENT_HOST_ROLE_ID,
            NEWS_REPORTER_ROLE_ID,
            FAMILY_FRIENDS_ROLE_ID
    );

    public static final List<String> PROTECTED_ROLE_IDS = Arrays.asList(
            SERVER_OWNERSHIP_ROLE_ID,
            BRYCES_CUTE_ROLE_ID,
            MANAGER_ROLE_ID,
            CO_MANAGER_ROLE_ID,
            OVERSEER_ROLE_ID
    );

    public static final List<String> SUPPORT_ROLE_IDS = Arrays.asList(
            FAMILY_FRIENDS_ROLE_ID
    );

    public static boolean isStaffOrModRole(String roleId) {
        return STAFF_ROLE_IDS.contains(roleId) 
            || MOD_ROLES.contains(roleId) 
            || ADMIN_ROLES.contains(roleId) 
            || MANAGEMENT_ROLE_IDS.contains(roleId);
    }

    public static boolean isSupportRole(String roleId) {
        return SUPPORT_ROLE_IDS.contains(roleId);
    }

    private static final String STAFF_STRIKES_ROLE_ID = EVENT_HOST_ROLE_ID;

    private String loadFromEnvFile(String key) {
        if (dotenv != null) {
            String value = dotenv.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public String getToken() {
        String token = System.getenv("BOT_TOKEN");
        if (token == null || token.isEmpty()) {
            token = System.getenv("TOKEN");
        }

        if (token != null && !token.isEmpty()) {
            System.out.println("Found token in environment variables");
            return token;
        }

        System.out.println("Working Directory = " + System.getProperty("user.dir"));

        token = loadFromEnvFile("BOT_TOKEN");
        if (token != null && !token.isEmpty()) {
            System.out.println("Found token in .env file");
            return token;
        }

        return token;
    }

    public String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        value = loadFromEnvFile(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        return defaultValue;
    }

    public String getGuildId() {
        return getEnvOrDefault("GUILD_ID", GUILD_ID);
    }

    public String getStatusText() {
        return getEnvOrDefault("BOT_STATUS", "🌍 Watching TW!");
    }

    public String getStatusUrl() {
        return getEnvOrDefault("BOT_STATUS_URL", "https://www.twitch.tv/mrjawesomeyt");
    }

    public OnlineStatus getOnlineStatus() {
        String statusTypeStr = getEnvOrDefault("BOT_ONLINE_STATUS", "ONLINE");
        return OnlineStatus.valueOf(statusTypeStr);
    }

    public String getMemberRoleId() {
        return MEMBER_ROLE_ID;
    }

    public String getWelcomeChannelId() {
        return WELCOME_CHANNEL_ID;
    }

    public String getGeneralChatChannelId() {
        return GENERAL_CHAT_CHANNEL_ID;
    }

    public String getNameLogChannelId() {
        return getEnvOrDefault("NAME_LOG_CHANNEL_ID", NAME_LOG_CHANNEL);
    }

    public String getEventNameChannelId() {
        return getEnvOrDefault("EVENT_NAME_CHANNEL_ID", EVENT_NAME_CHANNEL);
    }

    public String getBotCommandsChannelId() {
        return getEnvOrDefault("BOT_COMMANDS_CHANNEL_ID", BOT_COMMANDS_CHANNEL);
    }

    public String getModLogChannelId() {
        return MOD_LOG_CHANNEL_ID;
    }

    public List<String> getAutoReactionChannels() {
        return AUTO_REACTION_CHANNELS;
    }

    public String getTwEmojiName() {
        return TW_EMOJI_NAME;
    }

    public String getTwEmojiId() {
        return TW_EMOJI_ID;
    }

    public List<String> getModRoles() {
        return MOD_ROLES;
    }

    public List<String> getAdminRoles() {
        return ADMIN_ROLES;
    }

    public List<String> getLeadRoles() {
        return Arrays.asList(OCEANIA_LEAD_ROLE_ID, ASIA_LEAD_ROLE_ID);
    }

    public String getStaffStrikesRoleId() {
        return STAFF_STRIKES_ROLE_ID;
    }

    public EmojiConfig getChannelEmojiConfig(String channelId) {
        return CHANNEL_EMOJI_MAP.get(channelId);
    }
}