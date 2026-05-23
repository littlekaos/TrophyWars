package TWBot.services;

import TWBot.database.AppealDatabase;
import TWBot.database.StrikeDatabase;
import TWBot.models.Appeal;
import TWBot.models.Strike;
import java.util.List;

public class AppealService {
    private final AppealDatabase appealDatabase = new AppealDatabase();
    private final StrikeDatabase strikeDatabase = new StrikeDatabase();
    private DemotionService demotionService;
    private RoleRestorationService roleRestorationService;
    private net.dv8tion.jda.api.JDA jda;

    public void setDemotionService(DemotionService demotionService) {
        this.demotionService = demotionService;
    }

    public void setRoleRestorationService(RoleRestorationService roleRestorationService) {
        this.roleRestorationService = roleRestorationService;
    }

    public void setJda(net.dv8tion.jda.api.JDA jda) {
        this.jda = jda;
    }

    public boolean createAppeal(String userId, int strikeNumber, String reason) {
        // Check if user already has 2 pending appeals (concurrent limit)
        if (getActiveAppealCount(userId) >= 2) {
            return false;
        }

        // Use getStrikes so the numbering matches what the user sees in /strikes
        List<Strike> strikes = strikeDatabase.getStrikes(userId);

        if (strikeNumber < 1 || strikeNumber > strikes.size()) {
            return false;
        }

        Strike strike = strikes.get(strikeNumber - 1);
        
        // Check if they've already appealed this specific strike
        if (hasAppealedStrike(userId, strike.getId())) {
            return false;
        }

        return appealDatabase.createAppeal(userId, strike.getId(), reason);
    }

    public boolean createMultipleAppeals(String userId, int[] strikeNumbers, String[] reasons) {
        if (strikeNumbers.length > 2 || strikeNumbers.length != reasons.length) {
            return false;
        }

        int currentActive = getActiveAppealCount(userId);
        if (currentActive + strikeNumbers.length > 2) {
            return false;
        }

        List<Strike> strikes = strikeDatabase.getStrikes(userId);

        for (int strikeNumber : strikeNumbers) {
            if (strikeNumber < 1 || strikeNumber > strikes.size()) {
                return false;
            }
        }

        for (int i = 0; i < strikeNumbers.length; i++) {
            for (int j = i + 1; j < strikeNumbers.length; j++) {
                if (strikeNumbers[i] == strikeNumbers[j]) {
                    return false;
                }
            }
        }

        for (int strikeNumber : strikeNumbers) {
            Strike strike = strikes.get(strikeNumber - 1);
            if (hasAppealedStrike(userId, strike.getId())) {
                return false;
            }
        }

        for (int i = 0; i < strikeNumbers.length; i++) {
            Strike strike = strikes.get(strikeNumbers[i] - 1);
            if (!appealDatabase.createAppeal(userId, strike.getId(), reasons[i])) {
                return false;
            }
        }

        return true;
    }

    public int getActiveAppealCount(String userId) {
        return appealDatabase.getActiveAppealCount(userId);
    }

    public boolean hasAppealedStrike(String userId, int strikeId) {
        return appealDatabase.hasAppealedStrike(userId, strikeId);
    }

    public boolean hasEverSubmittedAppeal(String userId) {
        return appealDatabase.hasEverSubmittedAppeal(userId);
    }

    public List<Appeal> getAllPendingAppeals() {
        return appealDatabase.getAllPendingAppeals();
    }

    public List<Appeal> getUserAppeals(String userId) {
        return appealDatabase.getUserAppeals(userId);
    }

    public Appeal getAppealById(int appealId) {
        return appealDatabase.getAppealById(appealId);
    }

    public boolean approveAppeal(int appealId, String reviewerId, String reviewReason) {
        Appeal appeal = appealDatabase.getAppealById(appealId);
        if (appeal == null || !appeal.getStatus().equals("PENDING")) {
            return false;
        }

        boolean success = appealDatabase.reviewAppeal(appealId, reviewerId, "APPROVED", reviewReason);
        if (success) {
            System.out.println("✅ Appeal " + appealId + " approved successfully. Strike " + appeal.getStrikeId() + " remains for historical records.");
            
            // Check if user should be removed from demotions
            if (demotionService != null) {
                String userId = appeal.getUserId();
                List<Strike> activeStrikes = strikeDatabase.getStrikes(userId);
                int activeCount = activeStrikes.size();

                if (activeCount < 3) {
                    // Remove from permanent demotions FIRST so DemotionProtectionListener allows restoration
                    demotionService.removeDemotion(userId, jda);
                    System.out.println("✅ User " + userId + " removed from demotions as strike count is now " + activeCount);

                    // Restore roles if we have them saved
                    if (roleRestorationService != null) {
                        List<String> roleIds = strikeDatabase.getTemporaryDemotionRoles(userId);
                        if (!roleIds.isEmpty()) {
                            roleRestorationService.restoreRolesForUser(userId, roleIds, false);
                            System.out.println("✅ Restored roles for user " + userId + " after successful appeal.");
                        }
                    }
                }
            }
        }
        return success;
    }

    public boolean denyAppeal(int appealId, String reviewerId, String reviewReason) {
        return appealDatabase.reviewAppeal(appealId, reviewerId, "DENIED", reviewReason);
    }

    public boolean resetUserAppeals(String userId) {
        return appealDatabase.resetUserAppeals(userId);
    }

    public Strike getStrikeById(int strikeId) {
        return strikeDatabase.getStrikeById(strikeId);
    }
}