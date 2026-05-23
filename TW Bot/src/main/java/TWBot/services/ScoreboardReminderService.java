package TWBot.services;

import TWBot.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScoreboardReminderService {
    private final JDA jda;
    private final ScheduledExecutorService scheduler;
    private static final String OVERSEER_ROLE_ID = BotConfig.OVERSEER_ROLE_ID;
    private static final String MANAGER_ROLE_ID = BotConfig.MANAGER_ROLE_ID;
    private static final String STAFF_CHAT_CHANNEL_ID = BotConfig.STAFF_CHAT_CHANNEL_ID;
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");

    public ScoreboardReminderService(JDA jda) {
        this.jda = jda;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    private ZonedDateTime lastPingTime = null;

    public void start() {
        // Send initial ping on startup
        sendPing();
        lastPingTime = ZonedDateTime.now(EST_ZONE);

        // Run every minute to check if it's time for the next scheduled ping
        scheduler.scheduleAtFixedRate(this::checkAndPing, 1, 1, TimeUnit.MINUTES);
    }

    private void checkAndPing() {
        ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
        int hour = now.getHour();

        // Only ping at 11 PM (23:00)
        if (hour == 23) {
            // If we haven't pinged today yet
            if (lastPingTime == null || lastPingTime.toLocalDate().isBefore(now.toLocalDate())) {
                sendPing();
                lastPingTime = now;
            }
        }
    }

    private void sendPing() {
        try {
            TextChannel channel = jda.getTextChannelById(STAFF_CHAT_CHANNEL_ID);
            if (channel == null) {
                System.err.println("Staff chat channel not found for scoreboard reminder!");
                return;
            }

            Guild guild = channel.getGuild();
            Role overseerRole = guild.getRoleById(OVERSEER_ROLE_ID);
            Role managementRole = guild.getRoleById(MANAGER_ROLE_ID);

            Set<Member> membersToPing = new LinkedHashSet<>();

            if (overseerRole != null) {
                try {
                    List<Member> overseers = guild.findMembers(m -> m.getRoles().contains(overseerRole)).get();
                    membersToPing.addAll(overseers);
                } catch (Exception e) {
                    System.err.println("Error finding overseer members: " + e.getMessage());
                }
            }
            if (managementRole != null) {
                try {
                    List<Member> managers = guild.findMembers(m -> m.getRoles().contains(managementRole)).get();
                    membersToPing.addAll(managers);
                } catch (Exception e) {
                    System.err.println("Error finding management members: " + e.getMessage());
                }
            }

            String finalMentions;
            if (membersToPing.isEmpty()) {
                // Fallback to role mentions if no members found (e.g. not cached or empty)
                String overseerMention = overseerRole != null ? overseerRole.getAsMention() : "<@&" + OVERSEER_ROLE_ID + ">";
                String managementMention = managementRole != null ? managementRole.getAsMention() : "<@&" + MANAGER_ROLE_ID + ">";
                finalMentions = overseerMention + " " + managementMention;
            } else {
                StringBuilder sb = new StringBuilder();
                for (Member member : membersToPing) {
                    sb.append(member.getAsMention()).append(" ");
                }
                finalMentions = sb.toString().trim();
            }

            channel.sendMessage(finalMentions + " This is your reminder to do the SB's please and thank you!").queue(
                success -> System.out.println("Scoreboard reminder sent at " + ZonedDateTime.now(EST_ZONE)),
                error -> System.err.println("Failed to send scoreboard reminder: " + error.getMessage())
            );
        } catch (Exception e) {
            System.err.println("Error in ScoreboardReminderService: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}
