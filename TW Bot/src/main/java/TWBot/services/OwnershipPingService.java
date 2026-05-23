package TWBot.services;

import TWBot.config.BotConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.Color;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
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
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy");

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
            ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
            System.out.println("[OwnershipPingService] Checking ping status... Current time: " + now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));

            ZonedDateTime lastPingDate;
            if (lastPing == 0) {
                // Set baseline to February 16, 2026 as requested
                lastPingDate = ZonedDateTime.of(2026, 2, 16, 0, 0, 0, 0, EST_ZONE);
                dataService.updateLastPingTimestamp(PING_KEY, lastPingDate.toInstant().toEpochMilli());
                System.out.println("[OwnershipPingService] No previous ping found. Set Ownership ping baseline to Feb 16, 2026.");
                // We return here because we just set the baseline and 0 months have passed
                return;
            } else {
                lastPingDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastPing), EST_ZONE);
                
                // If the recorded ping is Feb 25, 2026, force it back to Feb 16, 2026 for correct 6-month calculation
                if (lastPingDate.getYear() == 2026 && lastPingDate.getMonthValue() == 2 && lastPingDate.getDayOfMonth() == 25) {
                    lastPingDate = ZonedDateTime.of(2026, 2, 16, 0, 0, 0, 0, EST_ZONE);
                    dataService.updateLastPingTimestamp(PING_KEY, lastPingDate.toInstant().toEpochMilli());
                    System.out.println("[OwnershipPingService] Corrected Feb 25 timestamp to Feb 16 baseline.");
                }
            }

            long monthsPassed = ChronoUnit.MONTHS.between(lastPingDate, now);
            System.out.println("[OwnershipPingService] Last ping baseline was on: " + lastPingDate.format(DATE_FORMATTER) + " (" + monthsPassed + " months ago)");
            
            // Check if 6 months have passed
            if (monthsPassed >= 6) {
                System.out.println("[OwnershipPingService] 6 months have passed since last ping baseline. Triggering reminder...");
                sendPing();
                dataService.updateLastPingTimestamp(PING_KEY, now.toInstant().toEpochMilli());
            } else {
                ZonedDateTime nextPingDate = lastPingDate.plusMonths(6);
                System.out.println("[OwnershipPingService] Not yet time for reminder. Next ping expected around: " + nextPingDate.format(DATE_FORMATTER));
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
                // Fallback to role mention if no members found
                finalMentions = ownershipRole != null ? ownershipRole.getAsMention() : "<@&" + ROLE_ID + ">";
            } else {
                StringBuilder sb = new StringBuilder();
                for (Member member : membersToPing) {
                    sb.append(member.getAsMention()).append(" ");
                }
                finalMentions = sb.toString().trim();
            }

            ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
            ZonedDateTime nextPingDate = now.plusMonths(6);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(BotConfig.TW_EMOJI_MENTION + " Ownership Reminder")
                    .setColor(new Color(0x00E1FF))
                    .setDescription("This is your periodic **6-month reminder** to check the ownership group chat.")
                    .addField("🕒 Current Date", now.format(DATE_FORMATTER), true)
                    .addField("🗓️ Next Reminder", nextPingDate.format(DATE_FORMATTER), true)
                    .addField("📝 Note", "Please ensure you are active and checking for any important updates in the group chat.", false)
                    .setFooter("Server Management System • 6-Month Reminder")
                    .setTimestamp(Instant.now());

            channel.sendMessage(finalMentions).setEmbeds(embed.build()).queue(
                success -> System.out.println("Ownership 6-month ping sent."),
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
