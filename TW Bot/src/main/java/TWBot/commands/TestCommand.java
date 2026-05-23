package TWBot.commands;

import TWBot.config.BotConfig;
import TWBot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;

import java.util.List;

public class TestCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("test", "Check if the bot is working")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.replyEmbeds(EmbedUtils.createEmbed(
                Color.BLUE,
                BotConfig.TW_EMOJI_MENTION + " Trophy Wars bot is currently working!"
        )).setEphemeral(true).queue();
    }
}