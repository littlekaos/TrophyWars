package TWBot.services;

import TWBot.config.BotConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.Color;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoggingService {
    private final Map<String, TextChannel> logChannelCache = new ConcurrentHashMap<>();

    // Hardcoded IDs for specific channels as requested
    private static final String MOD_LOG_CHANNEL_ID = BotConfig.MOD_LOG_CHANNEL_ID;
    private static final String STAFF_STRIKES_CHANNEL_ID = BotConfig.STAFF_STRIKE_LOG_CHANNEL_ID;

    public TextChannel getLogChannel(Guild guild, String channelName) {
        String cacheKey = guild.getId() + ":" + channelName;
        if (logChannelCache.containsKey(cacheKey)) {
            TextChannel cachedChannel = logChannelCache.get(cacheKey);
            if (cachedChannel != null && guild.getTextChannelById(cachedChannel.getId()) != null) {
                return cachedChannel;
            }
        }

        // Try to find by ID first for specific channels
        TextChannel logChannel = null;
        switch (channelName) {
            case "moderation-logs":
                logChannel = guild.getTextChannelById(MOD_LOG_CHANNEL_ID);
                break;
            case "staff-strikes":
                logChannel = guild.getTextChannelById(STAFF_STRIKES_CHANNEL_ID);
                break;
        }

        // Fallback to finding by name if ID lookup failed or for other channel names
        if (logChannel == null) {
            logChannel = guild.getTextChannelsByName(channelName, true).stream().findFirst().orElse(null);
        }

        if (logChannel != null) {
            logChannelCache.put(cacheKey, logChannel);
            return logChannel;
        }

        // Create channel if it still doesn't exist
        guild.createTextChannel(channelName).queue(newChannel -> {
            String topic;
            switch (channelName) {
                case "staff-strikes":
                    topic = "This channel logs all staff strike actions.";
                    break;
                case "moderation-logs":
                    topic = "This channel logs all moderation actions (warn, mute, timeout, etc).";
                    break;
                case "server-logs":
                    topic = "This channel logs all server events.";
                    break;
                default:
                    topic = "Server logging channel.";
            }

            newChannel.getManager().setTopic(topic).queue(success -> {
                logChannelCache.put(cacheKey, newChannel);
            });
        });

        return null;
    }

    public void logAction(Guild guild, String channelName, MessageEmbed embed) {
        TextChannel logChannel = getLogChannel(guild, channelName);
        if (logChannel != null) {
            logChannel.sendMessageEmbeds(embed).queue();
        }
    }

    public void logModAction(Guild guild, String actionTitle, User moderator, User target, String reason, String duration, Color color, String extraFieldName, String extraFieldValue) {
        String fieldName = (actionTitle.contains("Role")) ? "**Role**" : "Reason";
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(actionTitle)
                .setDescription(String.format("%s (%s) %s", target.getAsMention(), target.getId(), getActionDescription(actionTitle)))
                .addField("User ID", target.getId(), false)
                .addField(fieldName, reason, false);

        if (extraFieldName != null && extraFieldValue != null) {
            embed.addField(extraFieldName, extraFieldValue, false);
        }

        if (duration != null && !duration.isEmpty()) {
            embed.addField("Duration", duration, false);
        }

        embed.addField("Moderator", moderator.getName(), false)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setThumbnail(target.getEffectiveAvatarUrl());

        logAction(guild, "moderation-logs", embed.build());
    }

    public void logModAction(Guild guild, String actionTitle, User moderator, User target, String reason, String duration, Color color) {
        logModAction(guild, actionTitle, moderator, target, reason, duration, color, null, null);
    }

    private String getActionDescription(String title) {
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("muted")) return "has been muted";
        if (lowerTitle.contains("unmuted")) return "has been unmuted";
        if (lowerTitle.contains("warned")) return "has been warned";
        if (lowerTitle.contains("banned") && !lowerTitle.contains("unbanned")) return "has been banned";
        if (lowerTitle.contains("unbanned")) return "has been unbanned";
        if (lowerTitle.contains("kicked")) return "has been kicked";
        if (lowerTitle.contains("timed out")) return "has been timed out";
        if (lowerTitle.contains("timeout removed")) return "has had their timeout removed";
        if (lowerTitle.contains("role added")) return "has been given a role";
        if (lowerTitle.contains("role removed")) return "has had a role removed";
        return "action performed";
    }

    public void logActionWithFile(Guild guild, String channelName, MessageEmbed embed,
                                  InputStream fileData, String fileName) {
        TextChannel logChannel = getLogChannel(guild, channelName);
        if (logChannel != null) {
            FileUpload fileUpload = FileUpload.fromData(fileData, fileName);
            logChannel.sendMessageEmbeds(embed).queue(message -> {
                logChannel.sendFiles(fileUpload).queue();
            });
        }
    }
}
