package TWBot.repositories;

import TWBot.database.DatabaseManager;
import TWBot.models.EventNameData;

import java.sql.*;
import java.util.*;

public class SQLiteEventNameRepository {

    private final DatabaseManager dbManager;

    public SQLiteEventNameRepository() {
        this.dbManager = DatabaseManager.getInstance();
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS event_names (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL UNIQUE,
                event_name TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSQL);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_event_names_user_id ON event_names(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_event_names_event_name ON event_names(event_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_event_names_created_at ON event_names(created_at)");

            System.out.println("SQLite database initialized successfully");

        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveEventName(String userId, String eventName) {

        String sql = """
            INSERT INTO event_names (user_id, event_name, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(user_id) DO UPDATE SET
                event_name = excluded.event_name,
                updated_at = CURRENT_TIMESTAMP;
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setString(2, eventName.toLowerCase());

            int rowsAffected = stmt.executeUpdate();

            System.out.println(
                "Event name saved for user " + userId +
                " (rows affected: " + rowsAffected + ")"
            );

        } catch (SQLException e) {
            System.err.println("Error saving event name for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public EventNameData getEventNameByUser(String userId) {

        String sql = """
            SELECT 
                user_id,
                event_name,
                (strftime('%s', created_at) * 1000) AS timestamp
            FROM event_names
            WHERE user_id = ?;
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new EventNameData(
                            rs.getString("user_id"),
                            rs.getString("event_name"),
                            rs.getLong("timestamp")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving event name for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public List<EventNameData> searchEventNameByName(String name) {

        String sql = """
            SELECT 
                user_id,
                event_name,
                (strftime('%s', created_at) * 1000) AS timestamp
            FROM event_names
            WHERE LOWER(event_name) LIKE ?;
            """;

        List<EventNameData> results = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + name.toLowerCase() + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new EventNameData(
                            rs.getString("user_id"),
                            rs.getString("event_name"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error searching event names: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    public EventNameData getEventNameByUserAndName(String userId, String name) {

        String sql = """
            SELECT 
                user_id,
                event_name,
                (strftime('%s', created_at) * 1000) AS timestamp
            FROM event_names
            WHERE user_id = ?
              AND LOWER(event_name) = ?;
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setString(2, name.toLowerCase());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new EventNameData(
                            rs.getString("user_id"),
                            rs.getString("event_name"),
                            rs.getLong("timestamp")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving event name: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public Map<String, EventNameData> getAllEventNames() {

        String sql = """
            SELECT 
                user_id,
                event_name,
                (strftime('%s', created_at) * 1000) AS timestamp
            FROM event_names;
            """;

        Map<String, EventNameData> results = new HashMap<>();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String userId = rs.getString("user_id");

                results.put(userId, new EventNameData(
                        userId,
                        rs.getString("event_name"),
                        rs.getLong("timestamp")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving all event names: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    public void printStatistics() {

        String sql = """
            SELECT 
                COUNT(*) AS total_count,
                MAX(created_at) AS latest_submission
            FROM event_names;
            """;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                int totalCount = rs.getInt("total_count");
                String latest = rs.getString("latest_submission");

                System.out.println("=== Event Names Database Statistics ===");
                System.out.println("Total event names stored: " + totalCount);
                System.out.println("Latest submission: " + (latest != null ? latest : "None"));
                System.out.println("======================================");
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving statistics: " + e.getMessage());
        }
    }
}
