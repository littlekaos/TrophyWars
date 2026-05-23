package TWBot.services;

import TWBot.config.BotConfig;
import TWBot.database.StrikeDatabase;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DemotionSyncService {
    private static final Logger logger = LoggerFactory.getLogger(DemotionSyncService.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private final DemotionService demotionService;
    private final StrikeDatabase database;
    private final BotConfig config;
    private JDA jda;

    public DemotionSyncService(DemotionService demotionService, StrikeDatabase database, BotConfig config) {
        this.demotionService = demotionService;
        this.database = database;
        this.config = config;
    }

    public void initialize(JDA jda) {
        this.jda = jda;
        // Run every 30 minutes
        scheduler.scheduleAtFixedRate(this::syncDemotions, 0, 30, TimeUnit.MINUTES);
        logger.info("Demotion Sync Service initialized - checking every 30 minutes");
    }

    public void syncDemotions() {
        try {
            // Disabled automatic strike scanning as per user request
            // logger.info("Starting demotion synchronization check...");
            
            // Still update the message to ensure it's in sync with current demotion table
            demotionService.updateDemotionListMessage(jda);
        } catch (Exception e) {
            logger.error("Error during demotion sync: {}", e.getMessage());
        }
    }

    private void checkAndApplyTempDemotion(Guild guild, Member member) {
        String userId = member.getId();
        boolean alreadyTracked = database.loadTemporaryDemotionsWithRoles().containsKey(userId);
        boolean isPerm = database.loadPermanentDemotions().contains(userId);
        
        if (isPerm) return;

        if (!alreadyTracked) {
            // Check if they already served a demotion for their current strike count
            int strikeCount = database.getStrikes(userId).size();
            int servedStrikeCount = database.getServedDemotionStrikeCount(userId);
            
            if (servedStrikeCount >= strikeCount) {
                logger.debug("User {} already served their temporary demotion for {} strikes. Skipping re-application.", member.getUser().getName(), strikeCount);
                return;
            }

            List<String> removedRoleIds = new ArrayList<>();
            for (String roleId : BotConfig.STAFF_ROLE_IDS) {
                Role role = guild.getRoleById(roleId);
                if (role != null && member.getRoles().contains(role)) {
                    removedRoleIds.add(role.getId());
                    guild.removeRoleFromMember(member, role).queue();
                }
            }

            LocalDateTime restorationDate = LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(BotConfig.TEMP_DEMOTION_DAYS);
            demotionService.addTemporaryDemotion(member.getId(), removedRoleIds, restorationDate);
            logger.info("Auto-applied temporary demotion tracking for {} (2 strikes)", member.getUser().getName());
        }
    }

    private void checkAndApplyPermDemotion(Guild guild, Member member) {
        if (database.loadPermanentDemotions().contains(member.getId())) return;

        for (String roleId : BotConfig.STAFF_ROLE_IDS) {
            Role role = guild.getRoleById(roleId);
            if (role != null && member.getRoles().contains(role)) {
                guild.removeRoleFromMember(member, role).queue();
            }
        }

        demotionService.addPermanentDemotion(member.getId());
        logger.info("Auto-applied permanent demotion for {} (3+ strikes)", member.getUser().getName());
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
