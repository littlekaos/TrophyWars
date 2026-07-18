package TWBot.events;

import TWBot.TWBot;
import TWBot.commands.*;
import TWBot.config.BotConfig;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandEventListener extends ListenerAdapter {
    private final TWBot bot;
    private final Map<String, Command> commands = new HashMap<>();

    public CommandEventListener(TWBot bot) {
        this.bot = bot;
        registerCommandHandlers();
    }

    private void registerCommandHandlers() {
        registerCommand(new TestCommand());
        registerCommand(new HelpCommand());
        registerCommand(new EventNameCommand(bot));
        registerCommand(new BanCommand(bot));
        registerCommand(new VoidCheckerCommand(bot));
        registerCommand(new RoleCommand(bot));
        registerCommand(new ReasonCommand(bot));
        registerCommand(new StrikeCommand(bot));
        registerCommand(new AppealCommand(bot));
        registerCommand(new DemotionCommand(bot));
        registerCommand(new AdminStrikeCommand(bot));
        registerCommand(new WarnCommand(bot));
        registerCommand(new MuteCommand(bot));
        registerCommand(new UnmuteCommand(bot));
        registerCommand(new SetMuteRoleCommand(bot));
        registerCommand(new TimeoutCommand(bot));
        registerCommand(new UntimeoutCommand(bot));
        registerCommand(new UnbanCommand(bot));
        registerCommand(new KickCommand(bot));
        registerCommand(new PurgeCommand(bot));
        registerCommand(new RestrictCommand(bot));
        registerCommand(new UnrestrictCommand(bot));
        registerCommand(new RestrictSetupCommand(bot));
        registerCommand(new VoiceCommand(bot));
        registerCommand(new SayCommand(bot));
        registerCommand(new CommandPermissionsCommand(bot));
        registerCommand(new StealCommand(bot));
        registerCommand(new PaymentDueCommand());
    }

    private void registerCommand(Command command) {
        for (CommandData data : command.getCommandDataList()) {
            commands.put(data.getName(), command);
        }
    }

    public static void registerCommands(JDA jda, BotConfig config, TWBot bot) {
        try {
            List<CommandData> allCommands = new ArrayList<>();
            allCommands.addAll(new TestCommand().getCommandDataList());
            allCommands.addAll(new HelpCommand().getCommandDataList());
            allCommands.addAll(new EventNameCommand(bot).getCommandDataList());
            allCommands.addAll(new BanCommand(bot).getCommandDataList());
            allCommands.addAll(new VoidCheckerCommand(bot).getCommandDataList());
            allCommands.addAll(new RoleCommand(bot).getCommandDataList());
            allCommands.addAll(new ReasonCommand(bot).getCommandDataList());
            allCommands.addAll(new StrikeCommand(bot).getCommandDataList());
            allCommands.addAll(new AppealCommand(bot).getCommandDataList());
            allCommands.addAll(new DemotionCommand(bot).getCommandDataList());
            allCommands.addAll(new AdminStrikeCommand(bot).getCommandDataList());
            allCommands.addAll(new WarnCommand(bot).getCommandDataList());
            allCommands.addAll(new MuteCommand(bot).getCommandDataList());
            allCommands.addAll(new UnmuteCommand(bot).getCommandDataList());
            allCommands.addAll(new SetMuteRoleCommand(bot).getCommandDataList());
            allCommands.addAll(new TimeoutCommand(bot).getCommandDataList());
            allCommands.addAll(new UntimeoutCommand(bot).getCommandDataList());
            allCommands.addAll(new UnbanCommand(bot).getCommandDataList());
            allCommands.addAll(new KickCommand(bot).getCommandDataList());
            allCommands.addAll(new PurgeCommand(bot).getCommandDataList());
            allCommands.addAll(new RestrictCommand(bot).getCommandDataList());
            allCommands.addAll(new UnrestrictCommand(bot).getCommandDataList());
            allCommands.addAll(new RestrictSetupCommand(bot).getCommandDataList());
            allCommands.addAll(new VoiceCommand(bot).getCommandDataList());
            allCommands.addAll(new SayCommand(bot).getCommandDataList());
            allCommands.addAll(new CommandPermissionsCommand(bot).getCommandDataList());
            allCommands.addAll(new StealCommand(bot).getCommandDataList());
            allCommands.addAll(new PaymentDueCommand().getCommandDataList());

            // Clear global commands to avoid duplicates if they were registered previously
            jda.updateCommands().queue();

            String guildId = config.getGuildId();
            if (guildId != null && !guildId.isEmpty()) {
                net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
                if (guild != null) {
                    guild.updateCommands()
                            .addCommands(allCommands)
                            .queue(commands -> System.out.println("Successfully registered " + commands.size() + " guild slash commands for guild: " + guild.getName()));
                } else {
                    System.err.println("CRITICAL: Could not find guild with ID " + guildId + ". Commands NOT registered!");
                }
            } else {
                System.err.println("CRITICAL: GUILD_ID not configured. Commands NOT registered!");
            }
        } catch (Exception e) {
            System.err.println("Error registering commands: " + e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        Command command = commands.get(commandName);

        if (command != null) {
            try {
                // Check dynamic permissions
                if (!checkPermissions(event, commandName, command)) {
                    return;
                }
                command.execute(event);
            } catch (Exception e) {
                e.printStackTrace();
                if (!event.isAcknowledged()) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "An error occurred while executing this command: " + e.getMessage()
                    )).setEphemeral(true).queue();
                }
            }
        } else {
            event.reply("Unknown command: " + commandName).setEphemeral(true).queue();
        }
    }

    private boolean checkPermissions(SlashCommandInteractionEvent event, String commandName, Command command) {
        if (PermissionUtils.isBotOwner(event.getUser())) return true;

        if (event.getMember() == null) return false;

        if ("paymentdue".equals(commandName)) {
            boolean hasOwnershipRole = event.getMember().getRoles().stream()
                    .anyMatch(role -> role.getId().equals(BotConfig.SERVER_OWNERSHIP_ROLE_ID));
            if (!hasOwnershipRole) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("Only Server Ownership can use this command.")).setEphemeral(true).queue();
                return false;
            }
            return true;
        }

        // Server Ownership role has access to ALL commands
        if (event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(BotConfig.SERVER_OWNERSHIP_ROLE_ID))) return true;

        // Admin roles always have access to all commands
        boolean isAdmin = event.getMember().getRoles().stream()
                .anyMatch(role -> bot.getConfig().getAdminRoles().contains(role.getId()));
        if (isAdmin) return true;

        Map<String, Boolean> perms = bot.getDataService().getPermissionsForCommand(commandName);
        
        List<String> userRoleIds = event.getMember().getRoles().stream()
                .map(net.dv8tion.jda.api.entities.Role::getId)
                .toList();

        // If there ARE custom permissions set for this command...
        if (!perms.isEmpty()) {
            // Check for explicit disables for ANY of the user's roles
            for (String roleId : userRoleIds) {
                if (perms.containsKey(roleId) && !perms.get(roleId)) {
                    event.reply("❌ This command has been disabled for your role by Server Ownership.")
                            .setEphemeral(true).queue();
                    return false;
                }
            }

            // Check for explicit enables for ANY of the user's roles
            for (String roleId : userRoleIds) {
                if (perms.containsKey(roleId) && perms.get(roleId)) {
                    return true; // Explicitly allowed by cmdperm!
                }
            }

            // If there are ANY "enabled" entries for this command, and user has none, deny access
            boolean hasAnyEnable = perms.values().stream().anyMatch(b -> b);
            if (hasAnyEnable) {
                event.reply("❌ This command is not enabled for your roles.")
                        .setEphemeral(true).queue();
                return false;
            }
        }

        // Fallback to command's default permission check
        if (!command.hasPermission(event, bot)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use this command.")).setEphemeral(true).queue();
            return false;
        }

        return true;
    }
}