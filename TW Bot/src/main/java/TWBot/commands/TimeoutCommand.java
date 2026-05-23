package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.FormatUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class TimeoutCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public TimeoutCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("timeout", "Timeout a user")
                .addOption(OptionType.USER, "user", "The user to timeout", true)
                .addOption(OptionType.STRING, "reason", "The reason for the timeout", true)
                .addOption(OptionType.STRING, "duration", "Duration (e.g., 1h, 7d, 30m)", false));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        String durationStr = event.getOption("duration") != null ? event.getOption("duration").getAsString() : "1h";

        long minutes = FormatUtils.parseDurationToMinutes(durationStr);
        if (minutes <= 0) {
            event.getHook().sendMessage("❌ Invalid duration format. Use formats like: 1h, 7d, 30m (max: 28d)").queue();
            return;
        }

        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!PermissionUtils.canModerate(event.getMember(), targetMember)) {
                event.getHook().sendMessage("❌ You cannot timeout this user due to role hierarchy.").queue();
                return;
            }

            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("❌ I cannot timeout this user due to role hierarchy.").queue();
                return;
            }

            targetMember.timeoutFor(Duration.ofMinutes(minutes)).reason(reason).queue(success -> {
                dataService.saveModAction(ModAction.ActionType.TIMEOUT, event.getUser().getId(), event.getUser().getName(),
                        targetUser.getId(), targetUser.getName(), reason, (int) minutes, 0);

                event.getHook().sendMessage("✅ User has been timed out successfully.").queue();
                
                bot.getLoggingService().logModAction(event.getGuild(), "User Timed Out", event.getUser(), targetUser, reason, durationStr, Color.RED);
            }, error -> {
                event.getHook().sendMessage("❌ Error: Could not timeout the user. " + error.getMessage()).queue();
            });
        }, error -> event.getHook().sendMessage("❌ Error: Cannot find the user in this server.").queue());
    }
}
