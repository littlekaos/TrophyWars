package TWBot.services;

import TWBot.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RoleRestorationService {
    private static final Logger logger = LoggerFactory.getLogger(RoleRestorationService.class);
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final long CHECK_INTERVAL_MINUTES = 30; // Check every 30 minutes
    
    private final DemotionService demotionService;
    private final BotConfig config;
    private JDA jda;

    public RoleRestorationService(DemotionService demotionService, BotConfig config) {
        this.demotionService = demotionService;
        this.config = config;
    }
    
    public void initialize(JDA jda) {
        this.jda = jda;
        startRoleRestorationScheduler();
        logger.info("Role Restoration Service initialized and started - checking every {} minutes", CHECK_INTERVAL_MINUTES);
    }
    
    private void startRoleRestorationScheduler() {
        scheduler.scheduleAtFixedRate(this::checkAndRestoreRoles, 0, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }
    
    private void checkAndRestoreRoles() {
        try {
            logger.debug("Starting automatic role restoration check...");
            
            LocalDateTime now = LocalDateTime.now(ZoneId.of("America/New_York"));
            Map<String, Map<String, Object>> temporaryDemotions = demotionService.getTemporaryDemotions();
            
            List<String> toRestore = new ArrayList<>();
            
            for (Map.Entry<String, Map<String, Object>> entry : temporaryDemotions.entrySet()) {
                String userId = entry.getKey();
                Map<String, Object> demotion = entry.getValue();
                
                LocalDateTime restorationDate = (LocalDateTime) demotion.get("restorationDate");
                
                if (now.isAfter(restorationDate)) {
                    @SuppressWarnings("unchecked")
                    List<String> roleIds = (List<String>) demotion.get("roleIds");
                    
                    // Remove demotion record
                    demotionService.removeDemotion(userId, jda);
                    
                    // Restore roles automatically on expiry (true for automatic)
                    restoreRolesForUser(userId, roleIds, true);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during automatic role restoration check: {}", e.getMessage(), e);
        }
    }

    public void restoreRolesForUser(String userId, List<String> roleIds, boolean isAutomatic) {
        // We need a guild to restore roles. In this bot, we assume there's one primary guild.
        // If there are multiple, we'd need to know which one.
        // For now, let's try to find the user in any guild the bot is in, 
        // or use a configured Guild ID if available.
        
        String guildId = config.getGuildId(); // BotConfig has getGuildId() which returns GUILD_ID from env
        if (guildId == null || guildId.isEmpty()) {
            logger.warn("Guild ID not configured, cannot restore roles for user {}", userId);
            return;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.warn("Guild {} not found, cannot restore roles for user {}", guildId, userId);
            return;
        }

        guild.retrieveMemberById(userId).queue(
            member -> {
                List<Role> rolesToRestore = new ArrayList<>();
                boolean hasOtherStaffRole = false;
                
                for (String roleId : roleIds) {
                    if (roleId == null || roleId.trim().isEmpty()) continue;
                    
                    String trimmedId = roleId.trim();
                    Role role = guild.getRoleById(trimmedId);
                    if (role != null) {
                        rolesToRestore.add(role);
                    }
                }

                if (!rolesToRestore.isEmpty()) {
                    for (Role role : rolesToRestore) {
                        guild.addRoleToMember(member, role).queue(
                            success -> logger.debug("Successfully restored role {} to {}", role.getName(), member.getEffectiveName()),
                            failure -> logger.warn("Failed to restore role {} to {}: {}", role.getName(), member.getEffectiveName(), failure.getMessage())
                        );
                    }
                }

                // Mark demotion as served for the current strike count
                // This prevents DemotionSyncService from re-applying it immediately
                int strikeCount = demotionService.getDatabase().getStrikes(userId).size();
                demotionService.getDatabase().markDemotionAsServed(userId, strikeCount);
                logger.info("Marked temporary demotion as served for user {} at {} strikes", member.getEffectiveName(), strikeCount);

                // We no longer need to manage STAFF_VERIFY here as prerequisites are removed
                
                // Only send notification if it's automatic or if they still have 2+ strikes (e.g. going from 3 to 2)
                if (isAutomatic || strikeCount >= 2) {
                    sendRestorationNotification(userId, member.getEffectiveName());
                }
                logger.info("Automatically restored roles for user: {} ({})", member.getEffectiveName(), userId);
            },
            failure -> logger.warn("Member with ID {} not found in guild {}, cannot restore roles", userId, guildId)
        );
    }
    
    private void sendRestorationNotification(String userId, String username) {
        TextChannel staffChannel = jda.getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
        if (staffChannel != null) {
            staffChannel.sendMessage(String.format("🔄 **Automatic Role Restoration**\n<@%s> roles have been automatically restored after temporary demotion period.", 
                    userId)).queue(
                success -> logger.debug("Sent restoration notification for user {}", username),
                failure -> logger.warn("Failed to send restoration notification for user {}: {}", username, failure.getMessage())
            );
        }
    }
    
    public boolean isRunning() {
        return !scheduler.isShutdown() && !scheduler.isTerminated();
    }

    public void triggerManualCheck() {
        checkAndRestoreRoles();
    }

    public void shutdown() {
        logger.info("Shutting down Role Restoration Service...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
