package TWBot.services;

import TWBot.config.BotConfig;
import TWBot.database.StrikeDatabase;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DemotionService {
    private static final Logger logger = LoggerFactory.getLogger(DemotionService.class);
    private static final int ENTRIES_PER_PAGE = 20;

    private final StrikeDatabase database;
    private final BotConfig config;
    private RoleRestorationService roleRestorationService;

    public DemotionService(StrikeService strikeService, BotConfig config) {
        this.database = strikeService.getDatabase();
        this.config = config;
    }

    public void setRoleRestorationService(RoleRestorationService roleRestorationService) {
        this.roleRestorationService = roleRestorationService;
    }

    public StrikeDatabase getDatabase() {
        return database;
    }

    public void addPermanentDemotion(String userId) {
        database.addPermanentDemotion(userId);
    }

    public void addTemporaryDemotion(String userId, List<String> roleIds, LocalDateTime restorationDate) {
        database.saveTemporaryDemotion(userId, roleIds, restorationDate);
    }

    public void markDemotionAsServed(String userId, int strikeCount) {
        database.markDemotionAsServed(userId, strikeCount);
    }

    public int getServedDemotionStrikeCount(String userId) {
        return database.getServedDemotionStrikeCount(userId);
    }

    public void clearServedDemotion(String userId) {
        database.clearServedDemotion(userId);
    }

    public void removeDemotion(String userId, JDA jda) {
        if (roleRestorationService != null) {
            List<String> roleIds = database.getTemporaryDemotionRoles(userId);
            // This will trigger the updated restoration logic (mutually exclusive)
            roleRestorationService.restoreRolesForUser(userId, roleIds, false);
        }
        
        // Use consolidated transaction for better performance and to prevent locks
        int strikeCount = database.getStrikes(userId).size();
        database.removeFromDemotions(userId, strikeCount);
        
        if (jda != null) {
            updateDemotionListMessage(jda);
        }
    }

    public Map<String, Map<String, Object>> getTemporaryDemotions() {
        return database.loadTemporaryDemotionsWithRoles();
    }

    public Set<String> getPermanentDemotions() {
        return database.loadPermanentDemotions();
    }

    public boolean isPermanentlyDemoted(String userId) {
        return database.loadPermanentDemotions().contains(userId);
    }

    public boolean isTemporarilyDemoted(String userId) {
        Map<String, Map<String, Object>> demotions = database.loadTemporaryDemotionsWithRoles();
        if (!demotions.containsKey(userId)) {
            return false;
        }
        
        LocalDateTime restorationDate = (LocalDateTime) demotions.get(userId).get("restorationDate");
        return restorationDate != null && LocalDateTime.now(ZoneId.of("America/New_York")).isBefore(restorationDate);
    }

    public boolean isDemoted(String userId) {
        return isPermanentlyDemoted(userId) || isTemporarilyDemoted(userId);
    }

    public void updateDemotionListMessage(JDA jda) {
        String dbMessageId = database.getDemotionListMessageId();
        final String finalMessageId = (dbMessageId != null) ? dbMessageId : BotConfig.DEMOTION_LIST_MESSAGE_ID;

        if (finalMessageId == null || finalMessageId.isEmpty() || finalMessageId.equals("null")) {
            logger.warn("No demotion list message ID found. Use /initdemotionlist to create one.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
        if (channel == null) {
            logger.warn("Staff notification channel not found: {}", BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
            return;
        }

        List<MessageEmbed> pages = buildPages();
        if (pages.isEmpty()) {
            logger.info("No demotion pages to display.");
            return;
        }

        logger.info("Updating demotion list message {} with {} pages.", finalMessageId, pages.size());
        channel.editMessageEmbedsById(finalMessageId, pages.get(0))
                .setComponents(buildActionRow(1, pages.size()))
                .queue(
                    success -> logger.info("Successfully updated demotion list message: {}", finalMessageId),
                    error -> {
                        logger.error("Failed to update demotion list message {}: {}", finalMessageId, error.getMessage());
                        if (error.getMessage().contains("10008") || error.getMessage().contains("Unknown Message")) {
                            logger.warn("Demotion list message {} was deleted or is invalid. Clearing from database.", finalMessageId);
                            database.setDemotionListMessageId(null);
                        }
                    }
                );
    }

    public List<MessageEmbed> buildPages() {
        Map<String, Map<String, Object>> tempDemotions = database.loadTemporaryDemotionsWithRoles();
        Set<String> permDemotions = database.loadPermanentDemotions();
        
        List<String> tempLines = new ArrayList<>();
        List<String> permLines = new ArrayList<>();

        // Add explicit permanent demotions
        for (String userId : permDemotions) {
            permLines.add(String.format("<@%s> (%s) 3 strikes, permanent", userId, userId));
        }

        // Add explicit temporary demotions
        for (Map.Entry<String, Map<String, Object>> entry : tempDemotions.entrySet()) {
            String userId = entry.getKey();
            if (permDemotions.contains(userId)) {
                continue; // Already in permanent list
            }
            LocalDateTime date = (LocalDateTime) entry.getValue().get("restorationDate");
            long unix = date.atZone(ZoneId.of("America/New_York")).toEpochSecond();
            tempLines.add(String.format("<@%s> (%s) 2 strikes, back <t:%d:F>", userId, userId, unix));
        }

        List<MessageEmbed> pages = new ArrayList<>();
        
        // Only add categories if they have content, OR if both are empty show one empty page
        if (permLines.isEmpty() && tempLines.isEmpty()) {
            addPages(pages, new ArrayList<>(), "🔴 PERMANENT DEMOTIONS", Color.RED, true);
        } else {
            if (!permLines.isEmpty()) {
                addPages(pages, permLines, "🔴 PERMANENT DEMOTIONS", Color.RED, false);
            }
            if (!tempLines.isEmpty()) {
                addPages(pages, tempLines, "🟡 TEMPORARY DEMOTIONS", Color.YELLOW, false);
            }
        }

        return pages;
    }

    private void addPages(List<MessageEmbed> pages, List<String> lines, String title, Color color, boolean showEmpty) {
        if (lines.isEmpty()) {
            if (showEmpty) {
                pages.add(new EmbedBuilder()
                        .setTitle(title)
                        .setDescription("None\n")
                        .setColor(color)
                        .setFooter("Page " + (pages.size() + 1))
                        .build());
            }
            return;
        }

        for (int i = 0; i < lines.size(); i += ENTRIES_PER_PAGE) {
            int end = Math.min(i + ENTRIES_PER_PAGE, lines.size());
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                sb.append(lines.get(j)).append("\n");
            }
            pages.add(new EmbedBuilder()
                    .setTitle(title)
                    .setDescription((sb.toString().isEmpty() ? "None" : sb.toString()) + "\n")
                    .setColor(color)
                    .setFooter("Page " + (pages.size() + 1))
                    .build());
        }
    }

    public ActionRow buildActionRow(int currentPage, int totalPages) {
        return ActionRow.of(
                Button.primary("demotion_first", "⏮️ First").withDisabled(currentPage <= 1),
                Button.primary("demotion_prev", "◀ Prev").withDisabled(currentPage <= 1),
                Button.secondary("demotion_page", String.format("Page %d/%d", currentPage, totalPages)).asDisabled(),
                Button.primary("demotion_next", "Next ▶").withDisabled(currentPage >= totalPages),
                Button.primary("demotion_last", "Last ⏭️").withDisabled(currentPage >= totalPages)
        );
    }
}
