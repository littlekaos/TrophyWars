package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class KickCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public KickCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("kick", "Kick a user from the server")
                .addOption(OptionType.USER, "user", "The user to kick", true)
                .addOption(OptionType.STRING, "reason", "The reason for the kick", true));
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

        Guild guild = event.getGuild();

        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!PermissionUtils.canModerate(event.getMember(), targetMember)) {
                event.getHook().sendMessage("❌ You cannot kick this user due to role hierarchy.").queue();
                return;
            }

            if (!guild.getSelfMember().canInteract(targetMember)) {
                event.getHook().sendMessage("❌ I cannot kick this user due to role hierarchy.").queue();
                return;
            }

            targetUser.openPrivateChannel().queue(dmChannel -> {
                EmbedBuilder dmEmbed = new EmbedBuilder()
                        .setTitle("👢 You've Been Kicked")
                        .setDescription(String.format("You have been kicked from **%s**\n", guild.getName()))
                        .addField("Reason", reason, false)
                        .setColor(Color.ORANGE)
                        .setTimestamp(Instant.now());
                dmChannel.sendMessageEmbeds(dmEmbed.build()).queue(s -> proceedWithKick(event, targetUser, reason), e -> proceedWithKick(event, targetUser, reason));
            }, e -> proceedWithKick(event, targetUser, reason));
        }, error -> event.getHook().sendMessage("❌ Error: Cannot find the user in this server.").queue());
    }

    private void proceedWithKick(SlashCommandInteractionEvent event, User targetUser, String reason) {
        event.getGuild().kick(targetUser).reason(reason).queue(success -> {
            dataService.saveModAction(ModAction.ActionType.KICK, event.getUser().getId(), event.getUser().getName(),
                    targetUser.getId(), targetUser.getName(), reason, 0, 0);

            event.getHook().sendMessage("✅ User **" + targetUser.getName() + "** has been kicked successfully.").queue();
            
            bot.getLoggingService().logModAction(event.getGuild(), "User Kicked", event.getUser(), targetUser, reason, null, Color.ORANGE);
        }, error -> {
            event.getHook().sendMessage("❌ Error: Could not kick the user. " + error.getMessage()).queue();
        });
    }
}
