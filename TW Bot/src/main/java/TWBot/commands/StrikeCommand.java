package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.models.Strike;
import TWBot.services.DemotionService;
import TWBot.services.StrikeService;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class StrikeCommand implements Command {

    private final TWBot bot;
    private final StrikeService strikeService;
    private final DemotionService demotionService;

    public StrikeCommand(TWBot bot) {
        this.bot = bot;
        this.strikeService = bot.getStrikeService();
        this.demotionService = bot.getDemotionService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("strike", "Issue a strike to a user.")
                        .addOption(OptionType.USER, "user", "The user to strike", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the strike", true),
                Commands.slash("strikes", "View strikes of a user.")
                        .addOption(OptionType.USER, "user", "The user to view", true),
                Commands.slash("removestrike", "Remove a specific strike from a user.")
                        .addOption(OptionType.USER, "user", "The user to remove a strike from", true)
                        .addOption(OptionType.INTEGER, "number", "Strike number to remove (1 = first strike)", true),
                Commands.slash("clearstrikes", "Clear all strikes from a user. (Admin Only)")
                        .addOption(OptionType.USER, "user", "The user to clear strikes from", true),
                Commands.slash("editstrike", "Edit the reason of a specific strike. (Admin Only)")
                        .addOption(OptionType.USER, "user", "The user whose strike to edit", true)
                        .addOption(OptionType.INTEGER, "number", "Strike number to edit (1 = first strike)", true)
                        .addOption(OptionType.STRING, "newreason", "New reason for the strike", true)
        );
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        Member member = event.getMember();
        if (member == null) return false;

        // Block any strike-related actions for users with support roles
        boolean hasSupportRole = member.getRoles().stream()
                .anyMatch(role -> BotConfig.SUPPORT_ROLE_IDS.contains(role.getId()));
        if (hasSupportRole) return false;

        String name = event.getName();
        return switch (name) {
            case "strike", "removestrike" -> PermissionUtils.isModerator(member, bot.getConfig());
            case "clearstrikes", "editstrike" -> PermissionUtils.isAdmin(member, bot.getConfig());
            case "strikes" -> true;
            default -> false;
        };
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        switch (commandName) {
            case "strike" -> handleStrike(event);
            case "strikes" -> handleStrikes(event);
            case "removestrike" -> handleRemoveStrike(event);
            case "clearstrikes" -> handleClearStrikes(event);
            case "editstrike" -> handleEditStrike(event);
        }
    }

    private void handleStrike(SlashCommandInteractionEvent event) {
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        if (user.getId().equals(event.getUser().getId())) {
            event.reply("❌ You cannot strike yourself.").setEphemeral(true).queue();
            return;
        }

        String reason = Objects.requireNonNull(event.getOption("reason")).getAsString();

        // Execute directly without approval
        event.deferReply(true).queue();
        Member moderator = event.getMember();

        event.getGuild().retrieveMember(user).queue(
                targetMember -> {
                    if (cannotStrike(event, moderator, targetMember)) {
                        return;
                    }
                    proceedWithStrike(event, user, reason, moderator);
                },
                failure -> {
                    event.getHook().editOriginal("❌ Could not find the target user in this server.").queue();
                }
        );
    }

    private boolean cannotStrike(SlashCommandInteractionEvent event, Member moderator, Member targetMember) {
        if (moderator.getId().equals(targetMember.getId())) {
            event.getHook().editOriginal("❌ You cannot strike yourself.").queue();
            return true;
        }

        // Check if moderator's highest role is NOT strictly above the target's highest role
        // This prevents striking people with the same highest role or higher
        if (!moderator.canInteract(targetMember)) {
            event.getHook().editOriginal("❌ You cannot strike this user. They must have a lower role than you.").queue();
            return true;
        }

        if (PermissionUtils.isHighManagement(moderator, bot.getConfig())) {
            // High management can strike anyone except possibly each other (handled by canInteract above)
        } else if (hasProtectedRole(targetMember)) {
            event.getHook().editOriginal("❌ Cannot strike users with protected roles.").queue();
            return true;
        }

        if (isOverseerStrikingOverseer(moderator, targetMember)) {
            event.getHook().editOriginal("❌ Overseers cannot strike other Overseers.").queue();
            return true;
        }

        return false;
    }

    private boolean hasProtectedRole(Member member) {
        for (String roleId : BotConfig.PROTECTED_ROLE_IDS) {
            if (member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

    private boolean isOverseerStrikingOverseer(Member moderator, Member target) {
        boolean moderatorIsOverseer = moderator.getRoles().stream().anyMatch(role -> role.getId().equals(BotConfig.OVERSEER_ROLE_ID));
        boolean targetIsOverseer = target.getRoles().stream().anyMatch(role -> role.getId().equals(BotConfig.OVERSEER_ROLE_ID));
        boolean moderatorIsManagement = moderator.getRoles().stream().anyMatch(role -> role.getId().equals(BotConfig.MANAGER_ROLE_ID));

        return targetIsOverseer && moderatorIsOverseer && !moderatorIsManagement;
    }

    private void proceedWithStrike(SlashCommandInteractionEvent event, User user, String reason, Member moderator) {
        int previousStrikeCount = strikeService.getStrikes(user.getId()).size();
        strikeService.issueStrike(user.getId(), reason, moderator.getId());
        int strikeCount = previousStrikeCount + 1;

        if (strikeCount == 2) {
            handleStrikeRoleManagement(event, user, strikeCount);
        } else if (strikeCount >= 3) {
            handleStrikeRoleManagement(event, user, strikeCount);
        }

        sendStaffStrikeLog(event, user, reason, strikeCount);
        event.getHook().editOriginal("✅ Strike issued successfully!").queue();
    }

    private void sendStaffStrikeLog(SlashCommandInteractionEvent event, User user, String reason, int strikeCount) {
        String strikeWord = strikeCount == 1 ? "strike" : "strikes";
        String actionId = generateActionId();
        long unixTimestamp = System.currentTimeMillis() / 1000;
        String discordTimestamp = String.format("<t:%d:F>", unixTimestamp);
        
        EmbedBuilder detailedEmbed = new EmbedBuilder()
                .setTitle(BotConfig.TW_EMOJI_MENTION + " STRIKE ISSUED")
                .setColor(new Color(0xFF6B00))
                .setDescription("A strike has been issued to a server member")
                .addField("📋 Target User", user.getName() + "\n" + user.getId() + "\n" + user.getAsMention(), false)
                .addField("👮 Moderator", event.getMember().getEffectiveName() + "\n" + event.getMember().getAsMention(), false)
                .addField("📊 Strike Count", getStrikeCountEmojis(strikeCount) + "\n" + strikeCount + " " + strikeWord, false)
                .addField("📝 Reason", reason, false)
                .addField("⚡ Action ID", actionId, false)
                .setFooter("Strike Issued")
                .setTimestamp(java.time.Instant.now())
                .setThumbnail(user.getEffectiveAvatarUrl());
        
        EmbedBuilder simpleEmbed = new EmbedBuilder()
                .setTitle(BotConfig.TW_EMOJI_MENTION + " Strike Issued")
                .setColor(new Color(0x00E1FF))
                .setDescription(String.format("<@%s> (%s) has been striked.", user.getId(), user.getName()))
                .addField("User ID", user.getId(), false)
                .addField("Reason", reason, false)
                .addField("Now has:", strikeCount + " " + strikeWord, false)
                .setFooter("Strike System")
                .setTimestamp(java.time.Instant.now());

        TextChannel loggingChannel = event.getJDA().getTextChannelById("1472814682245955627");
        if (loggingChannel != null) {
            loggingChannel.sendMessageEmbeds(detailedEmbed.build()).queue();
        }
        
        TextChannel staffStrikeChannel = event.getJDA().getTextChannelById(BotConfig.STAFF_STRIKE_LOG_CHANNEL_ID);
        if (staffStrikeChannel != null) {
            staffStrikeChannel.sendMessageEmbeds(simpleEmbed.build()).queue();
        }
    }
    
    private String getStrikeCountEmojis(int strikeCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i < strikeCount) {
                sb.append("🔴");
            } else {
                sb.append("⚪");
            }
        }
        return sb.toString();
    }
    
    private String generateActionId() {
        return "MOD-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
    }

    private void handleStrikeRoleManagement(SlashCommandInteractionEvent event, User user, int strikeCount) {
        event.getGuild().retrieveMember(user).queue(
                member -> {
                    if (strikeCount == 2) {
                        handleTwoStrikeDemotion(event, member);
                    } else if (strikeCount >= 3) {
                        handleThreeStrikeDemotion(event, member);
                    }
                },
                failure -> {}
        );
    }

    private void handleTwoStrikeDemotion(SlashCommandInteractionEvent event, Member member) {
        List<String> removedRoleIds = new ArrayList<>();
        for (Role role : member.getRoles()) {
            if (BotConfig.isStaffOrModRole(role.getId()) 
                    || BotConfig.isSupportRole(role.getId())) {
                removedRoleIds.add(role.getId());
                event.getGuild().removeRoleFromMember(member, role).queue();
            }
        }

        LocalDateTime restorationDate = LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(BotConfig.TEMP_DEMOTION_DAYS);
        long unixTimestamp = restorationDate.atZone(ZoneId.of("America/New_York")).toEpochSecond();
        String discordTimestamp = String.format("<t:%d:F>", unixTimestamp);

        demotionService.addTemporaryDemotion(member.getId(), removedRoleIds, restorationDate);
        demotionService.updateDemotionListMessage(event.getJDA());

        TextChannel staffChannel = event.getJDA().getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
        if (staffChannel != null) {
            String message = String.format("<@%s> (%s) 2 strikes, gets roles back %s.", member.getId(), member.getId(), discordTimestamp);
            staffChannel.sendMessage(message).queue();
        }
    }

    private void handleThreeStrikeDemotion(SlashCommandInteractionEvent event, Member member) {
        List<String> removedRoles = new ArrayList<>();
        for (Role role : member.getRoles()) {
            if (BotConfig.isStaffOrModRole(role.getId()) 
                    || BotConfig.isSupportRole(role.getId())) {
                removedRoles.add(role.getId());
                event.getGuild().removeRoleFromMember(member, role).queue();
            }
        }

        // Save roles even for permanent demotions so they can be restored if an appeal is accepted
        // Set restoration date far in the future (10 years) just as a placeholder
        LocalDateTime farFuture = LocalDateTime.now(ZoneId.of("America/New_York")).plusYears(10);
        demotionService.addTemporaryDemotion(member.getId(), removedRoles, farFuture);

        demotionService.addPermanentDemotion(member.getId());
        demotionService.updateDemotionListMessage(event.getJDA());
    }

    public void proceedWithStrikeAction(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, java.util.Map<String, Object> params, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        String targetId = (String) params.get("targetId");
        String reason = (String) params.get("reason");

        guild.retrieveMemberById(targetId).queue(targetMember -> {
            if (!PermissionUtils.canModerate(moderator, targetMember)) {
                if (hook != null) hook.editOriginal("❌ You cannot strike this user due to role hierarchy.").queue();
                return;
            }
            
            int previousStrikeCount = strikeService.getStrikes(targetId).size();
            strikeService.issueStrike(targetId, reason, moderator.getId());
            int strikeCount = previousStrikeCount + 1;

            if (strikeCount >= 2) {
                handleStrikeRoleManagementApproved(guild, targetMember.getUser(), strikeCount, hook);
            }

            sendStaffStrikeLogApproved(guild, targetMember.getUser(), reason, strikeCount);
            if (hook != null) hook.editOriginal("✅ Strike issued successfully!").queue();
        }, failure -> {
            if (hook != null) hook.editOriginal("❌ Could not find the target user in this server.").queue();
        });
    }

    private void handleStrikeRoleManagementApproved(net.dv8tion.jda.api.entities.Guild guild, User user, int strikeCount, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        guild.retrieveMember(user).queue(
                member -> {
                    if (strikeCount == 2) {
                        handleTwoStrikeDemotionApproved(guild, member);
                    } else if (strikeCount >= 3) {
                        handleThreeStrikeDemotionApproved(guild, member);
                    }
                },
                failure -> {}
        );
    }

    private void handleTwoStrikeDemotionApproved(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member member) {
        List<String> removedRoleIds = new ArrayList<>();
        for (net.dv8tion.jda.api.entities.Role role : member.getRoles()) {
            if (BotConfig.isStaffOrModRole(role.getId()) 
                    || BotConfig.isSupportRole(role.getId())) {
                removedRoleIds.add(role.getId());
                guild.removeRoleFromMember(member, role).queue();
            }
        }

        LocalDateTime restorationDate = LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(BotConfig.TEMP_DEMOTION_DAYS);
        long unixTimestamp = restorationDate.atZone(ZoneId.of("America/New_York")).toEpochSecond();
        String discordTimestamp = String.format("<t:%d:F>", unixTimestamp);

        demotionService.addTemporaryDemotion(member.getId(), removedRoleIds, restorationDate);
        demotionService.updateDemotionListMessage(guild.getJDA());

        TextChannel staffChannel = guild.getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
        if (staffChannel != null) {
            String message = String.format("<@%s> (%s) 2 strikes, gets roles back %s.", member.getId(), member.getId(), discordTimestamp);
            staffChannel.sendMessage(message).queue();
        }
    }

    private void handleThreeStrikeDemotionApproved(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member member) {
        List<String> removedRoles = new ArrayList<>();
        for (net.dv8tion.jda.api.entities.Role role : member.getRoles()) {
            if (BotConfig.isStaffOrModRole(role.getId()) 
                    || BotConfig.isSupportRole(role.getId())) {
                removedRoles.add(role.getId());
                guild.removeRoleFromMember(member, role).queue();
            }
        }

        LocalDateTime farFuture = LocalDateTime.now(ZoneId.of("America/New_York")).plusYears(10);
        demotionService.addTemporaryDemotion(member.getId(), removedRoles, farFuture);

        demotionService.addPermanentDemotion(member.getId());
        demotionService.updateDemotionListMessage(guild.getJDA());
    }

    private void sendStaffStrikeLogApproved(net.dv8tion.jda.api.entities.Guild guild, User user, String reason, int strikeCount) {
        String strikeWord = strikeCount == 1 ? "strike" : "strikes";
        
        EmbedBuilder staffLogEmbed = new EmbedBuilder()
                .setTitle(BotConfig.TW_EMOJI_MENTION + " Strike Issued")
                .setColor(new Color(0x00E1FF))
                .setDescription(String.format("<@%s> (%s) has been striked.", user.getId(), user.getName()))
                .addField("User ID", user.getId(), false)
                .addField("Reason", reason, false)
                .addField("Now has:", strikeCount + " " + strikeWord, false)
                .setFooter("Strike System")
                .setTimestamp(java.time.Instant.now());

        TextChannel loggingChannel = guild.getJDA().getTextChannelById("1472814682245955627");
        if (loggingChannel != null) {
            loggingChannel.sendMessageEmbeds(staffLogEmbed.build()).queue();
        }
    }

    private void handleStrikes(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        List<Strike> strikes = strikeService.getStrikes(user.getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("\uD83D\uDCCB Strike History")
                .setFooter("Strike System", null)
                .setTimestamp(java.time.Instant.now())
                .setColor(Color.CYAN);

        if (strikes.isEmpty()) {
            embed.setDescription("✅ No strikes found for " + user.getAsMention() + "\n");
        } else {
            embed.setDescription("Viewing strike history for " + user.getAsMention() + "\n");
            for (int i = 0; i < strikes.size(); i++) {
                Strike s = strikes.get(i);
                String displayModerator = s.getModeratorId().matches("\\d+") ? "<@" + s.getModeratorId() + ">" : s.getModeratorId();
                embed.addField("Strike #" + (i + 1),
                        "**Reason:** " + s.getReason() + "\n" +
                                "**Moderator:** " + displayModerator + "\n" +
                                "**Date:** " + s.getDate().toString().substring(0, 10),
                        false);
            }
        }
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleRemoveStrike(SlashCommandInteractionEvent event) {
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        if (user.getId().equals(event.getUser().getId())) {
            event.reply("❌ You cannot remove your own strikes.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        int strikeNumber = Objects.requireNonNull(event.getOption("number")).getAsInt();

        List<Strike> strikes = strikeService.getStrikes(user.getId());
        String reason = null;
        if (strikeNumber >= 1 && strikeNumber <= strikes.size()) {
            reason = strikes.get(strikeNumber - 1).getReason();
        }

        boolean removed = strikeService.removeStrike(user.getId(), strikeNumber);
        if (removed) {
            if (reason != null) {
                sendStaffRemoveStrikeLog(event, user, strikeNumber, reason);
            }
            
            // If they no longer have enough strikes for demotion, remove them from the list
            int newStrikeCount = strikeService.getStrikes(user.getId()).size();
            if (newStrikeCount < 2) {
                List<String> roleIds = demotionService.getDatabase().getTemporaryDemotionRoles(user.getId());
                demotionService.removeDemotion(user.getId(), event.getJDA());
                if (bot.getRoleRestorationService() != null && !roleIds.isEmpty()) {
                    bot.getRoleRestorationService().restoreRolesForUser(user.getId(), roleIds, false);
                }
            }
            // If they went from 3+ to 2, they should be moved to temporary demotion
            else if (newStrikeCount == 2) {
                // If they were permanently demoted, we should ensure they now have a temporary restoration date
                if (!demotionService.isTemporarilyDemoted(user.getId())) {
                    List<String> roleIds = demotionService.getDatabase().getTemporaryDemotionRoles(user.getId());
                    if (!roleIds.isEmpty()) {
                        LocalDateTime restorationDate = LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(BotConfig.TEMP_DEMOTION_DAYS);
                        demotionService.addTemporaryDemotion(user.getId(), roleIds, restorationDate);
                        demotionService.updateDemotionListMessage(event.getJDA());
                        
                        // Notify that they are now on temporary demotion
                        TextChannel staffChannel = event.getJDA().getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
                        if (staffChannel != null) {
                            long unixTimestamp = restorationDate.atZone(ZoneId.of("America/New_York")).toEpochSecond();
                            String discordTimestamp = String.format("<t:%d:F>", unixTimestamp);
                            staffChannel.sendMessage(String.format("⚠️ <@%s> strike count is now 2. Moved to temporary demotion, roles back %s.", 
                                    user.getId(), discordTimestamp)).queue();
                        }
                    }
                }
            }

            event.getHook().editOriginal("✅ Successfully removed strike #" + strikeNumber + " from " + user.getAsMention()).queue();
        } else {
            event.getHook().editOriginal("❌ Could not find strike #" + strikeNumber + " for " + user.getAsMention()).queue();
        }
    }

    private void handleClearStrikes(SlashCommandInteractionEvent event) {
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        if (user.getId().equals(event.getUser().getId())) {
            event.reply("❌ You cannot clear your own strikes.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        int strikeCount = strikeService.getStrikes(user.getId()).size();
        
        List<String> roleIds = demotionService.getDatabase().getTemporaryDemotionRoles(user.getId());
        strikeService.clearStrikes(user.getId());
        demotionService.removeDemotion(user.getId(), event.getJDA());
        if (bot.getRoleRestorationService() != null && !roleIds.isEmpty()) {
            bot.getRoleRestorationService().restoreRolesForUser(user.getId(), roleIds, false);
        }
        sendStaffClearStrikesLog(event, user, strikeCount);
        event.getHook().editOriginal("✅ Successfully cleared all strikes for " + user.getAsMention()).queue();
    }

    private void sendStaffClearStrikesLog(SlashCommandInteractionEvent event, User user, int strikeCount) {
        String actionId = generateActionId();
        long unixTimestamp = System.currentTimeMillis() / 1000;
        String discordTimestamp = String.format("<t:%d:F>", unixTimestamp);
        
        EmbedBuilder detailedEmbed = new EmbedBuilder()
                .setTitle(BotConfig.TW_EMOJI_MENTION + " ALL STRIKES CLEARED")
                .setColor(new Color(0x00FF00))
                .setDescription("All strikes have been cleared from a server member")
                .addField("📋 Target User", user.getName() + "\n" + user.getId() + "\n" + user.getAsMention(), false)
                .addField("👮 Administrator", event.getMember().getEffectiveName() + "\n" + event.getMember().getAsMention(), false)
                .addField("📊 Strikes Cleared", strikeCount + " strikes removed", false)
                .addField("⚡ Action ID", actionId, false)
                .setFooter("Strikes Cleared")
                .setTimestamp(java.time.Instant.now())
                .setThumbnail(user.getEffectiveAvatarUrl());
        
        EmbedBuilder simpleEmbed = new EmbedBuilder()
                .setTitle(BotConfig.TW_EMOJI_MENTION + " All Strikes Cleared")
                .setColor(new Color(0x00FF00))
                .setDescription(String.format("All strikes have been cleared for <@%s> (%s).", user.getId(), user.getName()))
                .addField("User ID", user.getId(), false)
                .addField("Strikes Cleared", String.valueOf(strikeCount), false)
                .setFooter("Strike System")
                .setTimestamp(java.time.Instant.now());

        TextChannel loggingChannel = event.getJDA().getTextChannelById("1472814682245955627");
        if (loggingChannel != null) {
            loggingChannel.sendMessageEmbeds(detailedEmbed.build()).queue();
        }
        
        TextChannel staffStrikeChannel = event.getJDA().getTextChannelById(BotConfig.STAFF_STRIKE_LOG_CHANNEL_ID);
        if (staffStrikeChannel != null) {
            staffStrikeChannel.sendMessageEmbeds(simpleEmbed.build()).queue();
        }
    }

    private void sendStaffRemoveStrikeLog(SlashCommandInteractionEvent event, User user, int strikeNumber, String reason) {
        EmbedBuilder logEmbed = new EmbedBuilder()
                .setTitle("🗑️ Strike Removed")
                .setColor(new Color(0xFFAA00))
                .setDescription(String.format("Strike #%d has been removed from <@%s> (%s).", strikeNumber, user.getId(), user.getName()))
                .addField("User ID", user.getId(), false)
                .addField("Original Reason", reason, false)
                .setFooter("Strike System")
                .setTimestamp(java.time.Instant.now());

        TextChannel loggingChannel = event.getJDA().getTextChannelById("1472814682245955627");
        if (loggingChannel != null) {
            loggingChannel.sendMessageEmbeds(logEmbed.build()).queue();
        }
    }

    private void sendStaffEditStrikeLog(SlashCommandInteractionEvent event, User user, int strikeNumber, String oldReason, String newReason) {
        EmbedBuilder logEmbed = new EmbedBuilder()
                .setTitle("📝 Strike Edited")
                .setColor(new Color(0x3498DB))
                .setDescription(String.format("Strike #%d reason has been updated for <@%s> (%s).", strikeNumber, user.getId(), user.getName()))
                .addField("User ID", user.getId(), false)
                .addField("Previous Reason", oldReason, false)
                .addField("New Reason", newReason, false)
                .setFooter("Strike System")
                .setTimestamp(java.time.Instant.now());

        TextChannel loggingChannel = event.getJDA().getTextChannelById("1472814682245955627");
        if (loggingChannel != null) {
            loggingChannel.sendMessageEmbeds(logEmbed.build()).queue();
        }
    }

    private void handleEditStrike(SlashCommandInteractionEvent event) {
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        if (user.getId().equals(event.getUser().getId())) {
            event.reply("❌ You cannot edit your own strikes.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        int strikeNumber = Objects.requireNonNull(event.getOption("number")).getAsInt();
        String newReason = Objects.requireNonNull(event.getOption("newreason")).getAsString();

        List<Strike> strikes = strikeService.getStrikes(user.getId());
        String oldReason = null;
        if (strikeNumber >= 1 && strikeNumber <= strikes.size()) {
            oldReason = strikes.get(strikeNumber - 1).getReason();
        }

        try {
            strikeService.editStrike(user.getId(), strikeNumber, newReason);
            if (oldReason != null) {
                sendStaffEditStrikeLog(event, user, strikeNumber, oldReason, newReason);
            }
            event.getHook().editOriginal("✅ Successfully edited strike #" + strikeNumber + " for " + user.getAsMention()).queue();
        } catch (Exception e) {
            event.getHook().editOriginal("❌ Error editing strike: " + e.getMessage()).queue();
        }
    }
}
