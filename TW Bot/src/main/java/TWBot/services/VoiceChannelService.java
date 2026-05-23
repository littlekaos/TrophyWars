package TWBot.services;

import TWBot.database.DatabaseManager;
import TWBot.models.VoiceChannelRecord;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VoiceChannelService {
    private final DatabaseManager dbManager;
    private static boolean initialized = false;

    public VoiceChannelService() {
        this.dbManager = DatabaseManager.getInstance();
        initializeTables();
    }

    private void initializeTables() {
        if (initialized) return;

        try (Connection conn = dbManager.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // Voice Channels Table
                String createVoiceChannelsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_voice_channels (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        channel_id TEXT NOT NULL,
                        channel_name TEXT NOT NULL,
                        creator_id TEXT NOT NULL,
                        creator_name TEXT NOT NULL,
                        guild_id TEXT NOT NULL,
                        guild_name TEXT,
                        category_id TEXT,
                        user_limit INTEGER DEFAULT 0,
                        channel_type TEXT DEFAULT 'CUSTOM',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        deleted_at TIMESTAMP,
                        is_active BOOLEAN DEFAULT 1
                    )
                    """;
                stmt.execute(createVoiceChannelsTableSQL);

                // Voice Activity Table
                String createVoiceActivityTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_voice_activity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        channel_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        user_name TEXT NOT NULL,
                        guild_id TEXT NOT NULL,
                        action_type TEXT NOT NULL,
                        joined_at TIMESTAMP,
                        left_at TIMESTAMP,
                        duration_seconds INTEGER,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
                stmt.execute(createVoiceActivityTableSQL);

                // Voice Metadata Table
                String createVoiceMetadataTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_voice_metadata (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
                stmt.execute(createVoiceMetadataTableSQL);

                // User Voice Stats Table
                String createUserVoiceStatsTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_user_voice_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id TEXT NOT NULL,
                        user_name TEXT NOT NULL,
                        guild_id TEXT NOT NULL,
                        total_channels_created INTEGER DEFAULT 0,
                        total_voice_time_seconds BIGINT DEFAULT 0,
                        last_channel_created TIMESTAMP,
                        last_voice_activity TIMESTAMP,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(user_id, guild_id)
                    )
                    """;
                stmt.execute(createUserVoiceStatsTableSQL);

                // Server Setup Table
                String createServerSetupTableSQL = """
                    CREATE TABLE IF NOT EXISTS tw_voice_server_setup (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        guild_id TEXT NOT NULL UNIQUE,
                        category_id TEXT NOT NULL,
                        managed_vc_ids TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
                stmt.execute(createServerSetupTableSQL);

                initialized = true;
                System.out.println("Voice channel tables initialized successfully");
            }
        } catch (SQLException e) {
            System.err.println("Error initializing voice channel tables: " + e.getMessage());
        }
    }

    public void logChannelCreation(VoiceChannel channel, User creator, String channelType) {
        String sql = """
            INSERT INTO tw_voice_channels 
            (channel_id, channel_name, creator_id, creator_name, guild_id, guild_name, category_id, user_limit, channel_type) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, channel.getId());
            pstmt.setString(2, channel.getName());
            pstmt.setString(3, creator.getId());
            pstmt.setString(4, creator.getName());
            pstmt.setString(5, channel.getGuild().getId());
            pstmt.setString(6, channel.getGuild().getName());
            pstmt.setString(7, channel.getParentCategory() != null ? channel.getParentCategory().getId() : null);
            pstmt.setInt(8, channel.getUserLimit());
            pstmt.setString(9, channelType);
            pstmt.executeUpdate();

            updateUserChannelStats(creator.getId(), creator.getName(), channel.getGuild().getId());
        } catch (SQLException e) {
            System.err.println("Error logging voice channel creation: " + e.getMessage());
        }
    }

    private void updateUserChannelStats(String userId, String userName, String guildId) {
        String sql = """
            INSERT INTO tw_user_voice_stats (user_id, user_name, guild_id, total_channels_created, last_channel_created) 
            VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP) 
            ON CONFLICT (user_id, guild_id) 
            DO UPDATE SET 
            total_channels_created = total_channels_created + 1, 
            last_channel_created = CURRENT_TIMESTAMP, 
            updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, userName);
            pstmt.setString(3, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating user channel stats: " + e.getMessage());
        }
    }

    public void markChannelDeleted(String channelId) {
        String sql = "UPDATE tw_voice_channels SET deleted_at = CURRENT_TIMESTAMP, is_active = 0 WHERE channel_id = ? AND is_active = 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, channelId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error marking voice channel as deleted: " + e.getMessage());
        }
    }

    public void logUserJoinVoice(VoiceChannel channel, Member member) {
        logVoiceJoin(channel.getId(), member.getUser().getId(), member.getEffectiveName(), channel.getGuild().getId());
    }

    public void logUserLeaveVoice(VoiceChannel channel, Member member) {
        logVoiceLeave(channel.getId(), member.getUser().getId(), member.getEffectiveName(), channel.getGuild().getId());
    }

    public void logVoiceJoin(String channelId, String userId, String userName, String guildId) {
        String sql = "INSERT INTO tw_voice_activity (channel_id, user_id, user_name, guild_id, action_type, joined_at) VALUES (?, ?, ?, ?, 'JOIN', CURRENT_TIMESTAMP)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, channelId);
            pstmt.setString(2, userId);
            pstmt.setString(3, userName);
            pstmt.setString(4, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error logging voice join: " + e.getMessage());
        }
    }

    public void logVoiceLeave(String channelId, String userId, String userName, String guildId) {
        try (Connection conn = dbManager.getConnection()) {
            String selectSql = "SELECT id, joined_at FROM tw_voice_activity WHERE channel_id = ? AND user_id = ? AND action_type = 'JOIN' AND left_at IS NULL ORDER BY joined_at DESC LIMIT 1";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, channelId);
                selectStmt.setString(2, userId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        int recordId = rs.getInt("id");
                        Timestamp joinedAt = rs.getTimestamp("joined_at");
                        long now = System.currentTimeMillis();
                        long durationSeconds = (now - joinedAt.getTime()) / 1000;

                        String updateSql = "UPDATE tw_voice_activity SET left_at = CURRENT_TIMESTAMP, duration_seconds = ? WHERE id = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setLong(1, durationSeconds);
                            updateStmt.setInt(2, recordId);
                            updateStmt.executeUpdate();
                        }

                        updateUserVoiceTime(userId, userName, guildId, durationSeconds);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error logging voice leave: " + e.getMessage());
        }
    }

    private void updateUserVoiceTime(String userId, String userName, String guildId, long durationSeconds) {
        String sql = """
            INSERT INTO tw_user_voice_stats (user_id, user_name, guild_id, total_voice_time_seconds, last_voice_activity) 
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) 
            ON CONFLICT (user_id, guild_id) 
            DO UPDATE SET 
            total_voice_time_seconds = total_voice_time_seconds + EXCLUDED.total_voice_time_seconds, 
            last_voice_activity = CURRENT_TIMESTAMP, 
            updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, userName);
            pstmt.setString(3, guildId);
            pstmt.setLong(4, durationSeconds);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating user voice time: " + e.getMessage());
        }
    }

    public String getCategoryId(String guildId) {
        String sql = "SELECT category_id FROM tw_voice_server_setup WHERE guild_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("category_id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting category ID: " + e.getMessage());
        }
        return null;
    }

    public void saveServerSetup(String guildId, String categoryId, String managedVcIds) {
        String sql = """
            INSERT INTO tw_voice_server_setup (guild_id, category_id, managed_vc_ids) VALUES (?, ?, ?) 
            ON CONFLICT (guild_id) DO UPDATE SET category_id = EXCLUDED.category_id, 
            managed_vc_ids = EXCLUDED.managed_vc_ids, updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, categoryId);
            pstmt.setString(3, managedVcIds);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving server setup: " + e.getMessage());
        }
    }

    public boolean hasServerSetup(String guildId) {
        String sql = "SELECT 1 FROM tw_voice_server_setup WHERE guild_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking server setup: " + e.getMessage());
        }
        return false;
    }

    public String getManagedVcIds(String guildId) {
        String sql = "SELECT managed_vc_ids FROM tw_voice_server_setup WHERE guild_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("managed_vc_ids");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting managed VC IDs: " + e.getMessage());
        }
        return null;
    }

    public List<VoiceChannelRecord> getActiveChannels(String guildId) {
        List<VoiceChannelRecord> channels = new ArrayList<>();
        String sql = "SELECT * FROM tw_voice_channels WHERE guild_id = ? AND is_active = true ORDER BY created_at DESC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    channels.add(new VoiceChannelRecord(
                            rs.getInt("id"),
                            rs.getString("channel_id"),
                            rs.getString("channel_name"),
                            rs.getString("creator_id"),
                            rs.getString("creator_name"),
                            rs.getString("guild_id"),
                            rs.getString("guild_name"),
                            rs.getString("category_id"),
                            rs.getInt("user_limit"),
                            rs.getString("channel_type"),
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("deleted_at"),
                            rs.getBoolean("is_active")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting active channels: " + e.getMessage());
        }
        return channels;
    }

    public List<VoiceChannelRecord> getUserCreatedChannels(String userId, String guildId) {
        List<VoiceChannelRecord> channels = new ArrayList<>();
        String sql = "SELECT * FROM tw_voice_channels WHERE creator_id = ? AND guild_id = ? ORDER BY created_at DESC LIMIT 10";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    channels.add(new VoiceChannelRecord(
                            rs.getInt("id"),
                            rs.getString("channel_id"),
                            rs.getString("channel_name"),
                            rs.getString("creator_id"),
                            rs.getString("creator_name"),
                            rs.getString("guild_id"),
                            rs.getString("guild_name"),
                            rs.getString("category_id"),
                            rs.getInt("user_limit"),
                            rs.getString("channel_type"),
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("deleted_at"),
                            rs.getBoolean("is_active")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user created channels: " + e.getMessage());
        }
        return channels;
    }

    public int getTotalChannelsCreated() {
        String sql = "SELECT COUNT(*) as count FROM tw_voice_channels";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Error getting total channel count: " + e.getMessage());
        }
        return 0;
    }

    public int getActiveChannelCount(String guildId) {
        String sql = "SELECT COUNT(*) as count FROM tw_voice_channels WHERE guild_id = ? AND is_active = true";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guildId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting active channel count: " + e.getMessage());
        }
        return 0;
    }
}
