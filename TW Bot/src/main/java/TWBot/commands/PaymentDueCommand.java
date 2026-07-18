package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.utils.OwnershipScheduleUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class PaymentDueCommand implements Command {

    private static final int SCHEDULE_COUNT = 6;

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("paymentdue", "View the ownership payment due schedule (every 3 months)")
                .setDefaultPermissions(DefaultMemberPermissions.DISABLED));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isAdmin(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ZonedDateTime now = ZonedDateTime.now(OwnershipScheduleUtils.EST_ZONE);
        ZonedDateTime nextDueDate = OwnershipScheduleUtils.getNextDueDate(now);
        List<ZonedDateTime> upcomingDueDates = OwnershipScheduleUtils.getUpcomingDueDates(now, SCHEDULE_COUNT);

        String schedule = upcomingDueDates.stream()
                .map(date -> {
                    if (date.equals(nextDueDate)) {
                        return "**" + date.format(OwnershipScheduleUtils.DATE_FORMATTER) + "** (next due)";
                    }
                    return date.format(OwnershipScheduleUtils.DATE_FORMATTER);
                })
                .collect(Collectors.joining("\n"));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(BotConfig.TW_EMOJI_MENTION + " Ownership Payment Schedule")
                .setColor(new Color(0x00E1FF))
                .setDescription("Ownership payments are due every **3 months**, starting from the baseline date below.")
                .addField("📅 Baseline Date", OwnershipScheduleUtils.BASELINE_DATE.format(OwnershipScheduleUtils.DATE_FORMATTER), true)
                .addField("🕒 Current Date", now.format(OwnershipScheduleUtils.DATE_FORMATTER), true)
                .addField("⏭️ Next Payment Due", nextDueDate.format(OwnershipScheduleUtils.DATE_FORMATTER), true)
                .addField("🗓️ Upcoming Due Dates", schedule, false)
                .setFooter("Payments recur every 3 months")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
