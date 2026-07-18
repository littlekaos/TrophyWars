package TWBot.services;

import TWBot.config.BotConfig;
import TWBot.utils.OwnershipScheduleUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.Color;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OwnershipPingService {
    private final JDA jda;
    private final DataService dataService;
    private final ScheduledExecutorService scheduler;
    private static final String PING_KEY = "ownership_group_chat_check";
    private static final String ROLE_ID = BotConfig.SERVER_OWNERSHIP_ROLE_ID;
    private static final String CHANNEL_ID = BotConfig.ADMIN_CHAT_CHANNEL_ID;

    public OwnershipPingService(JDA jda, DataService dataService) {
        this.jda = jda;
        this.dataService = dataService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        // Initial check immediately on startup, then every hour
        scheduler.scheduleAtFixedRate(this::checkAndPing, 0, 1, TimeUnit.HOURS);
    }

    private void checkAndPing() {
        try {
            long lastPing = dataService.getLastPingTimestamp(PING_KEY);
            ZonedDateTime now = ZonedDateTime.now(OwnershipScheduleUtils.EST_ZONE);
            System.out.println("[OwnershipPingService] Checking ping status... Current time: " + now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));

            ZonedDateTime lastPingDate;
            if (lastPing == 0) {
                lastPingDate = OwnershipScheduleUtils.BASELINE_DATE;
                dataService.updateLastPingTimestamp(PING_KEY, lastPingDate.toInstant().toEpochMilli());
                System.out.println("[OwnershipPingService] No previous ping found. Set Ownership ping baseline to Feb 16, 2026.");
                return;
            }

            lastPingDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastPing), OwnershipScheduleUtils.EST_ZONE);
            lastPingDate = OwnershipScheduleUtils.normalizeBaseline(lastPingDate);
            if (lastPingDate.toInstant().toEpochMilli() != lastPing) {
                dataService.updateLastPingTimestamp(PING_KEY, lastPingDate.toInstant().toEpochMilli());
                System.out.println("[OwnershipPingService] Corrected Feb 25 timestamp to Feb 16 baseline.");
            }

            long monthsPassed = ChronoUnit.MONTHS.between(lastPingDate, now);
            System.out.println("[OwnershipPingService] Last ping baseline was on: " + lastPingDate.format(OwnershipScheduleUtils.DATE_FORMATTER) + " (" + monthsPassed + " months ago)");

            if (monthsPassed >= OwnershipScheduleUtils.PAYMENT_INTERVAL_MONTHS) {
                System.out.println("[OwnershipPingService] 3 months have passed since last ping baseline. Triggering reminder...");
                sendPing();
                dataService.updateLastPingTimestamp(PING_KEY, now.toInstant().toEpochMilli());
            } else {
                ZonedDateTime nextPingDate = lastPingDate.plusMonths(OwnershipScheduleUtils.PAYMENT_INTERVAL_MONTHS);
                System.out.println("[OwnershipPingService] Not yet time for reminder. Next ping expected around: " + nextPingDate.format(OwnershipScheduleUtils.DATE_FORMATTER));
            }
        } catch (Exception e) {
            System.err.println("Error in OwnershipPingService: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendPing() {
        try {
            TextChannel channel = jda.getTextChannelById(CHANNEL_ID);
            if (channel == null) {
                System.err.println("Admin chat channel not found for Ownership ping!");
                return;
            }

            Guild guild = channel.getGuild();
            Role ownershipRole = guild.getRoleById(ROLE_ID);

            Set<Member> membersToPing = new LinkedHashSet<>();

            if (ownershipRole != null) {
                try {
                    List<Member> owners = guild.findMembers(m -> m.getRoles().contains(ownershipRole)).get();
                    membersToPing.addAll(owners);
                } catch (Exception e) {
                    System.err.println("Error finding ownership members: " + e.getMessage());
                }
            }

            String finalMentions;
            if (membersToPing.isEmpty()) {
                finalMentions = ownershipRole != null ? ownershipRole.getAsMention() : "<@&" + ROLE_ID + ">";
            } else {
                StringBuilder sb = new StringBuilder();
                for (Member member : membersToPing) {
                    sb.append(member.getAsMention()).append(" ");
                }
                finalMentions = sb.toString().trim();
            }

            ZonedDateTime now = ZonedDateTime.now(OwnershipScheduleUtils.EST_ZONE);
            ZonedDateTime nextDueDate = OwnershipScheduleUtils.getNextDueDate(now);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(BotConfig.TW_EMOJI_MENTION + " Ownership Reminder")
                    .setColor(new Color(0x00E1FF))
                    .setDescription("This is your periodic **3-month reminder** to check the ownership group chat and submit payment.")
                    .addField("🕒 Current Date", now.format(OwnershipScheduleUtils.DATE_FORMATTER), true)
                    .addField("🗓️ Next Payment Due", nextDueDate.format(OwnershipScheduleUtils.DATE_FORMATTER), true)
                    .addField("📝 Note", "Use `/paymentdue` to view the full payment schedule. Please ensure you are active and checking for any important updates in the group chat.", false)
                    .setFooter("Ownership Reminder")
                    .setTimestamp(Instant.now());

            channel.sendMessage(finalMentions).setEmbeds(embed.build()).queue(
                success -> System.out.println("Ownership 3-month ping sent."),
                error -> System.err.println("Failed to send Ownership ping: " + error.getMessage())
            );
        } catch (Exception e) {
            System.err.println("Error sending Ownership ping: " + e.getMessage());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
