package TWBot.services;

import TWBot.database.DatabaseManager;
import TWBot.models.ModAction;
import TWBot.models.WarnRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataService {
    private static boolean initialized = false;

    public DataService() {
        initializeTables();
    }

    private void initializeTables() {
        if (initialized) return;
        
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // Moderation Analytics Table
                String createAnalyticsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_moderation_analytics (
                        id SERIAL PRIMARY KEY,
                        action VARCHAR(32) NOT NULL,
                        moderatorId VARCHAR(32),
                        moderatorName VARCHAR(255),
                        targetId VARCHAR(32) NOT NULL,
                        targetName VARCHAR(255),
                        reason TEXT,
                        timestamp BIGINT,
                        duration INT,
                        count INT,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
                stmt.execute(createAnalyticsTableSQL);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tw_moderation_targetId ON tw_moderation_analytics(targetId)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tw_moderation_action ON tw_moderation_analytics(action)");

                // Warnings Table
                String createWarningsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_warnings (
                        id SERIAL PRIMARY KEY,
                        userId VARCHAR(32) NOT NULL,
                        moderatorId VARCHAR(32) NOT NULL,
                        reason TEXT,
                        timestamp BIGINT NOT NULL
                    )
                    """;
                stmt.execute(createWarningsTableSQL);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_tw_warnings_userId ON tw_warnings(userId)");

                // Mute Config Table
                String createMuteConfigTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_mute_config (
                        guildId VARCHAR(32) PRIMARY KEY,
                        muteRoleId VARCHAR(32) NOT NULL
                    )
                    """;
                stmt.execute(createMuteConfigTableSQL);

                // Active Mutes Table
                String createActiveMutesTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_active_mutes (
                        guildId VARCHAR(32),
                        userId VARCHAR(32),
                        unmuteTime BIGINT,
                        PRIMARY KEY (guildId, userId)
                    )
                    """;
                stmt.execute(createActiveMutesTableSQL);

                // Channel Restrictions Table
                String createRestrictionsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_channel_restrictions (
                        channelId VARCHAR(32),
                        restrictionType VARCHAR(100),
                        PRIMARY KEY (channelId, restrictionType)
                    )
                    """;
                stmt.execute(createRestrictionsTableSQL);

                // Message Logs Table
                String createMessageLogsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_message_logs (
                        id SERIAL PRIMARY KEY,
                        guildId VARCHAR(32),
                        channelId VARCHAR(32),
                        messageId VARCHAR(32),
                        userId VARCHAR(32),
                        content TEXT,
                        action TEXT,
                        timestamp BIGINT
                    )
                    """;
                stmt.execute(createMessageLogsTableSQL);

                // General Logs Table
                String createGeneralLogsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_general_logs (
                        id SERIAL PRIMARY KEY,
                        guildId VARCHAR(32),
                        userId VARCHAR(32),
                        eventType TEXT,
                        details TEXT,
                        timestamp BIGINT
                    )
                    """;
                stmt.execute(createGeneralLogsTableSQL);

                // Scheduled Pings Table
                String createScheduledPingsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_scheduled_pings (
                        pingKey VARCHAR(64) PRIMARY KEY,
                        lastPingTimestamp BIGINT
                    )
                    """;
                stmt.execute(createScheduledPingsTableSQL);

                // Command Permissions Table
                String createCommandPermissionsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_command_permissions (
                        commandName VARCHAR(64),
                        roleId VARCHAR(32),
                        enabled BOOLEAN,
                        PRIMARY KEY (commandName, roleId)
                    )
                    """;
                stmt.execute(createCommandPermissionsTableSQL);

                // Dedupe archived Discord messages by messageId
                try {
                    stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_tw_message_logs_messageId ON tw_message_logs(messageId)");
                } catch (Exception ignored) {}

                // Apply migrations for existing tables
                try {
                    stmt.execute("ALTER TABLE tw_message_logs ALTER COLUMN action TYPE TEXT");
                    stmt.execute("ALTER TABLE tw_general_logs ALTER COLUMN eventType TYPE TEXT");
                    stmt.execute("ALTER TABLE tw_channel_restrictions ALTER COLUMN restrictionType TYPE VARCHAR(100)");
                    
                    // Expand ID columns in all tables
                    stmt.execute("ALTER TABLE tw_message_logs ALTER COLUMN guildId TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_message_logs ALTER COLUMN channelId TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_message_logs ALTER COLUMN messageId TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_message_logs ALTER COLUMN userId TYPE VARCHAR(32)");
                    
                    stmt.execute("ALTER TABLE tw_general_logs ALTER COLUMN guildId TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_general_logs ALTER COLUMN userId TYPE VARCHAR(32)");
                    
                    stmt.execute("ALTER TABLE tw_moderation_analytics ALTER COLUMN action TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_moderation_analytics ALTER COLUMN moderatorId TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_moderation_analytics ALTER COLUMN targetId TYPE VARCHAR(32)");
                    
                    stmt.execute("ALTER TABLE tw_warnings ALTER COLUMN userId TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_warnings ALTER COLUMN moderatorId TYPE VARCHAR(32)");
                    
                    stmt.execute("ALTER TABLE tw_mute_config ALTER COLUMN guildId TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_mute_config ALTER COLUMN muteRoleId TYPE VARCHAR(32)");
                    
                    stmt.execute("ALTER TABLE tw_active_mutes ALTER COLUMN guildId TYPE VARCHAR(32)");
                    stmt.execute("ALTER TABLE tw_active_mutes ALTER COLUMN userId TYPE VARCHAR(32)");
                    
                    stmt.execute("ALTER TABLE tw_channel_restrictions ALTER COLUMN channelId TYPE VARCHAR(32)");
                } catch (Exception e) {
                    // Ignore errors if columns are already the correct type or don't exist
                }

                initialized = true;
                System.out.println("All moderation tables initialized successfully");
            }
        } catch (Exception e) {
            System.err.println("Error initializing moderation tables: " + e.getMessage());
        }
    }

    public void saveModAction(ModAction.ActionType action, String moderatorId, String moderatorName, String targetId, String targetName, String reason, int duration, int count) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO tw_moderation_analytics (action, moderatorId, moderatorName, targetId, targetName, reason, timestamp, duration, count) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, action.name());
                pstmt.setString(2, moderatorId);
                pstmt.setString(3, moderatorName);
                pstmt.setString(4, targetId);
                pstmt.setString(5, targetName);
                pstmt.setString(6, reason);
                pstmt.setLong(7, System.currentTimeMillis());
                pstmt.setInt(8, duration);
                pstmt.setInt(9, count);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error saving moderation action: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveBanAction(String moderatorId, String moderatorName, String targetId, String targetName, String reason) {
        saveModAction(ModAction.ActionType.BAN, moderatorId, moderatorName, targetId, targetName, reason, 0, 0);
    }

    public void saveUnbanAction(String moderatorId, String moderatorName, String targetId) {
        saveModAction(ModAction.ActionType.UNBAN, moderatorId, moderatorName, targetId, "Unknown", "", 0, 0);
    }

    public void addWarning(WarnRecord record) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO tw_warnings (userId, moderatorId, reason, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, record.getUserId());
                pstmt.setString(2, record.getModeratorId());
                pstmt.setString(3, record.getReason());
                pstmt.setLong(4, record.getTimestamp());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error adding warning: " + e.getMessage());
        }
    }

    public List<WarnRecord> getWarningsForUser(String userId) {
        List<WarnRecord> warnings = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT userId, moderatorId, reason, timestamp FROM tw_warnings WHERE userId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        warnings.add(new WarnRecord(
                            rs.getString("userId"),
                            rs.getString("moderatorId"),
                            rs.getString("reason"),
                            rs.getLong("timestamp")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting warnings: " + e.getMessage());
        }
        return warnings;
    }

    public void setMuteRoleId(String guildId, String roleId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO tw_mute_config (guildId, muteRoleId) VALUES (?, ?) ON CONFLICT (guildId) DO UPDATE SET muteRoleId = EXCLUDED.muteRoleId";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, roleId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error setting mute role: " + e.getMessage());
        }
    }

    public String getMuteRoleId(String guildId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT muteRoleId FROM tw_mute_config WHERE guildId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("muteRoleId");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting mute role: " + e.getMessage());
        }
        return null;
    }

    public void addMute(String guildId, String userId, long unmuteTime) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO tw_active_mutes (guildId, userId, unmuteTime) VALUES (?, ?, ?) ON CONFLICT (guildId, userId) DO UPDATE SET unmuteTime = EXCLUDED.unmuteTime";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setLong(3, unmuteTime);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error adding mute: " + e.getMessage());
        }
    }

    public void removeMute(String guildId, String userId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "DELETE FROM tw_active_mutes WHERE guildId = ? AND userId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error removing mute: " + e.getMessage());
        }
    }

    public boolean isMuted(String guildId, String userId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT 1 FROM tw_active_mutes WHERE guildId = ? AND userId = ? AND (unmuteTime = 0 OR unmuteTime > ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setLong(3, System.currentTimeMillis());
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking mute status: " + e.getMessage());
        }
        return false;
    }

    public List<MuteEntry> getExpiredMutes(long currentTime) {
        List<MuteEntry> expired = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT guildId, userId FROM tw_active_mutes WHERE unmuteTime > 0 AND unmuteTime <= ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, currentTime);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        expired.add(new MuteEntry(rs.getString("guildId"), rs.getString("userId")));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting expired mutes: " + e.getMessage());
        }
        return expired;
    }

    public static record MuteEntry(String guildId, String userId) {}

    public void addChannelRestriction(String channelId, String type) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO tw_channel_restrictions (channelId, restrictionType) VALUES (?, ?) ON CONFLICT DO NOTHING";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                pstmt.setString(2, type);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error adding channel restriction: " + e.getMessage());
        }
    }

    public void removeChannelRestriction(String channelId, String type) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "DELETE FROM tw_channel_restrictions WHERE channelId = ? AND restrictionType = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                pstmt.setString(2, type);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error removing channel restriction: " + e.getMessage());
        }
    }

    public List<RestrictionEntry> getAllChannelRestrictions() {
        List<RestrictionEntry> restrictions = new ArrayList<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT channelId, restrictionType FROM tw_channel_restrictions";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        restrictions.add(new RestrictionEntry(
                            rs.getString("channelId"),
                            rs.getString("restrictionType")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting all channel restrictions: " + e.getMessage());
        }
        return restrictions;
    }

    public void logMessage(String guildId, String channelId, String messageId, String userId, String content, String action) {
        archiveMessage(guildId, channelId, messageId, userId, content, action, System.currentTimeMillis());
    }

    /**
     * Archives a Discord message into tw_message_logs. Duplicate messageIds are ignored.
     */
    public void archiveMessage(String guildId, String channelId, String messageId, String userId,
                               String content, String action, long timestamp) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT OR IGNORE INTO tw_message_logs (guildId, channelId, messageId, userId, content, action, timestamp) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, channelId);
                pstmt.setString(3, messageId);
                pstmt.setString(4, userId);
                pstmt.setString(5, content);
                pstmt.setString(6, action);
                pstmt.setLong(7, timestamp);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Failed to archive message to DB: " + e.getMessage());
        }
    }

    public boolean hasMessageArchived(String messageId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT 1 FROM tw_message_logs WHERE messageId = ? LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, messageId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to check archived message: " + e.getMessage());
        }
        return false;
    }

    public long getLatestArchivedTimestamp(String channelId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT MAX(timestamp) AS latest FROM tw_message_logs WHERE channelId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("latest");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get latest archived timestamp: " + e.getMessage());
        }
        return 0;
    }

    public int countArchivedMessages(String channelId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT COUNT(*) AS total FROM tw_message_logs WHERE channelId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, channelId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("total");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to count archived messages: " + e.getMessage());
        }
        return 0;
    }

    public void logGeneral(String guildId, String userId, String eventType, String details) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO tw_general_logs (guildId, userId, eventType, details, timestamp) " +
                       "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                pstmt.setString(3, eventType);
                pstmt.setString(4, details);
                pstmt.setLong(5, System.currentTimeMillis());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Failed to log general event to DB: " + e.getMessage());
        }
    }

    public static record RestrictionEntry(String channelId, String type) {}

    public List<ModAction> getBanUnbanHistory(String targetUserId) {
        return getHistory(targetUserId, List.of("BAN", "UNBAN"));
    }

    public List<ModAction> getHistory(String targetUserId, List<String> actions) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<ModAction> history = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT action, moderatorId, moderatorName, targetId, targetName, reason, timestamp, duration, count FROM tw_moderation_analytics WHERE targetId = ?");
            
            if (!actions.isEmpty()) {
                sql.append(" AND action IN (");
                for (int i = 0; i < actions.size(); i++) {
                    sql.append("?");
                    if (i < actions.size() - 1) sql.append(", ");
                }
                sql.append(")");
            }
            sql.append(" ORDER BY timestamp DESC");

            try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                pstmt.setString(1, targetUserId);
                for (int i = 0; i < actions.size(); i++) {
                    pstmt.setString(i + 2, actions.get(i));
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        history.add(new ModAction(
                                ModAction.ActionType.valueOf(rs.getString("action")),
                                rs.getString("moderatorId"),
                                rs.getString("moderatorName"),
                                rs.getString("targetId"),
                                rs.getString("targetName"),
                                rs.getString("reason"),
                                rs.getLong("timestamp"),
                                rs.getInt("duration"),
                                rs.getInt("count")
                        ));
                    }
                }
            }
            return history;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public long getLastPingTimestamp(String pingKey) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT lastPingTimestamp FROM tw_scheduled_pings WHERE pingKey = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, pingKey);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("lastPingTimestamp");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting last ping timestamp: " + e.getMessage());
        }
        return 0;
    }

    public void updateLastPingTimestamp(String pingKey, long timestamp) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO tw_scheduled_pings (pingKey, lastPingTimestamp) VALUES (?, ?) ON CONFLICT (pingKey) DO UPDATE SET lastPingTimestamp = EXCLUDED.lastPingTimestamp";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, pingKey);
                pstmt.setLong(2, timestamp);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error updating last ping timestamp: " + e.getMessage());
        }
    }

    public void setCommandPermission(String commandName, String roleId, boolean enabled) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO tw_command_permissions (commandName, roleId, enabled) VALUES (?, ?, ?) " +
                         "ON CONFLICT (commandName, roleId) DO UPDATE SET enabled = EXCLUDED.enabled";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, commandName);
                pstmt.setString(2, roleId);
                pstmt.setBoolean(3, enabled);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error setting command permission: " + e.getMessage());
        }
    }

    public void removeCommandPermission(String commandName, String roleId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "DELETE FROM tw_command_permissions WHERE commandName = ? AND roleId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, commandName);
                pstmt.setString(2, roleId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error removing command permission: " + e.getMessage());
        }
    }

    public Map<String, Boolean> getPermissionsForCommand(String commandName) {
        Map<String, Boolean> permissions = new HashMap<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT roleId, enabled FROM tw_command_permissions WHERE commandName = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, commandName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        permissions.put(rs.getString("roleId"), rs.getBoolean("enabled"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting command permissions: " + e.getMessage());
        }
        return permissions;
    }

    public Map<String, Map<String, Boolean>> getAllCommandPermissions() {
        Map<String, Map<String, Boolean>> allPermissions = new HashMap<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT commandName, roleId, enabled FROM tw_command_permissions";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String cmd = rs.getString("commandName");
                        String role = rs.getString("roleId");
                        boolean enabled = rs.getBoolean("enabled");
                        allPermissions.computeIfAbsent(cmd, k -> new HashMap<>()).put(role, enabled);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting all command permissions: " + e.getMessage());
        }
        return allPermissions;
    }
}
