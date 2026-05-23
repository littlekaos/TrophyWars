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

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class UntimeoutCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public UntimeoutCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("untimeout", "Remove a timeout from a user")
                .addOption(OptionType.USER, "user", "The user to untimeout", true)
                .addOption(OptionType.STRING, "reason", "The reason for removing the timeout", true));
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

        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!event.getMember().canInteract(targetMember)) {
                event.getHook().sendMessage("❌ You cannot remove the timeout from this user due to role hierarchy.").queue();
                return;
            }

            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("❌ I cannot remove the timeout from this user due to role hierarchy.").queue();
                return;
            }

            if (targetMember.getTimeOutEnd() == null) {
                event.getHook().sendMessage("❌ This user is not currently timed out.").queue();
                return;
            }

            targetMember.removeTimeout().reason(reason).queue(success -> {
                dataService.saveModAction(ModAction.ActionType.UNTIMEOUT, event.getUser().getId(), event.getUser().getName(),
                        targetUser.getId(), targetUser.getName(), reason, 0, 0);

                event.getHook().sendMessage("✅ User's timeout has been removed successfully.").queue();
                
                bot.getLoggingService().logModAction(event.getGuild(), "Timeout Removed", event.getUser(), targetUser, reason, null, Color.GREEN);
            }, error -> {
                event.getHook().sendMessage("❌ Error: Could not remove the timeout. " + error.getMessage()).queue();
            });
        }, error -> event.getHook().sendMessage("❌ Error: Cannot find the user in this server.").queue());
    }
}
