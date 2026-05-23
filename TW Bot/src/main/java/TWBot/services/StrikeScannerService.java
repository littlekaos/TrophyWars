package TWBot.services;

import TWBot.config.BotConfig;
import TWBot.models.Strike;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrikeScannerService {
    private static final Logger logger = LoggerFactory.getLogger(StrikeScannerService.class);
    
    private final StrikeService strikeService;
    private final BotConfig config;
    private static final Pattern USER_ID_PATTERN = Pattern.compile("\\((\\d{17,20})\\)");
    
    public StrikeScannerService(StrikeService strikeService, BotConfig config) {
        this.strikeService = strikeService;
        this.config = config;
    }

    public void initialize(JDA jda) {
        String channelId = "1472814682245955627";
        scanChannelForStrikes(jda, channelId, count -> {
            logger.info("Auto-import completed on startup. Found {} strikes.", count);
        });
    }

    public void scanChannelForStrikes(JDA jda, String channelId, java.util.function.Consumer<Integer> callback) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            logger.warn("Strike log channel not found: {}", channelId);
            callback.accept(0);
            return;
        }

        AtomicInteger strikesFound = new AtomicInteger(0);
        logger.info("Starting historical strike scan in channel: {}", channel.getName());

        channel.getIterableHistory().forEachAsync(message -> {
            boolean processed = processMessageForStrikes(message);
            if (processed) {
                strikesFound.incrementAndGet();
            }
            return true; // continue iterating
        }).thenRun(() -> {
            logger.info("Historical strike scan completed. Found {} strikes.", strikesFound.get());
            callback.accept(strikesFound.get());
        }).exceptionally(e -> {
            logger.error("Error during historical strike scan: {}", e.getMessage());
            callback.accept(strikesFound.get());
            return null;
        });
    }

    private boolean processMessageForStrikes(Message message) {
        // Skip non-bot messages if we expect bot embeds
        if (!message.getAuthor().isBot()) return false;

        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.isEmpty()) return false;

        for (MessageEmbed embed : embeds) {
            String title = embed.getTitle();
            if (title == null) continue;

            // Handle "⛔ Strike Issued" or "⚠️ STRIKE ISSUED"
            if (title.toUpperCase().contains("STRIKE ISSUED")) {
                return parseStrikeEmbed(embed, message.getTimeCreated().toInstant().toEpochMilli());
            }
        }
        return false;
    }

    private boolean parseStrikeEmbed(MessageEmbed embed, long timestamp) {
        String userId = null;
        String reason = "Imported from historical logs";
        String moderatorId = "SYSTEM_IMPORT";

        // Try parsing from fields or description
        for (MessageEmbed.Field field : embed.getFields()) {
            String name = field.getName() != null ? field.getName().toLowerCase() : "";
            String value = field.getValue();

            if (name.contains("user") || name.contains("target")) {
                userId = extractUserId(value);
            } else if (name.contains("reason")) {
                reason = value;
            } else if (name.contains("moderator")) {
                moderatorId = extractUserId(value);
            }
        }

        if (userId == null && embed.getDescription() != null) {
            userId = extractUserId(embed.getDescription());
            if (reason.equals("Imported from historical logs") && embed.getDescription().contains("Reason:")) {
                // simple parsing for reason if in description
            }
        }

        if (userId != null) {
            // Check if this specific strike might already exist to avoid duplicates
            // Since we don't have a unique ID for historical strikes, we can check if 
            // the user already has a strike with the same reason and roughly same time
            // For now, let's just check if we've already imported this user/reason combo recently
            
            List<Strike> existing = strikeService.getStrikes(userId);
            String finalReason = reason;
            boolean duplicate = existing.stream().anyMatch(s -> s.getReason().equals(finalReason));
            
            if (!duplicate) {
                strikeService.getDatabase().addStrike(userId, reason, moderatorId, new Timestamp(timestamp));
                return true;
            }
        }

        return false;
    }

    private String extractUserId(String text) {
        if (text == null) return null;
        
        // Match raw ID
        Matcher matcher = Pattern.compile("(\\d{17,20})").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Match mention <@ID> or <@!ID>
        matcher = Pattern.compile("<@!?(\\d{17,20})>").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
}
