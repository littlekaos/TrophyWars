package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
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
import java.util.concurrent.TimeUnit;

public class BanCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public BanCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("ban", "Ban a user from the server")
                .addOption(OptionType.USER, "user", "The user to ban", true)
                .addOption(OptionType.STRING, "reason", "The reason for the ban", true)
                .addOption(OptionType.INTEGER, "delete_days", "Number of days of messages to delete (0-7, defaults to 0)", false));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        int deleteDays = event.getOption("delete_days") != null ? event.getOption("delete_days").getAsInt() : 0;

        if (deleteDays < 0 || deleteDays > 7) {
            event.reply("❌ Error: delete_days must be between 0 and 7").setEphemeral(true).queue();
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("targetId", targetUser.getId());
        params.put("targetName", targetUser.getName());
        params.put("reason", reason);
        params.put("deleteDays", deleteDays);

        // Execute directly without approval
        event.deferReply().setEphemeral(true).queue();
        
        bot.getJda().retrieveUserById(targetUser.getId()).queue(user -> {
            event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
                if (!PermissionUtils.canModerate(event.getMember(), targetMember)) {
                    event.getHook().editOriginal("❌ You cannot ban this user due to role hierarchy.").queue();
                    return;
                }

                if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                    event.getHook().editOriginal("❌ I cannot ban this user due to role hierarchy.").queue();
                    return;
                }
                doBanManual(event, user, reason, deleteDays);
            }, error -> doBanManual(event, user, reason, deleteDays));
        }, error -> event.getHook().editOriginal("❌ Error: User not found.").queue());
    }

    private void doBanManual(SlashCommandInteractionEvent event, User targetUser, String reason, int deleteDays) {
        event.getGuild().ban(targetUser, deleteDays, TimeUnit.DAYS).reason(reason).queue(success -> {
            dataService.saveModAction(ModAction.ActionType.BAN, event.getUser().getId(), event.getUser().getName(),
                    targetUser.getId(), targetUser.getName(), reason, deleteDays, 0);

            event.getHook().editOriginal("✅ User has been banned successfully.").setEmbeds().setComponents().queue();
            
            String deleteDaysStr = deleteDays + " days";
            bot.getLoggingService().logModAction(event.getGuild(), "User Banned", event.getUser(), targetUser, reason, null, Color.RED, "Message History Deleted", deleteDaysStr);
        }, error -> {
            event.getHook().editOriginal("❌ Error: Could not ban the user. " + error.getMessage()).setEmbeds().setComponents().queue();
        });
    }

    public void proceedWithBan(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, Map<String, Object> params, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        String targetId = (String) params.get("targetId");
        String targetName = (String) params.get("targetName");
        String reason = (String) params.get("reason");
        int deleteDays = (int) params.get("deleteDays");

        bot.getJda().retrieveUserById(targetId).queue(targetUser -> {
            guild.retrieveMemberById(targetId).queue(targetMember -> {
                if (!PermissionUtils.canModerate(moderator, targetMember)) {
                    if (hook != null) hook.editOriginal("❌ You cannot ban this user due to role hierarchy.").setEmbeds().setComponents().queue();
                    return;
                }

                if (!guild.getSelfMember().canInteract(targetMember)) {
                    if (hook != null) hook.editOriginal("❌ I cannot ban this user due to role hierarchy.").setEmbeds().setComponents().queue();
                    return;
                }
                doBan(guild, moderator, targetUser, reason, deleteDays, hook);
            }, error -> doBan(guild, moderator, targetUser, reason, deleteDays, hook));
        }, error -> {
            if (hook != null) hook.editOriginal("❌ Error: User not found.").setEmbeds().setComponents().queue();
        });
    }

    private void doBan(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, User targetUser, String reason, int deleteDays, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        guild.ban(targetUser, deleteDays, TimeUnit.DAYS).reason(reason).queue(success -> {
            dataService.saveModAction(ModAction.ActionType.BAN, moderator.getUser().getId(), moderator.getUser().getName(),
                    targetUser.getId(), targetUser.getName(), reason, deleteDays, 0);

            if (hook != null) hook.editOriginal("✅ User has been banned successfully.").setEmbeds().setComponents().queue();
            
            String deleteDaysStr = deleteDays + " days";
            bot.getLoggingService().logModAction(guild, "User Banned", moderator.getUser(), targetUser, reason, null, Color.RED, "Message History Deleted", deleteDaysStr);
        }, error -> {
            if (hook != null) hook.editOriginal("❌ Error: Could not ban the user. " + error.getMessage()).setEmbeds().setComponents().queue();
        });
    }
}
