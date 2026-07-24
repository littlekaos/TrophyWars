package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.models.WarnRecord;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WarnCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public WarnCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("warn", "Issue a warning to a user")
                .addOption(OptionType.USER, "user", "The user to warn", true)
                .addOption(OptionType.STRING, "reason", "The reason for the warning", true)
                .addOption(OptionType.ATTACHMENT, "evidence", "Optional evidence attachment", false));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        net.dv8tion.jda.api.entities.Message.Attachment evidence = event.getOption("evidence") != null ?
                event.getOption("evidence").getAsAttachment() : null;

        Map<String, Object> params = new HashMap<>();
        params.put("targetId", targetUser.getId());
        params.put("targetName", targetUser.getName());
        params.put("reason", reason);
        if (evidence != null) {
            params.put("evidenceUrl", evidence.getProxyUrl());
            params.put("evidenceFileName", evidence.getFileName());
        }

        // Execute directly without approval
        event.deferReply().setEphemeral(true).queue();
        
        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!PermissionUtils.canModerate(event.getMember(), targetMember)) {
                event.getHook().editOriginal("❌ You cannot warn this user due to role hierarchy.").queue();
                return;
            }
            
            doWarnManual(event, targetUser.getId(), targetUser.getName(), reason, 
                    evidence != null ? evidence.getProxyUrl() : null, 
                    evidence != null ? evidence.getFileName() : null);
        }, error -> {
            doWarnManual(event, targetUser.getId(), targetUser.getName(), reason, 
                    evidence != null ? evidence.getProxyUrl() : null, 
                    evidence != null ? evidence.getFileName() : null);
        });
    }

    private void doWarnManual(SlashCommandInteractionEvent event, String targetId, String targetName, String reason, String evidenceUrl, String evidenceFileName) {
        WarnRecord warnRecord = new WarnRecord(
                targetId,
                event.getUser().getId(),
                reason,
                System.currentTimeMillis()
        );
        dataService.addWarning(warnRecord);
        int currentWarnings = dataService.getWarningsForUser(targetId).size();

        dataService.saveModAction(ModAction.ActionType.WARN, event.getUser().getId(), event.getUser().getName(),
                targetId, targetName, reason, 0, currentWarnings);

        bot.getJda().retrieveUserById(targetId).queue(targetUser -> {
            EmbedBuilder warnEmbed = new EmbedBuilder()
                    .setTitle("⚠️ Warning Issued")
                    .setDescription(String.format("A warning has been issued to %s (%s)\n", targetUser.getAsMention(), targetUser.getName()))
                    .addField("User ID", targetId, false)
                    .addField("Reason", reason, false)
                    .addField("Total Warnings", String.valueOf(currentWarnings), false)
                    .addField("Moderator", event.getUser().getName(), false)
                    .setColor(Color.YELLOW)
                    .setTimestamp(Instant.now())
                    .setThumbnail(targetUser.getEffectiveAvatarUrl());

            targetUser.openPrivateChannel().queue(channel -> {
                EmbedBuilder userWarnEmbed = new EmbedBuilder()
                        .setTitle("⚠️ You've Received a Warning")
                        .setDescription(String.format("You have been warned in **%s**\n", event.getGuild().getName()))
                        .addField("Reason", reason, false)
                        .setColor(Color.YELLOW)
                        .setTimestamp(Instant.now());
                channel.sendMessageEmbeds(userWarnEmbed.build()).queue(s -> {}, e -> {});
            });

            event.getHook().editOriginal("✅ Warning issued successfully.").setEmbeds().setComponents().queue();
            
            bot.getLoggingService().logModAction(event.getGuild(), "User Warned", event.getUser(), targetUser, reason, null, Color.YELLOW);
        });
    }

    public void proceedWithWarn(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, Map<String, Object> params, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        String targetId = (String) params.get("targetId");
        String targetName = (String) params.get("targetName");
        String reason = (String) params.get("reason");
        String evidenceUrl = (String) params.get("evidenceUrl");
        String evidenceFileName = (String) params.get("evidenceFileName");

        guild.retrieveMemberById(targetId).queue(targetMember -> {
            if (!PermissionUtils.canModerate(moderator, targetMember)) {
                if (hook != null) hook.editOriginal("❌ You cannot warn this user due to role hierarchy.").setEmbeds().setComponents().queue();
                return;
            }
            doWarn(guild, moderator, targetId, targetName, reason, evidenceUrl, evidenceFileName, hook);
        }, error -> doWarn(guild, moderator, targetId, targetName, reason, evidenceUrl, evidenceFileName, hook));
    }

    private void doWarn(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, String targetId, String targetName, String reason, String evidenceUrl, String evidenceFileName, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        WarnRecord warnRecord = new WarnRecord(
                targetId,
                moderator.getUser().getId(),
                reason,
                System.currentTimeMillis()
        );
        dataService.addWarning(warnRecord);
        int currentWarnings = dataService.getWarningsForUser(targetId).size();

        dataService.saveModAction(ModAction.ActionType.WARN, moderator.getUser().getId(), moderator.getUser().getName(),
                targetId, targetName, reason, 0, currentWarnings);

        bot.getJda().retrieveUserById(targetId).queue(targetUser -> {
            EmbedBuilder warnEmbed = new EmbedBuilder()
                    .setTitle("⚠️ Warning Issued")
                    .setDescription(String.format("A warning has been issued to %s (%s)\n", targetUser.getAsMention(), targetUser.getName()))
                    .addField("User ID", targetId, false)
                    .addField("Reason", reason, false)
                    .addField("Total Warnings", String.valueOf(currentWarnings), false)
                    .addField("Moderator", moderator.getUser().getName(), false)
                    .setColor(Color.YELLOW)
                    .setTimestamp(Instant.now())
                    .setThumbnail(targetUser.getEffectiveAvatarUrl());

            targetUser.openPrivateChannel().queue(channel -> {
                EmbedBuilder userWarnEmbed = new EmbedBuilder()
                        .setTitle("⚠️ You've Received a Warning")
                        .setDescription(String.format("You have been warned in **%s**\n", guild.getName()))
                        .addField("Reason", reason, false)
                        .setColor(Color.YELLOW)
                        .setTimestamp(Instant.now());
                channel.sendMessageEmbeds(userWarnEmbed.build()).queue(s -> {}, e -> {});
            });

            if (hook != null) hook.editOriginal("✅ Warning issued successfully.").setEmbeds().setComponents().queue();
            
            bot.getLoggingService().logModAction(guild, "User Warned", moderator.getUser(), targetUser, reason, null, Color.YELLOW);
        });
    }
}
