package TWBot.database;

import TWBot.models.Appeal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AppealDatabase {
    private final DatabaseManager dbManager;

    public AppealDatabase() {
        this.dbManager = DatabaseManager.getInstance();
        initAppealTables();
    }

    private void initAppealTables() {
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            String createAppealsTable = """
               CREATE TABLE IF NOT EXISTS appeals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    strike_id INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    status TEXT DEFAULT 'PENDING',
                    reviewer_id TEXT,
                    review_reason TEXT,
                    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    review_date TIMESTAMP,
                    FOREIGN KEY (strike_id) REFERENCES strikes(id) ON DELETE CASCADE
                );
                """;
            stmt.executeUpdate(createAppealsTable);

        } catch (SQLException e) {
            System.err.println("Error initializing appeal tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean createAppeal(String userId, int strikeId, String reason) {
        if (getActiveAppealCount(userId) >= 2) {
            return false;
        }

        if (hasAppealedStrike(userId, strikeId)) {
            return false;
        }

        String sql = "INSERT INTO appeals (user_id, strike_id, reason) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setInt(2, strikeId);
            pstmt.setString(3, reason);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error creating appeal: " + e.getMessage());
            return false;
        }
    }

    public boolean hasEverSubmittedAppeal(String userId) {
        String sql = "SELECT COUNT(*) FROM appeals WHERE user_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("Error checking if user has submitted appeal: " + e.getMessage());
            return false;
        }
    }

    public int getActiveAppealCount(String userId) {
        String sql = "SELECT COUNT(*) FROM appeals WHERE user_id = ? AND status = 'PENDING'";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Error getting active appeal count: " + e.getMessage());
            return 0;
        }
    }

    public boolean hasAppealedStrike(String userId, int strikeId) {
        String sql = "SELECT COUNT(*) FROM appeals WHERE user_id = ? AND strike_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setInt(2, strikeId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("Error checking if user has appealed strike: " + e.getMessage());
            return false;
        }
    }

    public List<Appeal> getAllPendingAppeals() {
        List<Appeal> appeals = new ArrayList<>();
        String sql = "SELECT * FROM appeals WHERE status = 'PENDING' ORDER BY date ASC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                appeals.add(new Appeal(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getInt("strike_id"),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("reviewer_id"),
                        rs.getString("review_reason"),
                        rs.getTimestamp("date"),
                        rs.getTimestamp("review_date")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting pending appeals: " + e.getMessage());
        }
        return appeals;
    }

    public List<Appeal> getUserAppeals(String userId) {
        List<Appeal> appeals = new ArrayList<>();
        String sql = "SELECT * FROM appeals WHERE user_id = ? ORDER BY date DESC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                appeals.add(new Appeal(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getInt("strike_id"),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("reviewer_id"),
                        rs.getString("review_reason"),
                        rs.getTimestamp("date"),
                        rs.getTimestamp("review_date")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting user appeals: " + e.getMessage());
        }
        return appeals;
    }

    public Appeal getAppealById(int appealId) {
        String sql = "SELECT * FROM appeals WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appealId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Appeal(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getInt("strike_id"),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("reviewer_id"),
                        rs.getString("review_reason"),
                        rs.getTimestamp("date"),
                        rs.getTimestamp("review_date")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting appeal by ID: " + e.getMessage());
        }
        return null;
    }

    public boolean reviewAppeal(int appealId, String reviewerId, String status, String reviewReason) {
        String sql = "UPDATE appeals SET status = ?, reviewer_id = ?, review_reason = ?, review_date = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, reviewerId);
            pstmt.setString(3, reviewReason);
            pstmt.setInt(4, appealId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error reviewing appeal: " + e.getMessage());
            return false;
        }
    }

    public boolean resetUserAppeals(String userId) {
        String sql = "DELETE FROM appeals WHERE user_id = ? AND status != 'APPROVED'";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error resetting user appeals: " + e.getMessage());
            return false;
        }
    }
}