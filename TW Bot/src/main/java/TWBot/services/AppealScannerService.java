package TWBot.services;

import TWBot.config.BotConfig;
import TWBot.models.Appeal;
import TWBot.models.Strike;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppealScannerService {
    private static final Logger logger = LoggerFactory.getLogger(AppealScannerService.class);
    
    private final AppealService appealService;
    private final StrikeService strikeService;
    private final BotConfig config;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private static final long SCAN_INTERVAL_MINUTES = 5;
    
    private static final Pattern APPEAL_PATTERN = Pattern.compile(
        "(?i)appeal:?\\s*strike\\s*#?(\\d+)\\s*-?\\s*reason:?\\s*(.+)",
        Pattern.DOTALL
    );
    
    private JDA jda;
    private LocalDateTime lastScanTime;
    
    public AppealScannerService(AppealService appealService, StrikeService strikeService, BotConfig config) {
        this.appealService = appealService;
        this.strikeService = strikeService;
        this.config = config;
        this.lastScanTime = LocalDateTime.now().minusHours(1);
    }
    
    public void initialize(JDA jda) {
        this.jda = jda;
        startScanning();
        logger.info("Appeal Scanner Service initialized and started");
    }
    
    private void startScanning() {
        scheduler.scheduleAtFixedRate(this::scanForNewAppeals, 0, SCAN_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }
    
    private void scanForNewAppeals() {
        try {
            logger.debug("Starting appeal scan...");
            scanDiscordChannel();
            logger.debug("Appeal scan completed");
        } catch (Exception e) {
            logger.error("Error during appeal scan: " + e.getMessage(), e);
        }
    }
    
    private void scanDiscordChannel() {
        if (jda == null) return;
        
        TextChannel appealChannel = jda.getTextChannelById(BotConfig.STAFF_CHAT_CHANNEL_ID);
        if (appealChannel == null) {
            logger.warn("Appeal channel not found: " + BotConfig.STAFF_CHAT_CHANNEL_ID);
            return;
        }
        
        LocalDateTime scanStartTime = LocalDateTime.now();
        
        appealChannel.getHistory().retrievePast(50).queue(messages -> {
            for (Message message : messages) {
                if (message.getTimeCreated().toLocalDateTime().isBefore(lastScanTime)) continue;
                if (message.getAuthor().isBot()) continue;
                
                processDiscordMessage(message);
            }
            lastScanTime = scanStartTime;
        }, error -> {
            logger.error("Failed to retrieve messages from appeal channel: " + error.getMessage());
        });
    }
    
    private void processDiscordMessage(Message message) {
        String content = message.getContentRaw();
        Matcher matcher = APPEAL_PATTERN.matcher(content);
        
        if (!matcher.find()) return;
        
        String userId = message.getAuthor().getId();
        String strikeNumberStr = matcher.group(1);
        String reason = matcher.group(2).trim();
        
        try {
            int strikeNumber = Integer.parseInt(strikeNumberStr);
            boolean success = appealService.createAppeal(userId, strikeNumber, reason);
            
            if (success) {
                message.addReaction(Emoji.fromUnicode("✅")).queue();
                
                List<Strike> strikes = strikeService.getStrikes(userId);
                Strike strike = (strikeNumber >= 1 && strikeNumber <= strikes.size()) ? strikes.get(strikeNumber - 1) : null;
                
                List<Appeal> userAppeals = appealService.getUserAppeals(userId);
                Appeal appeal = (strike != null) ? userAppeals.stream()
                        .filter(a -> a.getStrikeId() == strike.getId() && a.getStatus().equals("PENDING"))
                        .findFirst()
                        .orElse(null) : null;
                
                sendDiscordAppealConfirmation(message, userId, strikeNumber, strike, reason);
                logAppealSubmission(message.getAuthor(), strike, reason, appeal != null ? appeal.getId() : -1);
            } else {
                message.addReaction(Emoji.fromUnicode("❌")).queue();
                sendAppealFailureExplanation(message, userId);
            }
            
        } catch (NumberFormatException e) {
            message.addReaction(Emoji.fromUnicode("❓")).queue();
        }
    }
    
    private void sendAppealFailureExplanation(Message originalMessage, String userId) {
        String explanation;
        if (appealService.getActiveAppealCount(userId) >= 2) {
            explanation = "❌ You already have 2 pending appeals. You can appeal up to 2 strikes at once.";
        } else {
            List<Strike> strikes = strikeService.getStrikes(userId);
            if (strikes.isEmpty()) {
                explanation = "❌ You don't have any strikes to appeal.";
            } else {
                explanation = "❌ Invalid appeal format, strike number, or you've already appealed this strike.";
            }
        }
        originalMessage.reply(explanation).queue();
    }
    
    private void sendDiscordAppealConfirmation(Message originalMessage, String userId, int strikeNumber, Strike strike, String reason) {
        int newActiveCount = appealService.getActiveAppealCount(userId);
        String description = "Your appeal has been submitted and is pending review.";
        
        if (newActiveCount == 1) {
            description += "\n⚠️ **You can appeal 1 more strike, but this is your only appeal chance!**";
        } else {
            description += "\n⚠️ **This was your final appeal - you've used your one appeal chance!**";
        }
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📩 Appeal Submitted (Discord)")
                .setColor(Color.BLUE)
                .setDescription(description + "\n")
                .addField("Active Appeals", newActiveCount + "/2", false)
                .addField("Strike #" + strikeNumber, strike != null ? strike.getReason() : "Unknown Strike", false)
                .addField("Appeal Reason", reason, false)
                .setFooter("Strike Appeal System - Auto-processed from Discord", null)
                .setTimestamp(java.time.Instant.now());
        
        originalMessage.replyEmbeds(embed.build()).queue();
    }
    
    private void logAppealSubmission(User user, Strike strike, String reason, int appealId) {
        TextChannel logChannel = jda.getTextChannelById(BotConfig.STAFF_CHAT_CHANNEL_ID);
        if (logChannel == null) return;
        
        EmbedBuilder logEmbed = new EmbedBuilder()
                .setTitle("📩 APPEAL SUBMITTED")
                .setColor(new Color(0x4169E1))
                .setDescription("A user has submitted a strike appeal (Auto-Scanner)\n")
                .addField("📋 User",
                        String.format("**%s**\n`%s`\n<@%s>",
                                user.getName(),
                                user.getId(),
                                user.getId()), false)
                .addField("🆔 Appeal ID",
                        String.format("`%d`", appealId), false)
                .addField("📝 Original Strike",
                        String.format("```%s```", strike != null ? strike.getReason() : "Unknown"), false)
                .addField("💬 Appeal Reason",
                        String.format("```%s```", reason), false)
                .addField("🕐 Submitted",
                        String.format("<t:%d:F>", System.currentTimeMillis() / 1000), false)
                .addField("⚡ Action ID", String.format("`%s`", generateActionId()), false)
                .addField("🛠️ Action Required",
                        String.format("Use `/reviewappeal %d approve/deny reason`", appealId), false)
                .setThumbnail(user.getEffectiveAvatarUrl())
                .setFooter("Strike Appeal System - Discord Auto-Scanner",
                        jda.getSelfUser().getEffectiveAvatarUrl())
                .setTimestamp(java.time.Instant.now());

        String rolePing = String.format("<@&%s> <@&%s>", BotConfig.OVERSEER_ROLE_ID, BotConfig.MANAGER_ROLE_ID);
        logChannel.sendMessage(rolePing).setEmbeds(logEmbed.build()).queue();
    }

    private String generateActionId() {
        return "MOD-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Appeal Scanner Service shut down");
    }

    public void triggerManualScan() {
        logger.info("Manual appeal scan triggered");
        scanForNewAppeals();
    }

    public boolean isScannerRunning() {
        return !scheduler.isShutdown() && !scheduler.isTerminated();
    }

    public String getScanStats() {
        return String.format("Last scan: %s\nScan interval: %d minutes\nScanner status: %s",
                lastScanTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                SCAN_INTERVAL_MINUTES,
                scheduler.isShutdown() ? "Stopped" : "Running");
    }
}
