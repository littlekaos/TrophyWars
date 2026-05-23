package TWBot.services;

import TWBot.database.StrikeDatabase;
import TWBot.models.Strike;

import java.sql.Timestamp;
import java.util.List;

public class StrikeService {
    private final StrikeDatabase database = new StrikeDatabase();

    public StrikeDatabase getDatabase() {
        return database;
    }

    public StrikeService() {
    }

    public void issueStrike(String userId, String reason, String moderatorId) {
        database.addStrike(userId, reason, moderatorId, new Timestamp(System.currentTimeMillis()));
    }

    public List<Strike> getStrikes(String userId) {
        return database.getStrikes(userId);
    }

    public List<Strike> getAllStrikes(String userId) {
        return database.getAllStrikes(userId);
    }

    public void clearStrikes(String userId) {
        database.clearStrikes(userId);
        database.clearServedDemotion(userId);
    }

    public boolean removeStrike(String userId, int strikeNumber) {
        boolean removed = database.removeStrike(userId, strikeNumber);
        if (removed) {
            database.clearServedDemotion(userId);
        }
        return removed;
    }

    public void clearAllStrikes(String userId) {
        database.clearStrikes(userId);
        database.clearServedDemotion(userId);
    }

    public void editStrike(String userId, int strikeNumber, String newReason) {
        database.editStrike(userId, strikeNumber, newReason);
    }

    private void ensureStrikeCount(String userId, int requiredCount) {
        int currentCount = database.getStrikes(userId).size();
        if (currentCount < requiredCount) {
            for (int i = 0; i < (requiredCount - currentCount); i++) {
                issueStrike(userId, "Legacy Strike Backup", "SYSTEM");
            }
        }
    }

    public List<String> getAllUsersWithStrikes() {
        return database.getAllUsersWithStrikes();
    }

    public void importInitialStrikes() {
        if (database.hasBeenInitialized()) {
            return;
        }

        try {
            // Legacy permanent demotions (Staff blocked from having roles)
            // The Pringles (1382914870495809556)
            database.addPermanentDemotion("1382914870495809556");
            if (database.getStrikes("1382914870495809556").isEmpty()) {
                issueStrike("1382914870495809556", "Legacy Permanent Demotion", "SYSTEM");
                issueStrike("1382914870495809556", "Legacy Permanent Demotion", "SYSTEM");
                issueStrike("1382914870495809556", "Legacy Permanent Demotion", "SYSTEM");
            }

            // Jxn. (862778119614365747)
            database.addPermanentDemotion("862778119614365747");
            if (database.getStrikes("862778119614365747").isEmpty()) {
                issueStrike("862778119614365747", "Legacy Permanent Demotion", "SYSTEM");
                issueStrike("862778119614365747", "Legacy Permanent Demotion", "SYSTEM");
                issueStrike("862778119614365747", "Legacy Permanent Demotion", "SYSTEM");
            }

            // Check and import legacy specific strikes requested
            // 1270476962997211200: "adding comment in cd for no reason" by "TW Management"
            List<Strike> s1 = database.getStrikes("1270476962997211200");
            if (s1.stream().noneMatch(s -> s.getReason().equals("adding comment in cd for no reason"))) {
                issueStrike("1270476962997211200", "adding comment in cd for no reason", "TW MANAGEMENT");
            }

            // 1412876353560248450: "talking in cd when he's not hosting" by "TW Management"
            List<Strike> s2 = database.getStrikes("1412876353560248450");
            if (s2.stream().noneMatch(s -> s.getReason().equals("talking in cd when he's not hosting"))) {
                issueStrike("1412876353560248450", "talking in cd when he's not hosting", "TW MANAGEMENT");
            }

            // Akaza (1222432663592374302) 2 strikes
            List<Strike> s3 = database.getStrikes("1222432663592374302");
            if (s3.size() < 2) {
                while (database.getStrikes("1222432663592374302").size() < 2) {
                    issueStrike("1222432663592374302", "Legacy Strike", "SYSTEM");
                }
            }

            // Ensure all other requested IDs have their strikes
            ensureStrikeCount("1255903032344842406", 1);
            ensureStrikeCount("588388784320544778", 1);
            ensureStrikeCount("1374720554812313672", 1);
            ensureStrikeCount("1164209945114320896", 1);
            ensureStrikeCount("1059524335808815185", 1);
            ensureStrikeCount("1012795871454306334", 2);
            ensureStrikeCount("1230297242276200458", 1);

            database.markAsInitialized();
            System.out.println("✅ Imported initial strikes. Total strikes now: " + database.getTotalStrikeCount());
        } catch (Exception e) {
            System.err.println("Error importing initial strikes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
