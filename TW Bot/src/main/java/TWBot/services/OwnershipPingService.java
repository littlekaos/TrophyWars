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
import java.time.LocalDate;
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
    public static final String PING_KEY = "ownership_group_chat_check";

    public static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
    /** Original 3-month baseline. */
    public static final ZonedDateTime LEGACY_BASELINE =
            ZonedDateTime.of(2026, 5, 16, 0, 0, 0, 0, EST_ZONE);
    /** One last quarterly ping, then switch to yearly. */
    public static final ZonedDateTime FINAL_QUARTERLY_DUE =
            ZonedDateTime.of(2026, 8, 16, 0, 0, 0, 0, EST_ZONE);
    /**
     * After the August 16, 2026 ping, store this so the next due is July 16, 2027,
     * then every July 16 thereafter.
     */
    public static final ZonedDateTime YEARLY_BASELINE =
            ZonedDateTime.of(2026, 7, 16, 0, 0, 0, 0, EST_ZONE);

    public static final int QUARTERLY_MONTHS = 3;
    public static final int YEARLY_MONTHS = 12;
    /** Kept for callers; yearly interval once transitioned. */
    public static final int INTERVAL_MONTHS = YEARLY_MONTHS;

    private static final String ROLE_ID = BotConfig.SERVER_OWNERSHIP_ROLE_ID;
    private static final String CHANNEL_ID = BotConfig.ADMIN_CHAT_CHANNEL_ID;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    public OwnershipPingService(JDA jda, DataService dataService) {
        this.jda = jda;
        this.dataService = dataService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAndPing, 0, 1, TimeUnit.HOURS);
    }

    /** True while still on the May 16 → August 16 quarterly leg. */
    public static boolean isQuarterlyPhase(ZonedDateTime lastPingDate) {
        LocalDate last = lastPingDate.toLocalDate();
        return !last.isAfter(LEGACY_BASELINE.toLocalDate());
    }

    public static ZonedDateTime nextDueFrom(ZonedDateTime lastPingDate) {
        if (isQuarterlyPhase(lastPingDate)) {
            return lastPingDate.plusMonths(QUARTERLY_MONTHS);
        }
        return lastPingDate.plusMonths(YEARLY_MONTHS);
    }

    public static int currentIntervalMonths(ZonedDateTime lastPingDate) {
        return isQuarterlyPhase(lastPingDate) ? QUARTERLY_MONTHS : YEARLY_MONTHS;
    }

    private void checkAndPing() {
        try {
            long lastPing = dataService.getLastPingTimestamp(PING_KEY);
            ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
            System.out.println("[OwnershipPingService] Checking ping status... Current time: " + now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));

            ZonedDateTime lastPingDate;
            if (lastPing == 0) {
                lastPingDate = LEGACY_BASELINE;
                dataService.updateLastPingTimestamp(PING_KEY, lastPingDate.toInstant().toEpochMilli());
                System.out.println("[OwnershipPingService] No previous ping found. Set baseline to May 16, 2026 (next due August 16, 2026).");
                return;
            }

            lastPingDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastPing), EST_ZONE);

            // Undo mistaken early jump to July 16, 2026 before the August quarterly ping
            if (now.isBefore(FINAL_QUARTERLY_DUE)
                    && lastPingDate.toLocalDate().equals(YEARLY_BASELINE.toLocalDate())) {
                lastPingDate = LEGACY_BASELINE;
                dataService.updateLastPingTimestamp(PING_KEY, lastPingDate.toInstant().toEpochMilli());
                System.out.println("[OwnershipPingService] Restored May 16, 2026 baseline (next due August 16, 2026; then yearly July 16).");
            } else if (lastPingDate.isBefore(LEGACY_BASELINE)) {
                lastPingDate = LEGACY_BASELINE;
                dataService.updateLastPingTimestamp(PING_KEY, lastPingDate.toInstant().toEpochMilli());
                System.out.println("[OwnershipPingService] Corrected early baseline to May 16, 2026 (next due August 16, 2026).");
            } else if (lastPingDate.toLocalDate().equals(FINAL_QUARTERLY_DUE.toLocalDate())) {
                // August ping already stored as last cycle — anchor yearly from July 16, 2026
                lastPingDate = YEARLY_BASELINE;
                dataService.updateLastPingTimestamp(PING_KEY, lastPingDate.toInstant().toEpochMilli());
                System.out.println("[OwnershipPingService] Converted August 16 cycle into yearly July 16 schedule (next due July 16, 2027).");
            }

            long monthsPassed = ChronoUnit.MONTHS.between(lastPingDate, now);
            int interval = currentIntervalMonths(lastPingDate);
            ZonedDateTime nextPingDate = nextDueFrom(lastPingDate);
            System.out.println("[OwnershipPingService] Last ping baseline was on: " + lastPingDate.format(DATE_FORMATTER)
                    + " (" + monthsPassed + " months ago, " + interval + "-month leg)");

            if (!now.isBefore(nextPingDate)) {
                System.out.println("[OwnershipPingService] Due date reached (" + nextPingDate.format(DATE_FORMATTER) + "). Triggering reminder...");
                boolean wasQuarterly = isQuarterlyPhase(lastPingDate);
                ZonedDateTime followingDue = wasQuarterly
                        ? YEARLY_BASELINE.plusMonths(YEARLY_MONTHS) // July 16, 2027
                        : nextPingDate.plusMonths(YEARLY_MONTHS);
                sendPing(nextPingDate, followingDue, wasQuarterly);

                if (wasQuarterly) {
                    // After Aug 16 ping, start yearly clock from July 16, 2026
                    dataService.updateLastPingTimestamp(PING_KEY, YEARLY_BASELINE.toInstant().toEpochMilli());
                    System.out.println("[OwnershipPingService] Quarterly complete. Yearly cycle starts (next due July 16, 2027).");
                } else {
                    dataService.updateLastPingTimestamp(PING_KEY, nextPingDate.toInstant().toEpochMilli());
                }
            } else {
                System.out.println("[OwnershipPingService] Not yet time for reminder. Next ping expected around: " + nextPingDate.format(DATE_FORMATTER));
            }
        } catch (Exception e) {
            System.err.println("Error in OwnershipPingService: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendPing(ZonedDateTime dueDate, ZonedDateTime nextPingDate, boolean wasQuarterly) {
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

            ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
            String cadence = wasQuarterly
                    ? "This is your **final quarterly reminder**. After this, reminders move to a **yearly** schedule."
                    : "This is your periodic **yearly reminder** to check the ownership group chat.";

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(BotConfig.TW_EMOJI_MENTION + " Ownership Reminder")
                    .setColor(new Color(0x00E1FF))
                    .setDescription(cadence)
                    .addField("🕒 Current Date", now.format(DATE_FORMATTER), true)
                    .addField("🗓️ Next Reminder", nextPingDate.format(DATE_FORMATTER), true)
                    .addField("📝 Note", "Please ensure you are active and checking for any important updates in the group chat.", false)
                    .setFooter("Ownership Reminder")
                    .setTimestamp(Instant.now());

            channel.sendMessage(finalMentions).setEmbeds(embed.build()).queue(
                success -> System.out.println("Ownership ping sent (next: " + nextPingDate.format(DATE_FORMATTER) + ")."),
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
