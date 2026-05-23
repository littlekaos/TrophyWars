package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.EmbedBuilder;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoleCommand implements Command {
    private final TWBot bot;

    private static final SubcommandData ROLE_ADD = new SubcommandData(
            "add", "Add a role to a user"
    )
            .addOption(OptionType.USER, "user", "The user to add the role to", true)
            .addOption(OptionType.ROLE, "role", "The role to add", true);

    private static final SubcommandData ROLE_REMOVE = new SubcommandData(
            "remove", "Remove a role from a user"
    )
            .addOption(OptionType.USER, "user", "The user to remove the role from", true)
            .addOption(OptionType.ROLE, "role", "The role to remove", true);

    public RoleCommand(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("role", "Role management")
                .addSubcommands(ROLE_ADD, ROLE_REMOVE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getSubcommandName();
        if (subcommandName == null) {
            event.reply("Invalid subcommand!").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        switch (subcommandName) {
            case "add":
                handleRoleAdd(event, guild);
                break;
            case "remove":
                handleRoleRemove(event, guild);
                break;
            default:
                event.reply("Unknown subcommand: " + subcommandName).setEphemeral(true).queue();
        }
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig()) || PermissionUtils.isLead(event.getMember(), bot.getConfig());
    }

    private void handleRoleAdd(SlashCommandInteractionEvent event, Guild guild) {
        User targetUser = event.getOption("user").getAsUser();
        Role targetRole = event.getOption("role").getAsRole();
        Member moderator = event.getMember();

        // Prevent self-assignment of roles
        if (targetUser.getId().equals(moderator.getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You cannot assign roles to yourself."
            )).setEphemeral(true).queue();
            return;
        }

        if (!canManageRole(moderator, targetRole, guild)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You cannot add this role because it's equal to or higher than your highest role."
            )).setEphemeral(true).queue();
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("targetId", targetUser.getId());
        params.put("targetName", targetUser.getName());
        params.put("roleId", targetRole.getId());
        params.put("roleName", targetRole.getName());
        params.put("action", "add");

        // Execute directly without approval
        event.deferReply().setEphemeral(true).queue();
        
        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            doRoleManual(event, targetMember, targetRole, "add");
        }, error -> event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed("Could not find that user in this server.")).queue());
    }

    private void handleRoleRemove(SlashCommandInteractionEvent event, Guild guild) {
        User targetUser = event.getOption("user").getAsUser();
        Role targetRole = event.getOption("role").getAsRole();
        Member moderator = event.getMember();

        if (!canManageRole(moderator, targetRole, guild)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You cannot remove this role because it's equal to or higher than your highest role."
            )).setEphemeral(true).queue();
            return;
        }

        // Execute directly without approval
        event.deferReply().setEphemeral(true).queue();
        
        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            doRoleManual(event, targetMember, targetRole, "remove");
        }, error -> event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed("Could not find that user in this server.")).queue());
    }

    private void doRoleManual(SlashCommandInteractionEvent event, Member targetMember, Role targetRole, String action) {
        Guild guild = event.getGuild();
        Member moderator = event.getMember();
        String targetName = targetMember.getUser().getName();
        String roleName = targetRole.getName();
        String roleId = targetRole.getId();
        String targetId = targetMember.getId();

        if (action.equals("add")) {
            // 1. Check for demotion protection
            boolean isPermDemoted = bot.getDemotionService().isPermanentlyDemoted(targetId);
            boolean isTempDemoted = bot.getDemotionService().isTemporarilyDemoted(targetId);

            if (isPermDemoted || isTempDemoted) {
                String type = isPermDemoted ? "permanently" : "temporarily";
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Cannot add roles to " + targetName + " because they are " + type + " demoted."
                )).queue();
                return;
            }

            if (targetMember.getRoles().contains(targetRole)) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createWarningEmbed(
                        targetName + " already has the role " + roleName + "."
                )).queue();
                return;
            }

            guild.addRoleToMember(targetMember, targetRole).queue(
                    success -> {
                        logRoleAction(guild, "added", moderator.getUser(), targetMember.getUser(), targetRole);
                        event.getHook().editOriginalEmbeds(EmbedUtils.createEmbed(
                                "Role Added",
                                Color.GREEN,
                                "✅ Successfully added role " + roleName + " to " + targetName
                        )).queue();
                    },
                    error -> {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Failed to add role: " + error.getMessage()
                        )).queue();
                    }
            );
        } else {
            if (!targetMember.getRoles().contains(targetRole)) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createWarningEmbed(
                        targetName + " doesn't have the role " + roleName + "."
                )).queue();
                return;
            }

            guild.removeRoleFromMember(targetMember, targetRole).queue(
                    success -> {
                        logRoleAction(guild, "removed", moderator.getUser(), targetMember.getUser(), targetRole);
                        event.getHook().editOriginalEmbeds(EmbedUtils.createEmbed(
                                "Role Removed",
                                Color.GREEN,
                                "✅ Successfully removed role " + roleName + " from " + targetName
                        )).queue();
                    },
                    error -> {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Failed to remove role: " + error.getMessage()
                        )).queue();
                    }
            );
        }
    }

    public void proceedWithRoleAction(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, Map<String, Object> params, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        String targetId = (String) params.get("targetId");
        String targetName = (String) params.get("targetName");
        String roleId = (String) params.get("roleId");
        String roleName = (String) params.get("roleName");
        String action = (String) params.get("action");

        Role targetRole = guild.getRoleById(roleId);

        if (targetRole == null) {
            if (hook != null) hook.editOriginal("❌ Role no longer exists.").setEmbeds().setComponents().queue();
            return;
        }

        guild.retrieveMemberById(targetId).queue(targetMember -> {
            if (action.equals("add")) {
                // 1. Check for demotion protection
                boolean isPermDemoted = bot.getDemotionService().isPermanentlyDemoted(targetId);
                boolean isTempDemoted = bot.getDemotionService().isTemporarilyDemoted(targetId);

                if (isPermDemoted || isTempDemoted) {
                    String type = isPermDemoted ? "permanently" : "temporarily";
                    if (hook != null) hook.editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                            "Cannot add roles to " + targetName + " because they are " + type + " demoted."
                    )).setComponents().queue();
                    return;
                }

                if (targetMember.getRoles().contains(targetRole)) {
                    if (hook != null) hook.editOriginalEmbeds(EmbedUtils.createWarningEmbed(
                            targetName + " already has the role " + roleName + "."
                    )).setComponents().queue();
                    return;
                }

                guild.addRoleToMember(targetMember, targetRole).queue(
                        success -> {
                            logRoleAction(guild, "added", moderator.getUser(), targetMember.getUser(), targetRole);
                            if (hook != null) hook.editOriginalEmbeds(EmbedUtils.createEmbed(
                                    "Role Added",
                                    Color.GREEN,
                                    "✅ Successfully added role " + roleName + " to " + targetName
                            )).setComponents().queue();
                        },
                        error -> {
                            if (hook != null) hook.editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                    "Failed to add role: " + error.getMessage()
                            )).setComponents().queue();
                        }
                );
            } else {
                if (!targetMember.getRoles().contains(targetRole)) {
                    if (hook != null) hook.editOriginalEmbeds(EmbedUtils.createWarningEmbed(
                            targetName + " doesn't have the role " + roleName + "."
                    )).setComponents().queue();
                    return;
                }

                guild.removeRoleFromMember(targetMember, targetRole).queue(
                        success -> {
                            logRoleAction(guild, "removed", moderator.getUser(), targetMember.getUser(), targetRole);
                            if (hook != null) hook.editOriginalEmbeds(EmbedUtils.createEmbed(
                                    "Role Removed",
                                    Color.GREEN,
                                    "✅ Successfully removed role " + roleName + " from " + targetName
                            )).setComponents().queue();
                        },
                        error -> {
                            if (hook != null) hook.editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                    "Failed to remove role: " + error.getMessage()
                            )).setComponents().queue();
                        }
                );
            }
        }, error -> {
            if (hook != null) hook.editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Could not find that user in this server."
            )).setComponents().queue();
        });
    }

    private boolean canManageRole(Member moderator, Role targetRole, Guild guild) {
        if (moderator.isOwner()) {
            return true;
        }

        Role highestRole = moderator.getRoles().isEmpty() ? null : moderator.getRoles().get(0);

        if (highestRole == null) {
            return false;
        }

        return highestRole.getPosition() > targetRole.getPosition();
    }

    private void logRoleAction(Guild guild, String action, User moderator, User target, Role role) {
        String title = action.equalsIgnoreCase("added") ? "Role Added" : "Role Removed";
        Color color = action.equalsIgnoreCase("added") ? Color.GREEN : Color.RED;
        bot.getLoggingService().logModAction(guild, title, moderator, target, role.getName(), null, color);
    }
}