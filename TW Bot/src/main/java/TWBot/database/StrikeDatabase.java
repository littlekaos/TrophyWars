package TWBot.database;

import TWBot.models.Strike;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class StrikeDatabase {
    private final DatabaseManager dbManager;

    public StrikeDatabase() {
        this.dbManager = DatabaseManager.getInstance();
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            String createStrikesTable = """
                CREATE TABLE IF NOT EXISTS strikes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    moderator_id TEXT NOT NULL,
                    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;
            stmt.executeUpdate(createStrikesTable);

            String createMetadataTable = """
                CREATE TABLE IF NOT EXISTS bot_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """;
            stmt.executeUpdate(createMetadataTable);

            // If table already exists, make sure value column allows NULL
            try {
                stmt.executeUpdate("ALTER TABLE bot_metadata RENAME TO bot_metadata_old;");
                stmt.executeUpdate(createMetadataTable);
                stmt.executeUpdate("INSERT INTO bot_metadata (key, value, created_at) SELECT key, value, created_at FROM bot_metadata_old;");
                stmt.executeUpdate("DROP TABLE bot_metadata_old;");
            } catch (SQLException e) {
                // If ALTER fails it's likely already compatible or locked, ignore
            }

            String createTemporaryDemotionsTable = """
                CREATE TABLE IF NOT EXISTS temporary_demotions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL UNIQUE,
                    role_ids TEXT NOT NULL,
                    restoration_date TIMESTAMP NOT NULL
                );
                """;
            stmt.executeUpdate(createTemporaryDemotionsTable);

        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addStrike(String userId, String reason, String moderatorId, Timestamp date) {
        String sql = "INSERT INTO strikes (user_id, reason, moderator_id, date) VALUES (?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, reason);
            pstmt.setString(3, moderatorId);
            pstmt.setTimestamp(4, date);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding strike: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void clearStrikes(String userId) {
        try (Connection conn = dbManager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            if (autoCommit) {
                conn.setAutoCommit(false);
            }
            
            try {
                String deleteStrikesSQL = "DELETE FROM strikes WHERE user_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteStrikesSQL)) {
                    pstmt.setString(1, userId);
                    pstmt.executeUpdate();
                }

                // Also remove from demotions
                removeFromDemotions(userId);
                
                if (autoCommit) {
                    conn.commit();
                }
            } catch (SQLException e) {
                if (autoCommit) {
                    try {
                        if (!conn.getAutoCommit()) {
                            conn.rollback();
                        }
                    } catch (SQLException rollbackEx) {
                        e.addSuppressed(rollbackEx);
                    }
                }
                throw e;
            } finally {
                if (autoCommit) {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException setAutoEx) {
                        // Suppress or log
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error clearing strikes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void editStrike(String userId, int strikeNumber, String newReason) {
        List<Strike> strikes = getStrikes(userId);

        if (strikeNumber < 1 || strikeNumber > strikes.size()) {
            throw new IllegalArgumentException("Invalid strike number");
        }

        Strike strikeToEdit = strikes.get(strikeNumber - 1);

        String sql = "UPDATE strikes SET reason = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newReason);
            pstmt.setInt(2, strikeToEdit.getId());

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                System.err.println("Warning: No strike was updated for user " + userId);
            }

        } catch (SQLException e) {
            System.err.println("Error editing strike for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean hasBeenInitialized() {
        String sql = "SELECT value FROM bot_metadata WHERE key = 'strikes_imported'";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && "true".equals(rs.getString("value"));
        } catch (SQLException e) {
            System.err.println("Error checking initialization status: " + e.getMessage());
            return false;
        }
    }

    public void markAsInitialized() {
        String sql = "INSERT INTO bot_metadata (key, value) " +
                "VALUES ('strikes_imported', 'true') " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
            System.out.println("✅ Database marked as initialized - strikes will not be re-imported");
        } catch (SQLException e) {
            System.err.println("Error marking database as initialized: " + e.getMessage());
        }
    }

    public int getTotalStrikeCount() {
        String sql = "SELECT COUNT(*) as count FROM strikes";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Error getting total strike count: " + e.getMessage());
        }
        return 0;
    }

    public List<String> getAllUsersWithStrikes() {
        List<String> userIds = new ArrayList<>();
        String sql = "SELECT DISTINCT user_id FROM strikes";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                userIds.add(rs.getString("user_id"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all users with strikes: " + e.getMessage());
            e.printStackTrace();
        }

        return userIds;
    }

    public void setDemotionListMessageId(String messageId) {
        if (messageId == null) {
            String sql = "DELETE FROM bot_metadata WHERE key = 'demotion_list_message_id'";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("Error deleting demotion list message ID: " + e.getMessage());
            }
            return;
        }

        String sql = "INSERT INTO bot_metadata (key, value) " +
                "VALUES ('demotion_list_message_id', ?) " +
                "ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, messageId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving demotion list message ID: " + e.getMessage());
        }
    }

    public String getDemotionListMessageId() {
        String sql = "SELECT value FROM bot_metadata WHERE key = 'demotion_list_message_id'";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            System.err.println("Error getting demotion list message ID: " + e.getMessage());
        }
        return null;
    }

    public void addTemporaryDemotion(String userId, LocalDateTime restoreDate) {
        String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "temp_demotion_" + userId);
            pstmt.setString(2, restoreDate.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding temporary demotion: " + e.getMessage());
        }
    }

    public void addPermanentDemotion(String userId) {
        String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, 'permanent') ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "perm_demotion_" + userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding permanent demotion: " + e.getMessage());
        }
    }

    public void removeFromDemotions(String userId) {
        removeFromDemotions(userId, -1);
    }

    public void removeFromDemotions(String userId, int servedStrikeCount) {
        try (Connection conn = dbManager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            if (autoCommit) {
                conn.setAutoCommit(false);
            }
            try {
                // Delete from metadata
                String metadataSql = "DELETE FROM bot_metadata WHERE key = ? OR key = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(metadataSql)) {
                    pstmt.setString(1, "temp_demotion_" + userId);
                    pstmt.setString(2, "perm_demotion_" + userId);
                    pstmt.executeUpdate();
                }

                // Mark as served if strike count is provided
                if (servedStrikeCount > 0) {
                    String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, "served_temp_demotion_" + userId);
                        pstmt.setString(2, String.valueOf(servedStrikeCount));
                        pstmt.executeUpdate();
                    }
                }

                // Delete from temporary_demotions table
                String tempTableSql = "DELETE FROM temporary_demotions WHERE user_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(tempTableSql)) {
                    pstmt.setString(1, userId);
                    pstmt.executeUpdate();
                }

                if (autoCommit) {
                    conn.commit();
                }
            } catch (SQLException e) {
                if (autoCommit) {
                    try {
                        if (!conn.getAutoCommit()) {
                            conn.rollback();
                        }
                    } catch (SQLException rollbackEx) {
                        e.addSuppressed(rollbackEx);
                    }
                }
                throw e;
            } finally {
                if (autoCommit) {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException setAutoEx) {
                        // Suppress or log
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error removing from demotions: " + e.getMessage());
        }
    }

    public Map<String, LocalDateTime> loadTemporaryDemotions() {
        Map<String, LocalDateTime> tempDemotions = new HashMap<>();
        String sql = "SELECT user_id, restoration_date FROM temporary_demotions";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String userId = rs.getString("user_id");
                Timestamp ts = rs.getTimestamp("restoration_date");
                LocalDateTime restoreDate = ts.toInstant().atZone(java.time.ZoneId.of("America/New_York")).toLocalDateTime();
                tempDemotions.put(userId, restoreDate);
            }
        } catch (SQLException e) {
            System.err.println("Error loading temporary demotions: " + e.getMessage());
        }
        return tempDemotions;
    }

    public Set<String> loadPermanentDemotions() {
        Set<String> permDemotions = new HashSet<>();
        String sql = "SELECT key FROM bot_metadata WHERE key LIKE 'perm_demotion_%'";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String key = rs.getString("key");
                String userId = key.replace("perm_demotion_", "");
                permDemotions.add(userId);
            }
        } catch (SQLException e) {
            System.err.println("Error loading permanent demotions: " + e.getMessage());
        }
        return permDemotions;
    }

    public void removeTemporaryDemotion(String userId) {
        String sql = "DELETE FROM bot_metadata WHERE key = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "temp_demotion_" + userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error removing temporary demotion: " + e.getMessage());
        }
    }

    public void saveTemporaryDemotion(String userId, List<String> roleIds, LocalDateTime restorationDate) {
        String roleIdsStr = String.join(",", roleIds);
        String sql = "INSERT INTO temporary_demotions (user_id, role_ids, restoration_date) VALUES (?, ?, ?) " +
                "ON CONFLICT (user_id) DO UPDATE SET role_ids = excluded.role_ids, restoration_date = excluded.restoration_date";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, roleIdsStr);
            pstmt.setTimestamp(3, Timestamp.valueOf(restorationDate));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving temporary demotion: " + e.getMessage());
        }
    }

    public List<String> getTemporaryDemotionRoles(String userId) {
        String sql = "SELECT role_ids FROM temporary_demotions WHERE user_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String roleIdsStr = rs.getString("role_ids");
                if (roleIdsStr != null && !roleIdsStr.isEmpty()) {
                    return Arrays.asList(roleIdsStr.split(","));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting temporary demotion roles: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public Map<String, Map<String, Object>> loadTemporaryDemotionsWithRoles() {
        Map<String, Map<String, Object>> demotions = new HashMap<>();
        String sql = "SELECT user_id, role_ids, restoration_date FROM temporary_demotions";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String userId = rs.getString("user_id");
                String roleIdsStr = rs.getString("role_ids");
                Timestamp ts = rs.getTimestamp("restoration_date");
                LocalDateTime restorationDate = ts.toInstant().atZone(java.time.ZoneId.of("America/New_York")).toLocalDateTime();
                
                Map<String, Object> demotion = new HashMap<>();
                demotion.put("roleIds", Arrays.asList(roleIdsStr.split(",")));
                demotion.put("restorationDate", restorationDate);
                demotions.put(userId, demotion);
            }
        } catch (SQLException e) {
            System.err.println("Error loading temporary demotions with roles: " + e.getMessage());
        }
        return demotions;
    }

    public void deleteTemporaryDemotion(String userId) {
        String sql = "DELETE FROM temporary_demotions WHERE user_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting temporary demotion: " + e.getMessage());
        }
    }

    public void markDemotionAsServed(String userId, int strikeCount) {
        String sql = "INSERT INTO bot_metadata (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "served_temp_demotion_" + userId);
            pstmt.setString(2, String.valueOf(strikeCount));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error marking demotion as served: " + e.getMessage());
        }
    }

    public int getServedDemotionStrikeCount(String userId) {
        String sql = "SELECT value FROM bot_metadata WHERE key = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "served_temp_demotion_" + userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting served demotion strike count: " + e.getMessage());
        }
        return -1;
    }

    public void clearServedDemotion(String userId) {
        String sql = "DELETE FROM bot_metadata WHERE key = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "served_temp_demotion_" + userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error clearing served demotion: " + e.getMessage());
        }
    }

    public List<Strike> getStrikes(String userId) {
        List<Strike> strikes = new ArrayList<>();
        String sql = """
            SELECT s.id, s.user_id, s.reason, s.moderator_id, s.date
            FROM strikes s
            WHERE s.user_id = ?
            AND s.id NOT IN (
                SELECT a.strike_id
                FROM appeals a
                WHERE a.status = 'APPROVED'
            )
            ORDER BY s.date ASC;
            """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                strikes.add(new Strike(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("reason"),
                        rs.getString("moderator_id"),
                        rs.getTimestamp("date")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting strikes: " + e.getMessage());
            e.printStackTrace();
        }
        return strikes;
    }

    public List<Strike> getAllStrikes(String userId) {
        List<Strike> strikes = new ArrayList<>();
        String sql = "SELECT id, user_id, reason, moderator_id, date FROM strikes WHERE user_id = ? ORDER BY date ASC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                strikes.add(new Strike(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("reason"),
                        rs.getString("moderator_id"),
                        rs.getTimestamp("date")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all strikes: " + e.getMessage());
        }
        return strikes;
    }

    public Strike getStrikeById(int strikeId) {
        String sql = "SELECT id, user_id, reason, moderator_id, date FROM strikes WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, strikeId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Strike(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("reason"),
                        rs.getString("moderator_id"),
                        rs.getTimestamp("date")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting strike by ID: " + e.getMessage());
        }
        return null;
    }

    public boolean removeStrike(String userId, int strikeNumber) {
        List<Strike> strikes = getStrikes(userId);

        if (strikeNumber < 1 || strikeNumber > strikes.size()) {
            System.err.println("Invalid strike number: " + strikeNumber + " (user has " + strikes.size() + " strikes)");
            return false;
        }

        Strike strikeToRemove = strikes.get(strikeNumber - 1);
        int strikeId = strikeToRemove.getId();

        try (Connection conn = dbManager.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            if (autoCommit) {
                conn.setAutoCommit(false);
            }

            try {
                String deleteStrikeSQL = "DELETE FROM strikes WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteStrikeSQL)) {
                    pstmt.setInt(1, strikeId);
                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected > 0) {
                        if (autoCommit) {
                            conn.commit();
                        }
                        return true;
                    } else {
                        if (autoCommit) {
                            try {
                                if (!conn.getAutoCommit()) {
                                    conn.rollback();
                                }
                            } catch (SQLException rollbackEx) {
                                // Ignore or log
                            }
                        }
                        return false;
                    }
                }
            } catch (SQLException e) {
                if (autoCommit) {
                    try {
                        if (!conn.getAutoCommit()) {
                            conn.rollback();
                        }
                    } catch (SQLException rollbackEx) {
                        e.addSuppressed(rollbackEx);
                    }
                }
                throw e;
            } finally {
                if (autoCommit) {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException setAutoEx) {
                        // Suppress or log
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Error removing strike: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}