package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.models.ModAction;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReasonCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(ReasonCommand.class);
    private final TWBot bot;
    private final DataService dataService;
    private static final String COURT_GUILD_ID = BotConfig.GUILD_ID;
    private static final String MAIN_GUILD_ID = BotConfig.GUILD_ID;

    public ReasonCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("reason", "Check the ban/unban history for a user")
                .addOption(OptionType.STRING, "user", "The user ID to check history for", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getOption("user").getAsString();
        logger.info("[ReasonScan] /reason command called for user: {}", userId);

        if (!PermissionUtils.isModerator(event.getMember(), bot.getConfig())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        startScan(event, userId);
    }

    private void startScan(SlashCommandInteractionEvent event, String userId) {
        Guild mainGuild = event.getJDA().getGuildById(MAIN_GUILD_ID);

        if (mainGuild == null) {
            logger.error("[ReasonScan] Guild not found!");
            event.getHook().sendMessage("Error: Guild not found. Please contact an administrator.").queue();
            return;
        }

        List<ModAction> scanResults = new ArrayList<>();
        // 1 category scan + 2 audit logs (ban, unban) + 1 ban list retrieval
        AtomicInteger pendingScans = new AtomicInteger(4);

        // Scan [🗂️] Content Moderation Category
        scanCategoryLogs(mainGuild, "[🗂️] Content Moderation", userId, scanResults, () -> {
            if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
        });

        // Scan Audit Logs (Ban Tab - Built-in)
        scanAuditLogs(mainGuild, userId, ActionType.BAN, scanResults, () -> {
            if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
        });

        // Scan Audit Logs (Unban Tab - Built-in)
        scanAuditLogs(mainGuild, userId, ActionType.UNBAN, scanResults, () -> {
            if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
        });

        // Scan Bans List (Bans Tab - Built-in)
        mainGuild.retrieveBan(UserSnowflake.fromId(userId)).queue(ban -> {
            String reason = ban.getReason() != null ? ban.getReason() : "Breaking server rules";
            long timestamp = Instant.now().toEpochMilli();

            synchronized (scanResults) {
                boolean exists = scanResults.stream().anyMatch(a -> Math.abs(a.getTimestamp() - timestamp) < 5000);
                if (!exists) {
                    scanResults.add(new ModAction(ModAction.ActionType.BAN, "BAN_TAB_FALLBACK", "Trophy Wars", userId, "User",
                            reason, timestamp, 0, 0));
                }
            }
            if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
        }, error -> {
            if (pendingScans.decrementAndGet() == 0) finishScan(event, userId, scanResults);
        });
    }

    private void scanCategoryLogs(Guild guild, String categoryKeyword, String userId, List<ModAction> results, Runnable onComplete) {
        List<Category> categories = guild.getCategories().stream()
                .filter(c -> c.getName().toLowerCase().contains(categoryKeyword.toLowerCase()))
                .toList();

        if (categories.isEmpty()) {
            onComplete.run();
            return;
        }

        List<TextChannel> allChannels = new ArrayList<>();
        for (Category category : categories) {
            allChannels.addAll(category.getTextChannels());
        }

        if (allChannels.isEmpty()) {
            onComplete.run();
            return;
        }

        AtomicInteger pendingChannels = new AtomicInteger(allChannels.size());
        for (TextChannel channel : allChannels) {
            scanChannelRecursive(channel, null, userId, ModAction.ActionType.BAN, results, 500, () -> {
                if (pendingChannels.decrementAndGet() == 0) onComplete.run();
            });
        }
    }

    private void scanAuditLogs(Guild guild, String userId, ActionType type, List<ModAction> results, Runnable onComplete) {
        scanAuditLogsRecursive(guild, null, userId, type, results, 500, onComplete);
    }

    private void scanAuditLogsRecursive(Guild guild, AuditLogEntry lastEntry, String userId, ActionType type, List<ModAction> results, int remaining, Runnable onComplete) {
        if (remaining <= 0) {
            onComplete.run();
            return;
        }

        int limit = Math.min(remaining, 100);
        var action = guild.retrieveAuditLogs().type(type).limit(limit);

        if (lastEntry != null) {
            action.skipTo(lastEntry.getIdLong());
        }

        action.queue(logs -> {
            if (logs.isEmpty()) {
                onComplete.run();
                return;
            }

            for (AuditLogEntry entry : logs) {
                if (userId.equals(entry.getTargetId())) {
                    String reason = entry.getReason() != null ? entry.getReason() : "Breaking server rules";
                    String modName = entry.getUser() != null ? entry.getUser().getName() : "Trophy Wars";
                    String modId = entry.getUser() != null ? entry.getUser().getId() : "0";
                    long timestamp = entry.getTimeCreated().toInstant().toEpochMilli();

                    synchronized (results) {
                        boolean exists = results.stream().anyMatch(a -> Math.abs(a.getTimestamp() - timestamp) < 5000);
                        if (!exists) {
                            ModAction.ActionType modActionType = (type == ActionType.UNBAN) ? ModAction.ActionType.UNBAN : ModAction.ActionType.BAN;
                            results.add(new ModAction(modActionType, modId, modName, userId, "User",
                                    reason, timestamp, 0, 0));
                        }
                    }
                }
            }

            if (logs.size() < limit) {
                onComplete.run();
            } else {
                scanAuditLogsRecursive(guild, logs.get(logs.size() - 1), userId, type, results, remaining - logs.size(), onComplete);
            }
        }, error -> onComplete.run());
    }

    private void scanChannel(TextChannel channel, String userId, ModAction.ActionType type, List<ModAction> results, Runnable onComplete) {
        channel.getHistory().retrievePast(100).queue(messages -> {
            processMessages(messages, userId, type, channel, results);
            onComplete.run();
        }, error -> onComplete.run());
    }

    private void scanChannelRecursive(TextChannel channel, Message before, String userId, ModAction.ActionType type, List<ModAction> results, int remaining, Runnable onComplete) {
        if (remaining <= 0) {
            onComplete.run();
            return;
        }

        int limit = Math.min(remaining, 100);
        if (before == null) {
            channel.getHistory().retrievePast(limit).queue(messages -> {
                processMessages(messages, userId, type, channel, results);
                if (messages.size() < limit) onComplete.run();
                else scanChannelRecursive(channel, messages.get(messages.size() - 1), userId, type, results, remaining - messages.size(), onComplete);
            }, e -> onComplete.run());
        } else {
            channel.getHistoryBefore(before, limit).queue(history -> {
                List<Message> messages = history.getRetrievedHistory();
                processMessages(messages, userId, type, channel, results);
                if (messages.size() < limit) onComplete.run();
                else scanChannelRecursive(channel, messages.get(messages.size() - 1), userId, type, results, remaining - messages.size(), onComplete);
            }, e -> onComplete.run());
        }
    }

    private void processMessages(List<Message> messages, String userId, ModAction.ActionType type, TextChannel channel, List<ModAction> results) {
        String mentionFormat = "<@" + userId + ">";
        String nicknameMentionFormat = "<@!" + userId + ">";

        for (Message message : messages) {
            if (messageMatches(message, userId, mentionFormat, nicknameMentionFormat)) {
                ModAction action = parseMessage(message, userId, type, channel);
                synchronized (results) {
                    boolean exists = results.stream().anyMatch(a -> Math.abs(a.getTimestamp() - action.getTimestamp()) < 5000);
                    if (!exists) results.add(action);
                }
            }
        }
    }

    private boolean messageMatches(Message message, String userId, String mentionFormat, String nicknameMentionFormat) {
        String content = message.getContentRaw();
        if (content.contains(userId) || content.contains(mentionFormat) || content.contains(nicknameMentionFormat)) return true;
        for (MessageEmbed embed : message.getEmbeds()) {
            if (embedContains(embed, userId) || embedContains(embed, mentionFormat) || embedContains(embed, nicknameMentionFormat)) return true;
        }
        return false;
    }

    private boolean embedContains(MessageEmbed embed, String text) {
        if (embed == null) return false;
        if (embed.getDescription() != null && embed.getDescription().contains(text)) return true;
        if (embed.getTitle() != null && embed.getTitle().contains(text)) return true;
        if (embed.getFooter() != null && embed.getFooter().getText() != null && embed.getFooter().getText().contains(text)) return true;
        if (embed.getAuthor() != null && embed.getAuthor().getName() != null && embed.getAuthor().getName().contains(text)) return true;
        for (MessageEmbed.Field field : embed.getFields()) {
            if (field.getName() != null && field.getName().contains(text)) return true;
            if (field.getValue() != null && field.getValue().contains(text)) return true;
        }
        return false;
    }

    private ModAction parseMessage(Message message, String userId, ModAction.ActionType type, TextChannel channel) {
        String content = message.getContentRaw();
        boolean isBanList = channel.getName().toLowerCase().contains("ban");

        String foundReason = type == ModAction.ActionType.UNBAN ? "appealed" : "Breaking server rules";
        String foundMod = message.getAuthor().getName();
        String foundModId = message.getAuthor().getId();
        long timestamp = message.getTimeCreated().toInstant().toEpochMilli();

        if (!isBanList && foundModId.equals(message.getJDA().getSelfUser().getId())) {
             foundMod = "Trophy Wars";
             foundModId = "0";
        }

        for (MessageEmbed embed : message.getEmbeds()) {
            if (embedContains(embed, userId)) {
                if (embed.getDescription() != null && embed.getDescription().toLowerCase().contains("reason:")) {
                    String desc = embed.getDescription();
                    int reasonIndex = desc.toLowerCase().indexOf("reason:");
                    foundReason = desc.substring(reasonIndex + 7).split("\n")[0].trim();
                }
                for (MessageEmbed.Field field : embed.getFields()) {
                    if (field.getName() != null && (field.getName().toLowerCase().contains("moderator") || field.getName().toLowerCase().contains("staff"))) {
                        String modVal = field.getValue();
                        if (modVal != null) {
                            if (modVal.contains("(") && modVal.contains(")")) {
                                int openParen = modVal.lastIndexOf("(");
                                foundModId = modVal.substring(openParen + 1, modVal.lastIndexOf(")")).trim();
                                foundMod = modVal.substring(0, openParen).trim();
                            } else {
                                foundMod = modVal;
                                foundModId = modVal;
                            }
                        }
                        break;
                    }
                }
            }
        }

        if (type == ModAction.ActionType.BAN && !content.isEmpty()) {
            String[] lines = content.split("\n");
            boolean foundIdLine = false;
            for (String line : lines) {
                if (line.contains("Discord ID:") && line.contains(userId)) {
                    foundIdLine = true;
                    continue;
                }
                if (foundIdLine && !line.trim().isEmpty()) {
                    foundReason = line.trim();
                    break;
                }
            }
            if (foundReason.equals("Breaking server rules") && content.contains(" - ")) {
                String[] parts = content.split(" - ");
                if (parts.length >= 2) {
                    foundReason = parts[0].trim();
                    String modPart = parts[1].trim();
                    if (modPart.contains(" (")) {
                        foundMod = modPart.substring(0, modPart.indexOf(" (")).trim();
                        foundModId = modPart.substring(modPart.indexOf(" (") + 2, modPart.indexOf(")")).trim();
                    } else foundMod = modPart;
                }
            }
        }

        return new ModAction(type, foundModId, foundMod, userId, "User", foundReason,
                timestamp, 0, 0);
    }

    private void finishScan(SlashCommandInteractionEvent event, String userId, List<ModAction> results) {
        // Deduplicate: if we have any BAN action from a real source, remove the "BAN_TAB_FALLBACK"
        boolean hasRealBan = results.stream().anyMatch(a -> a.getActionType() == ModAction.ActionType.BAN && !a.getModeratorId().equals("BAN_TAB_FALLBACK"));
        if (hasRealBan) {
            results.removeIf(a -> a.getModeratorId().equals("BAN_TAB_FALLBACK"));
        }

        results.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        if (results.isEmpty()) {
            EmbedBuilder historyEmbed = new EmbedBuilder()
                    .setTitle("📋 Ban/Unban History")
                    .setDescription("**User:** <@" + userId + "> (`" + userId + "`)\n\n" +
                            "🔨 **BAN**\n" +
                            "└─ **Moderator:** Trophy Wars (ID: `0`)\n" +
                            "└─ **Reason:** Breaking server rules\n" +
                            "└─ **Source:** Trophy Wars Community / Audit Log\n" +
                            "└─ **Date:** <t:" + (Instant.now().getEpochSecond()) + ":F>\n")
                    .setColor(new Color(100, 150, 255))
                    .setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(historyEmbed.build()).queue();
            return;
        }

        StringBuilder historyText = new StringBuilder();
        String botId = event.getJDA().getSelfUser().getId();
        for (ModAction action : results) {
            String actionEmoji = action.getActionType() == ModAction.ActionType.BAN ? "🔨" : "🔓";
            String modId = action.getModeratorId().equals("BAN_TAB_FALLBACK") ? botId : action.getModeratorId();
            
            historyText.append(actionEmoji).append(" **").append(action.getActionType().name()).append("**\n");
            historyText.append("└─ **Moderator:** ").append(action.getModeratorName().replace("_", "\\_"))
                    .append(" (ID: `").append(modId).append("`)\n");
            historyText.append("└─ **Reason:** ").append(action.getReason().replace("_", "\\_")).append("\n");
            historyText.append("└─ **Date:** ").append(action.getFormattedDate()).append("\n\n");
        }

        EmbedBuilder historyEmbed = new EmbedBuilder()
                .setTitle("📋 Ban/Unban History")
                .setDescription("**User:** <@" + userId + "> (`" + userId + "`)\n\n" + historyText)
                .setColor(new Color(100, 150, 255))
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(historyEmbed.build()).queue();
    }
}
