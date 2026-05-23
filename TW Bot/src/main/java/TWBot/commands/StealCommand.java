package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StealCommand implements Command {

    private final TWBot bot;

    public StealCommand(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("steal", "Steal an emoji and add it to the server")
                .addOption(OptionType.STRING, "emoji", "The emoji to steal (paste the emoji or emoji ID)", true)
                .addOption(OptionType.STRING, "name", "Custom name for the emoji (optional)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String emojiInput = Objects.requireNonNull(event.getOption("emoji")).getAsString();
        String customName = event.getOption("name") != null ? event.getOption("name").getAsString() : null;

        try {
            String emojiId = parseEmojiId(emojiInput);
            if (emojiId == null) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid emoji format. Please provide a custom emoji."
                )).queue();
                return;
            }

            RichCustomEmoji sourceEmoji = null;

            for (RichCustomEmoji emoji : bot.getJda().getEmojis()) {
                if (emoji.getId().equals(emojiId)) {
                    sourceEmoji = emoji;
                    break;
                }
            }

            if (sourceEmoji != null) {
                String finalName = customName != null ? customName : sourceEmoji.getName();
                downloadAndCreateEmoji(event, sourceEmoji, finalName);
            } else {
                String finalName = customName != null ? customName : "stolen_emoji";
                downloadAndCreateEmojiFromUrl(event, emojiId, finalName);
            }

        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "An error occurred while stealing the emoji: " + e.getMessage()
            )).queue();
        }
    }

    private String parseEmojiId(String input) {
        Pattern mentionPattern = Pattern.compile("<a?:([a-zA-Z0-9_]+):(\\d+)>");
        Matcher mentionMatcher = mentionPattern.matcher(input);

        if (mentionMatcher.find()) {
            return mentionMatcher.group(2);
        }

        if (input.matches("\\d+")) {
            return input;
        }

        Pattern unicodePattern = Pattern.compile("\\p{So}|\\p{Sk}");
        Matcher unicodeMatcher = unicodePattern.matcher(input);
        if (unicodeMatcher.find()) {
            String emojiChar = unicodeMatcher.group();
            for (RichCustomEmoji emoji : bot.getJda().getEmojis()) {
                if (emoji.getName().equals(emojiChar) || emoji.getAsMention().contains(emojiChar)) {
                    return emoji.getId();
                }
            }
        }

        return null;
    }

    private void downloadAndCreateEmoji(SlashCommandInteractionEvent event, RichCustomEmoji sourceEmoji, String name) {
        try {
            String imageUrl = sourceEmoji.getImageUrl();
            if (imageUrl == null) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Could not get the emoji image URL."
                )).queue();
                return;
            }

            downloadAndCreateFromUrl(event, imageUrl, name);

        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Error downloading emoji: " + e.getMessage()
            )).queue();
        }
    }

    private void downloadAndCreateEmojiFromUrl(SlashCommandInteractionEvent event, String emojiId, String name) {
        try {
            String gifUrl = "https://cdn.discordapp.com/emojis/" + emojiId + ".gif";
            String pngUrl = "https://cdn.discordapp.com/emojis/" + emojiId + ".png";

            try {
                downloadAndCreateFromUrl(event, gifUrl, name);
            } catch (Exception gifException) {
                downloadAndCreateFromUrl(event, pngUrl, name);
            }

        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Error downloading emoji: " + e.getMessage()
            )).queue();
        }
    }

    private void downloadAndCreateFromUrl(SlashCommandInteractionEvent event, String imageUrl, String name) throws Exception {
        URL url = new URL(imageUrl);
        InputStream inputStream = url.openStream();
        byte[] imageData = inputStream.readAllBytes();
        inputStream.close();

        event.getGuild().createEmoji(name, net.dv8tion.jda.api.entities.Icon.from(imageData)).queue(
                newEmoji -> event.getHook().sendMessageEmbeds(EmbedUtils.createEmbed(
                        "✅ Emoji Stolen!",
                        Color.GREEN,
                        "Successfully added **" + name + "** " + newEmoji.getAsMention() + " to the server!"
                )).queue(),
                failure -> event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Failed to create emoji: " + failure.getMessage()
                )).queue()
        );
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig()) || event.getUser().getId().equals(BotConfig.OWNER_USER_ID);
    }
}
