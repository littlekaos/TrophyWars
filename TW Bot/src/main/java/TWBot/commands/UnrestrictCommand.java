package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class UnrestrictCommand implements Command {
    private final TWBot bot;

    public UnrestrictCommand(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("unrestrict", "Remove a restriction from a channel")
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "The channel to unrestrict", true)
                        .setChannelTypes(ChannelType.TEXT))
                .addOptions(new OptionData(OptionType.STRING, "type", "The type of restriction to remove", true)
                        .addChoice("Media With Text", "MEDIA_WITH_TEXT")
                        .addChoice("Media Only", "MEDIA_ONLY")
                        .addChoice("Screenshot Only", "SCREENSHOT_ONLY")
                        .addChoice("Text Only", "TEXT_ONLY")
                        .addChoice("No Media", "NO_MEDIA")
                        .addChoice("No Content", "NO_CONTENT")
                        .addChoice("No Message", "NO_MESSAGE")));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isAdmin(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        GuildChannelUnion channel = event.getOption("channel").getAsChannel();
        String type = event.getOption("type").getAsString();

        bot.getRestrictionService().removeRestriction(channel.getId(), type);
        bot.getDataService().saveModAction(ModAction.ActionType.UNRESTRICT, event.getUser().getId(), event.getUser().getName(),
                channel.getId(), channel.getName(), "Removed Restriction: " + type, 0, 0);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔓 Channel Restriction Removed")
                .setDescription(String.format("Restriction **%s** has been removed from %s.\n", type, channel.getAsMention()))
                .setColor(Color.YELLOW)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}
