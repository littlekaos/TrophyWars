package TWBot.events;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.models.ModAction;
import TWBot.services.RestrictionService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Arrays;
import java.util.List;

public class MessageEventListener extends ListenerAdapter {
    private final TWBot bot;

    public MessageEventListener(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        boolean isArchiveChannel = event.isFromGuild()
                && bot.getStaffChannelArchiveService() != null
                && bot.getStaffChannelArchiveService().isArchiveChannel(
                        event.getChannel().getId(),
                        event.getChannel().getName());

        if (event.getAuthor().isBot()) {
            // Still archive bot embeds/logs from staff moderation channels
            if (isArchiveChannel) {
                bot.getStaffChannelArchiveService().archiveMessageLive(event.getMessage());
            }

            if (event.isWebhookMessage()) {
            } else {
                String channelId = event.getChannel().getId();
                List<String> autoReactionChannels = bot.getConfig().getAutoReactionChannels();
                if (autoReactionChannels.contains(channelId)) {
                    addReactionsToMessage(event.getMessage());
                }
                return;
            }
        }

        bot.getUserCache().cacheUser(event.getAuthor());
        bot.getMessageCache().cacheMessage(event.getMessage(), event.getAuthor().getId());

        if (event.isFromGuild()) {
            if (isArchiveChannel) {
                bot.getStaffChannelArchiveService().archiveMessageLive(event.getMessage());
            } else {
                bot.getDataService().logMessage(
                        event.getGuild().getId(),
                        event.getChannel().getId(),
                        event.getMessageId(),
                        event.getAuthor().getId(),
                        event.getMessage().getContentRaw(),
                        "RECEIVED"
                );
            }
        }

        Member member = event.getMember();
        boolean isStaff = PermissionUtils.isModerator(member, bot.getConfig());
        
        if (!isStaff) {
            if (handleRestrictions(event)) return;
        }

        String channelId = event.getChannel().getId();
        String eventNameChannel = bot.getConfig().getEventNameChannelId();
        String botCommandsChannel = bot.getConfig().getBotCommandsChannelId();
        String content = event.getMessage().getContentRaw().toLowerCase();

        // Detect invalid event name submissions
        if (content.contains("eventname")) {
            boolean isIncorrectChannel = channelId.equals(eventNameChannel) || channelId.equals(botCommandsChannel);
            boolean isPrefixCommand = content.startsWith("-eventname") || content.startsWith("!eventname");

            if (isIncorrectChannel || isPrefixCommand) {
                event.getMessage().delete().queue(null, e -> {});
                event.getChannel().sendMessage(event.getAuthor().getAsMention() + " ⚠️ **THAT IS NOT HOW YOU SUBMIT EVENT NAMES!**\n" +
                                "TO SUBMIT NAMES: use the `/eventname submit` SLASH COMMAND within the channel. " +
                                "If you don't correct this, **__YOUR WINS WILL BE VOIDED.__**")
                        .queue(m -> m.delete().queueAfter(15, java.util.concurrent.TimeUnit.SECONDS));
                return;
            }
        }

        List<String> autoReactionChannels = bot.getConfig().getAutoReactionChannels();

        if (autoReactionChannels.contains(channelId)) {
            addReactionsToMessage(event.getMessage());
        }
    }

    private boolean handleRestrictions(MessageReceivedEvent event) {
        RestrictionService service = bot.getRestrictionService();
        String channelId = event.getChannel().getId();
        Message message = event.getMessage();

        if (service.getMediaWithTextChannels().contains(channelId)) {
            if (!hasMedia(message)) {
                deleteAndWarn(event, "Images and links are required in this channel.");
                return true;
            }
        }

        if (service.getMediaOnlyChannels().contains(channelId)) {
            if (!hasMedia(message) || !hasOnlyMedia(message)) {
                deleteAndWarn(event, "Only images and links are allowed in this channel (no regular chat messages).");
                return true;
            }
        }

        if (service.getScreenshotOnlyChannels().contains(channelId)) {
            if (message.getAttachments().isEmpty() || !message.getContentRaw().isEmpty() ||
                message.getAttachments().stream().anyMatch(a -> !a.isImage())) {
                deleteAndWarn(event, "Only images/screenshots are allowed in this channel (no text).");
                return true;
            }
        }

        if (service.getNoMessageChannels().contains(channelId)) {
            deleteAndWarn(event, "Messages are not allowed in this channel.");
            return true;
        }

        if (service.getTextOnlyChannels().contains(channelId)) {
            if (hasMedia(message)) {
                deleteAndWarn(event, "Only text messages are allowed in this channel (no media, links, or attachments).");
                return true;
            }
        }

        if (service.getNoMediaChannels().contains(channelId)) {
            if (hasMedia(message)) {
                deleteAndWarn(event, "Media and links are not allowed in this channel.");
                return true;
            }
        }

        if (service.getNoContentChannels().contains(channelId)) {
            deleteAndWarn(event, "No content is allowed in this channel.");
            return true;
        }

        return false;
    }

    private boolean hasMedia(Message message) {
        return !message.getAttachments().isEmpty() || containsLinks(message.getContentRaw());
    }

    private boolean hasOnlyMedia(Message message) {
        String content = message.getContentRaw().trim();
        return content.isEmpty() || containsOnlyLinks(content);
    }

    private boolean containsLinks(String content) {
        return content.matches("(?s).*https?://\\S+.*");
    }

    private boolean containsOnlyLinks(String content) {
        String[] words = content.split("\\s+");
        return Arrays.stream(words).filter(w -> !w.isEmpty()).allMatch(w -> w.matches("https?://\\S+.*"));
    }

    private void deleteAndWarn(MessageReceivedEvent event, String reason) {
        event.getMessage().delete().queue();
        event.getChannel().sendMessageEmbeds(EmbedUtils.createWarningEmbed(reason))
                .queue(m -> m.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));

        bot.getDataService().logMessage(event.getGuild().getId(), event.getChannel().getId(), 
                event.getMessageId(), event.getAuthor().getId(), event.getMessage().getContentRaw(), 
                "MODERATION_DELETE: " + reason);
        
        bot.getDataService().saveModAction(ModAction.ActionType.RESTRICT, bot.getJda().getSelfUser().getId(), 
                bot.getJda().getSelfUser().getName(), event.getAuthor().getId(), event.getAuthor().getName(), 
                "Auto-delete: " + reason, 0, 0);
    }

    private void addReactionsToMessage(Message message) {
        try {
            String channelId = message.getChannel().getId();
            BotConfig.EmojiConfig emojiConfig = bot.getConfig().getChannelEmojiConfig(channelId);

            if (emojiConfig == null) {
                System.err.println("No emoji configuration found for channel " + channelId);
                return;
            }

            Emoji emoji = null;

            if (emojiConfig.unicodeEmoji != null) {
                emoji = Emoji.fromUnicode(emojiConfig.unicodeEmoji);
            } else if (emojiConfig.customEmojiName != null && emojiConfig.customEmojiId != null) {
                emoji = Emoji.fromCustom(emojiConfig.customEmojiName, Long.parseLong(emojiConfig.customEmojiId), false);
            }

            if (emoji == null) {
                System.err.println("Could not create emoji for channel " + channelId);
                return;
            }

            message.addReaction(emoji).queue(
                    success -> System.out.println("Added reaction to message in channel " + channelId),
                    error -> System.err.println("Failed to add reaction to message: " + error.getMessage())
            );
        } catch (Exception e) {
            System.err.println("Error adding reactions to message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}