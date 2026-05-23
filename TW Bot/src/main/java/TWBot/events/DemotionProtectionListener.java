package TWBot.events;

import TWBot.config.BotConfig;
import TWBot.services.DemotionService;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemotionProtectionListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DemotionProtectionListener.class);
    private final DemotionService demotionService;

    public DemotionProtectionListener(DemotionService demotionService) {
        this.demotionService = demotionService;
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        String userId = event.getUser().getId();
        Member member = event.getMember();
        
        // 1. Protection for Demoted Users
        if (demotionService.isDemoted(userId)) {
            boolean isPerm = demotionService.isPermanentlyDemoted(userId);
            String type = isPerm ? "permanently" : "temporarily";
            
            for (Role role : event.getRoles()) {
                // Remove staff roles and support roles for demoted users
                if (BotConfig.isStaffOrModRole(role.getId()) || BotConfig.isSupportRole(role.getId())) {
                    event.getGuild().removeRoleFromMember(member, role).queue(
                        success -> {
                            logger.info("Removed staff role {} from {} demoted user {}", role.getName(), type, userId);
                            
                            // Find who added the role to mention them in the notice
                            event.getGuild().retrieveAuditLogs()
                                .type(ActionType.MEMBER_ROLE_UPDATE)
                                .limit(5)
                                .queue(logs -> {
                                    String moderatorMention = "Unknown Moderator";
                                    for (AuditLogEntry entry : logs) {
                                        if (entry.getTargetId().equals(userId)) {
                                            moderatorMention = entry.getUser().getAsMention();
                                            break;
                                        }
                                    }
                                    sendProtectionNotice(event, role, type + " demoted", moderatorMention);
                                }, failure -> {
                                    sendProtectionNotice(event, role, type + " demoted", "Unknown Moderator");
                                });
                        },
                        failure -> logger.error("Failed to remove staff role from demoted user: {}", failure.getMessage())
                    );
                }
            }
        }
    }

    private void sendProtectionNotice(GuildMemberRoleAddEvent event, Role role, String type, String moderatorMention) {
        var staffChannel = event.getJDA().getTextChannelById(BotConfig.MANAGER_CHAT_CHANNEL_ID);
        if (staffChannel == null) return;

        Role ownerRole = event.getGuild().getRoleById(BotConfig.SERVER_OWNERSHIP_ROLE_ID);
        if (ownerRole == null) {
            sendDemotionProtectionMessage(staffChannel, "", moderatorMention, role, type, event.getUser().getId());
            return;
        }

        event.getGuild().findMembers(m -> m.getRoles().contains(ownerRole)).onSuccess(members -> {
            StringBuilder pings = new StringBuilder();
            for (net.dv8tion.jda.api.entities.Member m : members) {
                pings.append(m.getAsMention()).append(" ");
            }
            if (pings.length() == 0) {
                pings.append(ownerRole.getAsMention()).append(" ");
            }
            sendDemotionProtectionMessage(staffChannel, pings.toString().trim(), moderatorMention, role, type, event.getUser().getId());
        }).onError(e -> sendDemotionProtectionMessage(staffChannel, ownerRole.getAsMention(), moderatorMention, role, type, event.getUser().getId()));
    }

    private void sendDemotionProtectionMessage(
            net.dv8tion.jda.api.entities.channel.concrete.TextChannel staffChannel,
            String pings,
            String moderatorMention,
            Role role,
            String type,
            String userId
    ) {
        String message = String.format("%s\n⚠️ **Demotion Protection**\nModerator %s attempted to give staff role `%s` to **%s** demoted user <@%s>. Role has been automatically removed.",
                pings, moderatorMention, role.getName(), type, userId);
        staffChannel.sendMessage(message).queue();
    }
}
