package TWBot.services;

import TWBot.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Backfills and continuously archives staff moderation channels into tw_message_logs
 * so Discord channel history is preserved inside tw-bot.db.
 */
public class StaffChannelArchiveService {
    private static final Logger logger = LoggerFactory.getLogger(StaffChannelArchiveService.class);

    private final DataService dataService;

    public StaffChannelArchiveService(DataService dataService) {
        this.dataService = dataService;
    }

    public void start(JDA jda) {
        for (Guild guild : jda.getGuilds()) {
            archiveGuild(guild);
        }
    }

    public void archiveGuild(Guild guild) {
        Set<TextChannel> channels = resolveArchiveChannels(guild);
        if (channels.isEmpty()) {
            logger.warn("[ChannelArchive] No archive channels found in guild {}", guild.getName());
            return;
        }

        logger.info("[ChannelArchive] Archiving {} staff/moderation channel(s) in {}", channels.size(), guild.getName());
        for (TextChannel channel : channels) {
            archiveChannel(channel);
        }
    }

    public boolean isArchiveChannel(String channelId) {
        return BotConfig.ARCHIVE_CHANNEL_IDS.contains(channelId);
    }

    public boolean isArchiveChannel(String channelId, String channelName) {
        if (channelId != null && BotConfig.ARCHIVE_CHANNEL_IDS.contains(channelId)) {
            return true;
        }
        if (channelName == null) return false;
        return BotConfig.ARCHIVE_CHANNEL_NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(channelName));
    }

    public boolean isArchiveChannel(TextChannel channel) {
        if (channel == null) return false;
        return isArchiveChannel(channel.getId(), channel.getName());
    }

    public void archiveMessageLive(Message message) {
        if (message == null || message.getGuild() == null) return;
        dataService.archiveMessage(
                message.getGuild().getId(),
                message.getChannel().getId(),
                message.getId(),
                message.getAuthor().getId(),
                formatMessageContent(message),
                "ARCHIVED",
                message.getTimeCreated().toInstant().toEpochMilli()
        );
    }

    private Set<TextChannel> resolveArchiveChannels(Guild guild) {
        Set<TextChannel> channels = new LinkedHashSet<>();

        for (String channelId : BotConfig.ARCHIVE_CHANNEL_IDS) {
            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel != null) {
                channels.add(channel);
            }
        }

        for (String name : BotConfig.ARCHIVE_CHANNEL_NAMES) {
            List<TextChannel> matches = guild.getTextChannelsByName(name, true);
            channels.addAll(matches);
        }

        return channels;
    }

    private void archiveChannel(TextChannel channel) {
        long latestKnown = dataService.getLatestArchivedTimestamp(channel.getId());
        AtomicInteger archived = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        logger.info("[ChannelArchive] Scanning #{} (latest in DB: {})",
                channel.getName(),
                latestKnown == 0 ? "none" : latestKnown);

        channel.getIterableHistory().forEachAsync(message -> {
            long messageTs = message.getTimeCreated().toInstant().toEpochMilli();

            // History is newest-first; stop once we reach already-archived history
            if (latestKnown > 0 && messageTs <= latestKnown) {
                return false;
            }

            if (dataService.hasMessageArchived(message.getId())) {
                skipped.incrementAndGet();
                return true;
            }

            dataService.archiveMessage(
                    channel.getGuild().getId(),
                    channel.getId(),
                    message.getId(),
                    message.getAuthor().getId(),
                    formatMessageContent(message),
                    "ARCHIVED",
                    messageTs
            );
            archived.incrementAndGet();
            return true;
        }).thenRun(() -> {
            int total = dataService.countArchivedMessages(channel.getId());
            logger.info("[ChannelArchive] #{} done — newly archived: {}, skipped: {}, total in DB: {}",
                    channel.getName(), archived.get(), skipped.get(), total);
        }).exceptionally(e -> {
            logger.error("[ChannelArchive] Failed scanning #{}: {}", channel.getName(), e.getMessage());
            return null;
        });
    }

    static String formatMessageContent(Message message) {
        StringBuilder sb = new StringBuilder();
        String raw = message.getContentRaw();
        if (raw != null && !raw.isBlank()) {
            sb.append(raw);
        }

        List<MessageEmbed> embeds = message.getEmbeds();
        if (!embeds.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            for (int i = 0; i < embeds.size(); i++) {
                MessageEmbed embed = embeds.get(i);
                sb.append("[EMBED ").append(i + 1).append("]");
                if (embed.getTitle() != null) {
                    sb.append(" title=").append(embed.getTitle());
                }
                if (embed.getDescription() != null) {
                    sb.append(" description=").append(embed.getDescription());
                }
                for (MessageEmbed.Field field : embed.getFields()) {
                    sb.append(" | ")
                            .append(field.getName() != null ? field.getName() : "")
                            .append("=")
                            .append(field.getValue() != null ? field.getValue() : "");
                }
                if (embed.getFooter() != null && embed.getFooter().getText() != null) {
                    sb.append(" footer=").append(embed.getFooter().getText());
                }
                sb.append("\n");
            }
        }

        if (!message.getAttachments().isEmpty()) {
            for (Message.Attachment attachment : message.getAttachments()) {
                sb.append("[ATTACHMENT] ").append(attachment.getUrl()).append("\n");
            }
        }

        String content = sb.toString().trim();
        return content.isEmpty() ? "(empty message)" : content;
    }
}
