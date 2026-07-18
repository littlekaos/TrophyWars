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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class OwnershipCycleCommand implements Command {

    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
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
        ZonedDateTime now = ZonedDateTime.now(EST_ZONE);

        if (lastPing == 0) {
            event.replyEmbeds(EmbedUtils.createEmbed(
                    Color.ORANGE,
                    "No ownership schedule baseline is set yet. The next automated check will initialize it."
            )).setEphemeral(true).queue();
            return;
        }

        ZonedDateTime lastPingDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastPing), EST_ZONE);
        ZonedDateTime nextDue = lastPingDate.plusMonths(OwnershipPingService.INTERVAL_MONTHS);
        long monthsPassed = ChronoUnit.MONTHS.between(lastPingDate, now);
        long monthsRemaining = Math.max(0, OwnershipPingService.INTERVAL_MONTHS - monthsPassed);
        boolean isDue = monthsPassed >= OwnershipPingService.INTERVAL_MONTHS;

        String status = isDue
                ? "⚠️ **Due now** — the " + OwnershipPingService.INTERVAL_MONTHS + "-month cycle is overdue."
                : "✅ **On track** — " + monthsRemaining + " month(s) remaining in the current cycle.";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(BotConfig.TW_EMOJI_MENTION + " Ownership Schedule")
                .setColor(isDue ? new Color(0xFFAA00) : new Color(0x00E1FF))
                .setDescription(status)
                .addField("📅 Last Cycle", lastPingDate.format(DATE_FORMATTER), true)
                .addField("🗓️ Next Due", nextDue.format(DATE_FORMATTER), true)
                .addField("⏱️ Interval", OwnershipPingService.INTERVAL_MONTHS + " months", true)
                .setFooter("Ownership Only")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
