package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.database.DatabaseManager;
import TWBot.models.Strike;
import TWBot.services.AppealScannerService;
import TWBot.services.RoleRestorationService;
import TWBot.services.StrikeService;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.*;
import java.util.List;
import java.util.Objects;

public class AdminStrikeCommand implements Command {

    private final TWBot bot;
    private final StrikeService strikeService;
    private final AppealScannerService appealScannerService;
    private final RoleRestorationService roleRestorationService;

    public AdminStrikeCommand(TWBot bot) {
        this.bot = bot;
        this.strikeService = bot.getStrikeService();
        this.appealScannerService = bot.getAppealScannerService();
        this.roleRestorationService = bot.getRoleRestorationService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("dbinfo", "Show database statistics."),
                Commands.slash("backupstrikes", "Create a full backup of the bot database."),
                Commands.slash("checkroles", "Check and restore temporary role demotions (Admin Only)"),
                Commands.slash("appealscanner", "Manage the appeal scanner service. (Admin Only)")
                        .addOption(OptionType.STRING, "action", "scan, stats, or status", true),
                Commands.slash("rolerestoration", "Manage the role restoration service. (Admin Only)")
                        .addOption(OptionType.STRING, "action", "status or check", true)
        );
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isAdmin(event.getMember(), bot.getConfig()) || event.getUser().getId().equals(BotConfig.OWNER_USER_ID);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        switch (commandName) {
            case "dbinfo" -> handleDbInfo(event);
            case "backupstrikes" -> handleBackup(event);
            case "checkroles" -> handleCheckRoles(event);
            case "appealscanner" -> handleScanner(event);
            case "rolerestoration" -> handleRestoration(event);
        }
    }

    private void handleDbInfo(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        List<String> usersWithStrikes = strikeService.getAllUsersWithStrikes();
        StringBuilder info = new StringBuilder();
        info.append("**All Users With Strikes:**\n\n");

        int totalStrikes = 0;
        int totalUsers = 0;

        for (String userId : usersWithStrikes) {
            List<Strike> strikes = strikeService.getStrikes(userId);
            if (!strikes.isEmpty()) {
                totalUsers++;
                totalStrikes += strikes.size();
                info.append(String.format("• <@%s> (%s): **%d** strikes\n", userId, userId, strikes.size()));
            }
        }

        info.append(String.format("\n**Summary:**\n"));
        info.append(String.format("• Total users with strikes: **%d**\n", totalUsers));
        info.append(String.format("• Total strikes issued: **%d**\n", totalStrikes));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Database Info")
                .setDescription(info.toString() + "\n")
                .setColor(Color.CYAN)
                .setTimestamp(java.time.Instant.now());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleBackup(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        try {
            var backupPath = DatabaseManager.getInstance().createBackup();
            int totalStrikes = strikeService.getDatabase().getTotalStrikeCount();
            int totalUsers = strikeService.getAllUsersWithStrikes().size();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("💾 Database Backup Created")
                    .setDescription("A full SQLite snapshot of the bot database was saved.")
                    .setColor(Color.CYAN)
                    .addField("Backup File", "`" + backupPath.getFileName() + "`", false)
                    .addField("Location", "`" + backupPath.getParent() + "`", false)
                    .addField("Total Strikes", String.valueOf(totalStrikes), true)
                    .addField("Total Users", String.valueOf(totalUsers), true)
                    .addField("Retention", "Only one backup kept (overwritten each time)", false)
                    .setTimestamp(java.time.Instant.now());

            event.getHook().editOriginalEmbeds(embed.build()).queue();
        } catch (Exception e) {
            event.getHook().editOriginal("❌ Backup failed: " + e.getMessage()).queue();
        }
    }

    private void handleCheckRoles(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        roleRestorationService.triggerManualCheck();
        event.getHook().editOriginal("✅ Role restoration check triggered.").queue();
    }

    private void handleScanner(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String action = Objects.requireNonNull(event.getOption("action")).getAsString().toLowerCase();

        switch (action) {
            case "scan" -> {
                appealScannerService.triggerManualScan();
                event.getHook().editOriginal("✅ Manual scan triggered.").queue();
            }
            case "stats" -> {
                event.getHook().editOriginal("📊 Scanner stats: " + appealScannerService.getScanStats()).queue();
            }
            case "status" -> {
                event.getHook().editOriginal("⚙️ Scanner is currently " + (appealScannerService.isScannerRunning() ? "RUNNING" : "IDLE")).queue();
            }
            default -> event.getHook().editOriginal("❌ Unknown action: " + action).queue();
        }
    }

    private void handleRestoration(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String action = Objects.requireNonNull(event.getOption("action")).getAsString().toLowerCase();

        switch (action) {
            case "status" -> {
                event.getHook().editOriginal("⚙️ Role restoration is " + (roleRestorationService.isRunning() ? "ACTIVE" : "INACTIVE")).queue();
            }
            case "check" -> {
                roleRestorationService.triggerManualCheck();
                event.getHook().editOriginal("✅ Manual check triggered.").queue();
            }
            default -> event.getHook().editOriginal("❌ Unknown action: " + action).queue();
        }
    }
}
