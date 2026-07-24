package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.services.DataService;
import TWBot.services.OwnershipPingService;
import TWBot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class OwnershipCycleCommand implements Command {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final DataService dataService;

    public OwnershipCycleCommand(TWBot bot) {
        this.dataService = bot.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("qcheck", "View ownership schedule status"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean isOwner = event.getUser().getId().equals(BotConfig.OWNER_USER_ID);
        boolean hasOwnershipRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(BotConfig.SERVER_OWNERSHIP_ROLE_ID));

        if (!isOwner && !hasOwnershipRole) {
            event.reply("❌ Only Server Ownership can use this command.").setEphemeral(true).queue();
            return;
        }

        long lastPing = dataService.getLastPingTimestamp(OwnershipPingService.PING_KEY);
        ZonedDateTime now = ZonedDateTime.now(OwnershipPingService.EST_ZONE);

        if (lastPing == 0) {
            event.replyEmbeds(EmbedUtils.createEmbed(
                    Color.ORANGE,
                    "No ownership schedule baseline is set yet. The next automated check will initialize it."
            )).setEphemeral(true).queue();
            return;
        }

        ZonedDateTime lastPingDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastPing), OwnershipPingService.EST_ZONE);

        // Mirror service undo of mistaken July migration for display accuracy
        if (now.isBefore(OwnershipPingService.FINAL_QUARTERLY_DUE)
                && lastPingDate.toLocalDate().equals(OwnershipPingService.YEARLY_BASELINE.toLocalDate())) {
            lastPingDate = OwnershipPingService.LEGACY_BASELINE;
        }

        ZonedDateTime nextDue = OwnershipPingService.nextDueFrom(lastPingDate);
        int interval = OwnershipPingService.currentIntervalMonths(lastPingDate);
        boolean quarterly = OwnershipPingService.isQuarterlyPhase(lastPingDate);
        long monthsPassed = ChronoUnit.MONTHS.between(lastPingDate, now);
        long monthsRemaining = Math.max(0, interval - monthsPassed);
        boolean isDue = !now.isBefore(nextDue);

        String status = isDue
                ? "⚠️ **Due now** — ownership reminder is overdue."
                : "✅ **On track** — next reminder in about " + monthsRemaining + " month(s).";

        String intervalLabel = quarterly
                ? "Final quarterly → then yearly (July 16)"
                : "Yearly (every July 16)";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(BotConfig.TW_EMOJI_MENTION + " Ownership Schedule")
                .setColor(isDue ? new Color(0xFFAA00) : new Color(0x00E1FF))
                .setDescription(status)
                .addField("📅 Last Cycle", lastPingDate.format(DATE_FORMATTER), true)
                .addField("🗓️ Next Due", nextDue.format(DATE_FORMATTER), true)
                .addField("⏱️ Interval", intervalLabel, true)
                .setFooter("Ownership Only")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
