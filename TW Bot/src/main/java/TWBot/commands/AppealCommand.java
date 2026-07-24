package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.models.Appeal;
import TWBot.models.Strike;
import TWBot.services.AppealService;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class AppealCommand implements Command {

    private final TWBot bot;
    private final AppealService appealService;

    public AppealCommand(TWBot bot) {
        this.bot = bot;
        this.appealService = bot.getAppealService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("appeal", "Appeal up to 2 of your strikes at once.")
                        .addOption(OptionType.INTEGER, "strike1", "First strike number to appeal (1 = first strike)", true)
                        .addOption(OptionType.STRING, "reason1", "Reason for appealing the first strike", true)
                        .addOption(OptionType.INTEGER, "strike2", "Second strike number to appeal (optional)", false)
                        .addOption(OptionType.STRING, "reason2", "Reason for appealing the second strike (required if strike2 is provided)", false)
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
                        .setContexts(InteractionContextType.GUILD),
                Commands.slash("myappeals", "View your strike appeals.")
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
                        .setContexts(InteractionContextType.GUILD),
                Commands.slash("pendingappeals", "View all pending appeals. (Staff Only)")
                        .setContexts(InteractionContextType.GUILD),
                Commands.slash("reviewappeal", "Review a pending appeal. (Staff Only)")
                        .addOption(OptionType.INTEGER, "appealid", "Appeal ID to review", true)
                        .addOption(OptionType.STRING, "decision", "approve or deny", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the decision", true)
                        .setContexts(InteractionContextType.GUILD),
                Commands.slash("undoappeal", "Reset a user's appeal so they can appeal again. (Admin Only)")
                        .addOption(OptionType.USER, "user", "User whose appeal to reset", true)
                        .setContexts(InteractionContextType.GUILD)
        );
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        String subcommand = event.getSubcommandName();
        return switch (Objects.requireNonNull(subcommand)) {
            case "appeal", "myappeals" -> true;
            case "pendingappeals", "reviewappeal" -> PermissionUtils.isModerator(event.getMember(), bot.getConfig());
            case "undoappeal" -> PermissionUtils.isAdmin(event.getMember(), bot.getConfig());
            default -> false;
        };
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        switch (commandName) {
            case "appeal" -> handleAppeal(event);
            case "myappeals" -> handleMyAppeals(event);
            case "pendingappeals" -> handlePendingAppeals(event);
            case "reviewappeal" -> handleReviewAppeal(event);
            case "undoappeal" -> handleUndoAppeal(event);
        }
    }

    private void handleAppeal(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        int strike1 = Objects.requireNonNull(event.getOption("strike1")).getAsInt();
        String reason1 = Objects.requireNonNull(event.getOption("reason1")).getAsString();
        Integer strike2 = event.getOption("strike2") != null ? event.getOption("strike2").getAsInt() : null;
        String reason2 = event.getOption("reason2") != null ? event.getOption("reason2").getAsString() : null;

        String userId = event.getUser().getId();

        if (strike2 != null && reason2 == null) {
            event.getHook().editOriginal("❌ You must provide a reason for the second strike appeal.").queue();
            return;
        }

        boolean success;
        if (strike2 != null) {
            success = appealService.createMultipleAppeals(userId, new int[]{strike1, strike2}, new String[]{reason1, reason2});
        } else {
            success = appealService.createAppeal(userId, strike1, reason1);
        }

        if (success) {
            List<Strike> strikes = bot.getStrikeService().getStrikes(userId);
            List<Appeal> userAppeals = appealService.getUserAppeals(userId);
            
            int activeCount = appealService.getActiveAppealCount(userId);
            String description = "Your appeal" + (strike2 != null ? "s have" : " has") + " been submitted and " + (strike2 != null ? "are" : "is") + " pending review.";
            
            if (activeCount == 1) {
                description += "\n\n⚠️ **You can appeal 1 more strike, but this is your only appeal chance!**";
            } else {
                description += "\n\n⚠️ **This was your final appeal - you've used your one appeal chance!**";
            }

            EmbedBuilder responseEmbed = new EmbedBuilder()
                    .setTitle("📩 Appeal" + (strike2 != null ? "s" : "") + " Submitted")
                    .setColor(Color.BLUE)
                    .setDescription(description + "\n")
                    .addField("Active Appeals", activeCount + "/2", false)
                    .addField("Status", "Pending Review", false);

            if (strike2 != null) {
                Strike s1 = strikes.get(strike1 - 1);
                Strike s2 = strikes.get(strike2 - 1);
                
                responseEmbed.addField("Strike #" + strike1, s1.getReason(), false);
                responseEmbed.addField("Appeal Reason", reason1, false);
                responseEmbed.addField("Strike #" + strike2, s2.getReason(), false);
                responseEmbed.addField("Appeal Reason", reason2, false);
                
                Appeal a1 = findCorrespondingAppeal(userAppeals, s1.getId());
                Appeal a2 = findCorrespondingAppeal(userAppeals, s2.getId());
                
                if (a1 != null) notifyStaffOfAppeal(event.getUser(), s1, reason1, a1.getId());
                if (a2 != null) notifyStaffOfAppeal(event.getUser(), s2, reason2, a2.getId());
            } else {
                Strike s1 = strikes.get(strike1 - 1);
                responseEmbed.addField("Strike #" + strike1, s1.getReason(), false);
                responseEmbed.addField("Appeal Reason", reason1, false);
                
                Appeal a1 = findCorrespondingAppeal(userAppeals, s1.getId());
                if (a1 != null) notifyStaffOfAppeal(event.getUser(), s1, reason1, a1.getId());
            }
            
            responseEmbed.setFooter("Strike Appeal System", null)
                    .setTimestamp(java.time.Instant.now());
            
            event.getHook().editOriginalEmbeds(responseEmbed.build()).queue();
        } else {
            event.getHook().editOriginal("❌ Could not submit appeal. Ensure you haven't exceeded the appeal limit, haven't already appealed these strikes, and that the strike numbers are valid.").queue();
        }
    }

    private Appeal findCorrespondingAppeal(List<Appeal> appeals, int strikeId) {
        return appeals.stream()
                .filter(a -> a.getStrikeId() == strikeId && a.getStatus().equals("PENDING"))
                .findFirst()
                .orElse(null);
    }

    private void notifyStaffOfAppeal(User user, Strike strike, String reason, int appealId) {
        TextChannel staffChannel = bot.getJda().getTextChannelById(BotConfig.STAFF_CHAT_CHANNEL_ID);
        if (staffChannel != null) {
            long unixTimestamp = System.currentTimeMillis() / 1000;
            String discordTimestamp = String.format("<t:%d:F>", unixTimestamp);
            
            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setTitle(BotConfig.TW_EMOJI_MENTION + " STRIKE APPEALED")
                    .setColor(new Color(0x4169E1))
                    .setDescription("A user has submitted a strike appeal")
                    .addField("📋 User",
                            String.format("**%s**\n`%s`\n<@%s>",
                                    user.getName(),
                                    user.getId(),
                                    user.getId()), false)
                    .addField("🆔 Appeal ID",
                            String.format("`%d`", appealId), false)
                    .addField("📝 Original Strike",
                            String.format("```%s```", strike.getReason()), false)
                    .addField("💬 Appeal Reason",
                            String.format("```%s```", reason), false)
                    .addField("⚡ Action ID", String.format("`%s`", generateActionId()), false)
                    .addField("🛠️ Action Required",
                            String.format("Use `/reviewappeal %d approve/deny reason`", appealId), false)
                    .setThumbnail(user.getEffectiveAvatarUrl())
                    .setFooter("Strike Appealed")
                    .setTimestamp(java.time.Instant.now());

            String rolePing = String.format("<@&%s> <@&%s>", BotConfig.OVERSEER_ROLE_ID, BotConfig.MANAGER_ROLE_ID);
            staffChannel.sendMessage(rolePing).setEmbeds(logEmbed.build()).queue();
        }
    }

    private void handleMyAppeals(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        List<Appeal> appeals = appealService.getUserAppeals(event.getUser().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📝 Your Strike Appeals")
                .setColor(Color.CYAN)
                .setTimestamp(java.time.Instant.now());

        if (appeals.isEmpty()) {
            embed.setDescription("You haven't submitted any appeals yet.");
        } else {
            for (Appeal appeal : appeals) {
                Strike strike = appealService.getStrikeById(appeal.getStrikeId());
                String strikeInfo = strike != null ? strike.getReason() : "Unknown Strike";
                embed.addField("Appeal #" + appeal.getId(),
                        "**Status:** " + appeal.getStatus() + "\n" +
                                "**Strike:** " + strikeInfo + "\n" +
                                "**Reason:** " + appeal.getReason() + (appeal.getReviewReason() != null ? "\n**Staff Note:** " + appeal.getReviewReason() : ""),
                        false);
            }
        }

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handlePendingAppeals(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        List<Appeal> pending = appealService.getAllPendingAppeals();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⏳ Pending Strike Appeals")
                .setColor(Color.ORANGE)
                .setTimestamp(java.time.Instant.now());

        if (pending.isEmpty()) {
            embed.setDescription("There are no pending appeals at the moment.");
        } else {
            for (Appeal appeal : pending) {
                Strike strike = appealService.getStrikeById(appeal.getStrikeId());
                String strikeInfo = strike != null ? strike.getReason() : "Unknown Strike";
                embed.addField("Appeal ID: " + appeal.getId(),
                        "**User:** <@" + appeal.getUserId() + ">\n" +
                                "**Strike Reason:** " + strikeInfo + "\n" +
                                "**Appeal Reason:** " + appeal.getReason(),
                        false);
            }
        }

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleReviewAppeal(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        int appealId = Objects.requireNonNull(event.getOption("appealid")).getAsInt();
        String decision = Objects.requireNonNull(event.getOption("decision")).getAsString().toLowerCase();
        String reason = Objects.requireNonNull(event.getOption("reason")).getAsString();

        Appeal appealCheck = appealService.getAppealById(appealId);
        if (appealCheck != null && appealCheck.getUserId().equals(event.getUser().getId())) {
            event.getHook().editOriginal("❌ You cannot review your own appeal.").queue();
            return;
        }

        boolean success;
        if (decision.equals("approve")) {
            success = appealService.approveAppeal(appealId, event.getUser().getId(), reason);
        } else if (decision.equals("deny")) {
            success = appealService.denyAppeal(appealId, event.getUser().getId(), reason);
        } else {
            event.getHook().editOriginal("❌ Decision must be 'approve' or 'deny'.").queue();
            return;
        }

        if (success) {
            event.getHook().editOriginal("✅ Appeal #" + appealId + " has been " + (decision.equals("approve") ? "approved" : "denied") + ".").queue();
            
            Appeal appeal = appealService.getAppealById(appealId);
            if (appeal != null) {
                // Log the review
                logAppealReviewed(event, appeal, decision, reason);
                
                bot.getJda().retrieveUserById(appeal.getUserId()).queue(user -> {
                    user.openPrivateChannel().queue(pc -> {
                        EmbedBuilder dmEmbed = new EmbedBuilder()
                                .setTitle("🔔 Appeal Reviewed")
                                .setColor(decision.equals("approve") ? Color.GREEN : Color.RED)
                                .setDescription("Your strike appeal #" + appealId + " has been **" + decision.toUpperCase() + "**.")
                                .addField("Staff Note", reason, false)
                                .setFooter("Strike Appeal System", null)
                                .setTimestamp(java.time.Instant.now());
                        pc.sendMessageEmbeds(dmEmbed.build()).queue(null, err -> {});
                    });
                });
            }
        } else {
            event.getHook().editOriginal("❌ Could not review appeal. Ensure the ID is correct and the appeal is still pending.").queue();
        }
    }

    private void handleUndoAppeal(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();

        boolean success = appealService.resetUserAppeals(user.getId());
        if (success) {
            logAppealReset(event, user);
            event.getHook().editOriginal("✅ Successfully reset appeals for " + user.getAsMention() + ". They can now appeal again.").queue();
        } else {
            event.getHook().editOriginal("❌ Could not reset appeals for " + user.getAsMention() + ".").queue();
        }
    }

    private void logAppealReviewed(SlashCommandInteractionEvent event, Appeal appeal, String decision, String reason) {
        TextChannel logChannel = bot.getJda().getTextChannelById(BotConfig.STAFF_STRIKES_CHANNEL_ID);
        if (logChannel == null) return;

        Strike strike = appealService.getStrikeById(appeal.getStrikeId());
        boolean approved = decision.equalsIgnoreCase("approve");

        bot.getJda().retrieveUserById(appeal.getUserId()).queue(user -> {
            EmbedBuilder logEmbed = new EmbedBuilder()
                    .setTitle(approved ? "✅ Appeal Approved" : "❌ Appeal Denied")
                    .setColor(approved ? Color.GREEN : Color.RED)
                    .setDescription(String.format("Appeal #%d for <@%s> (%s) has been %s.", 
                            appeal.getId(), user.getId(), user.getName(), decision.toUpperCase()))
                    .addField("User ID", user.getId(), false)
                    .addField("Original Strike", strike != null ? strike.getReason() : "Unknown", false)
                    .addField("User's Appeal", appeal.getReason(), false)
                    .addField("Staff Decision", reason, false)
                    .setFooter("Strike System")
                    .setTimestamp(java.time.Instant.now());

            logChannel.sendMessageEmbeds(logEmbed.build()).queue();
        });
    }

    private void logAppealReset(SlashCommandInteractionEvent event, User targetUser) {
        TextChannel logChannel = bot.getJda().getTextChannelById(BotConfig.STAFF_STRIKES_CHANNEL_ID);
        if (logChannel == null) return;

        EmbedBuilder logEmbed = new EmbedBuilder()
                .setTitle("🔄 Appeals Reset")
                .setColor(Color.ORANGE)
                .setDescription(String.format("Appeals have been reset for <@%s> (%s).", targetUser.getId(), targetUser.getName()))
                .addField("User ID", targetUser.getId(), false)
                .addField("Result", "User can now submit appeals again", false)
                .setFooter("Strike System")
                .setTimestamp(java.time.Instant.now());

        logChannel.sendMessageEmbeds(logEmbed.build()).queue();
    }

    private String generateActionId() {
        return "MOD-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
    }
}
