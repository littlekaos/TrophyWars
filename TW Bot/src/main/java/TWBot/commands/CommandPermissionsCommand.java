package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CommandPermissionsCommand implements Command {

    private final TWBot bot;
    private final DataService dataService;

    public CommandPermissionsCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = bot.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("cmdperm", "Manage command permissions for roles (Server Ownership Only)")
                .addSubcommandGroups(
                        new SubcommandGroupData("list", "List command permissions")
                                .addSubcommands(
                                        new SubcommandData("all", "List permissions for all commands"),
                                        new SubcommandData("command", "List permissions for a specific command")
                                                .addOption(OptionType.STRING, "name", "The command name", true)
                                )
                )
                .addSubcommands(
                        new SubcommandData("enable", "Enable a command for a specific role")
                                .addOption(OptionType.STRING, "command", "The command name", true)
                                .addOption(OptionType.ROLE, "role", "The role to enable it for", true),
                        new SubcommandData("disable", "Disable a command for a specific role")
                                .addOption(OptionType.STRING, "command", "The command name", true)
                                .addOption(OptionType.ROLE, "role", "The role to disable it for", true),
                        new SubcommandData("reset", "Reset permissions for a command and role")
                                .addOption(OptionType.STRING, "command", "The command name", true)
                                .addOption(OptionType.ROLE, "role", "The role to reset", true)
                ));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean isOwner = event.getUser().getId().equals(BotConfig.OWNER_USER_ID);
        boolean hasOwnershipRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(BotConfig.SERVER_OWNERSHIP_ROLE_ID));

        if (!isOwner && !hasOwnershipRole) {
            event.reply("❌ Only Server Ownership can manage command permissions.").setEphemeral(true).queue();
            return;
        }

        String group = event.getSubcommandGroup();
        String subcommand = event.getSubcommandName();

        if ("list".equals(group)) {
            if ("all".equals(subcommand)) {
                handleListAll(event);
            } else if ("command".equals(subcommand)) {
                String commandName = Objects.requireNonNull(event.getOption("name")).getAsString().toLowerCase();
                if (commandName.startsWith("/")) commandName = commandName.substring(1);
                handleList(event, commandName);
            }
            return;
        }

        String commandName = Objects.requireNonNull(event.getOption("command")).getAsString().toLowerCase();
        if (commandName.startsWith("/")) commandName = commandName.substring(1);

        switch (Objects.requireNonNull(subcommand)) {
            case "enable" -> handleEnable(event, commandName, true);
            case "disable" -> handleEnable(event, commandName, false);
            case "reset" -> handleReset(event, commandName);
        }
    }

    private void handleEnable(SlashCommandInteractionEvent event, String commandName, boolean enabled) {
        Role role = Objects.requireNonNull(event.getOption("role")).getAsRole();
        dataService.setCommandPermission(commandName, role.getId(), enabled);
        
        String status = enabled ? "enabled" : "disabled";
        event.reply("✅ Command `/" + commandName + "` is now **" + status + "** for role " + role.getAsMention() + ".")
                .setEphemeral(true).queue();
    }

    private void handleReset(SlashCommandInteractionEvent event, String commandName) {
        Role role = Objects.requireNonNull(event.getOption("role")).getAsRole();
        dataService.removeCommandPermission(commandName, role.getId());
        
        event.reply("✅ Reset permissions for command `/" + commandName + "` and role " + role.getAsMention() + ".")
                .setEphemeral(true).queue();
    }

    private void handleList(SlashCommandInteractionEvent event, String commandName) {
        Map<String, Boolean> perms = dataService.getPermissionsForCommand(commandName);
        
        if (perms.isEmpty()) {
            event.reply("ℹ️ No custom permissions set for command `/" + commandName + "`.").setEphemeral(true).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Permissions for `/").append(commandName).append("`:**\n\n");
        
        for (Map.Entry<String, Boolean> entry : perms.entrySet()) {
            sb.append("<@&").append(entry.getKey()).append(">: ")
                    .append(entry.getValue() ? "✅ Enabled" : "❌ Disabled")
                    .append("\n");
        }

        event.replyEmbeds(EmbedUtils.createEmbed(Color.CYAN, sb.toString())).setEphemeral(true).queue();
    }

    private void handleListAll(SlashCommandInteractionEvent event) {
        Map<String, Map<String, Boolean>> allPerms = dataService.getAllCommandPermissions();
        
        if (allPerms.isEmpty()) {
            event.reply("ℹ️ No custom command permissions are currently set.").setEphemeral(true).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**All Custom Command Permissions:**\n\n");
        
        for (Map.Entry<String, Map<String, Boolean>> cmdEntry : allPerms.entrySet()) {
            sb.append("🔹 `").append(cmdEntry.getKey()).append("`:\n");
            for (Map.Entry<String, Boolean> roleEntry : cmdEntry.getValue().entrySet()) {
                sb.append("  • <@&").append(roleEntry.getKey()).append(">: ")
                        .append(roleEntry.getValue() ? "✅ Enabled" : "❌ Disabled")
                        .append("\n");
            }
            sb.append("\n");
        }

        event.replyEmbeds(EmbedUtils.createEmbed(Color.CYAN, sb.toString())).setEphemeral(true).queue();
    }
}
