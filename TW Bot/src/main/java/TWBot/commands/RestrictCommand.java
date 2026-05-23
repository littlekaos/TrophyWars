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

public class RestrictCommand implements Command {
    private final TWBot bot;

    public RestrictCommand(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("restrict", "Apply specific content restrictions to a channel")
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "The channel to restrict", true)
                        .setChannelTypes(ChannelType.TEXT))
                .addOptions(new OptionData(OptionType.STRING, "type", "The type of restriction", true)
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

        bot.getRestrictionService().addRestriction(channel.getId(), type);
        bot.getDataService().saveModAction(ModAction.ActionType.RESTRICT, event.getUser().getId(), event.getUser().getName(),
                channel.getId(), channel.getName(), "Restriction: " + type, 0, 0);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Channel Restriction Added")
                .setDescription(String.format("Restriction **%s** has been added to %s.\n", type, channel.getAsMention()))
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}
