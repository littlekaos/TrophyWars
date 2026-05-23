package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnmuteCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public UnmuteCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("unmute", "Unmute a user")
                .addOption(OptionType.USER, "user", "The user to unmute", true)
                .addOption(OptionType.STRING, "reason", "The reason for the unmute", true));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();

        Guild guild = event.getGuild();
        String muteRoleId = dataService.getMuteRoleId(guild.getId());

        if (muteRoleId == null) {
            event.reply("❌ No mute role has been set for this server.").setEphemeral(true).queue();
            return;
        }

        Role muteRole = guild.getRoleById(muteRoleId);
        if (muteRole == null) {
            event.reply("❌ The configured mute role no longer exists.").setEphemeral(true).queue();
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("targetId", targetUser.getId());
        params.put("targetName", targetUser.getName());
        params.put("reason", reason);
        params.put("muteRoleId", muteRoleId);

        // Execute directly without approval
        event.deferReply().setEphemeral(true).queue();
        
        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!event.getMember().canInteract(targetMember)) {
                event.getHook().editOriginal("❌ You cannot unmute this user due to role hierarchy.").queue();
                return;
            }

            if (!targetMember.getRoles().contains(muteRole)) {
                event.getHook().editOriginal("❌ This user is not currently muted.").queue();
                return;
            }
            
            doUnmuteManual(event, targetUser.getId(), targetUser.getName(), reason, muteRole);
        }, error -> event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").queue());
    }

    private void doUnmuteManual(SlashCommandInteractionEvent event, String targetId, String targetName, String reason, Role muteRole) {
        Guild guild = event.getGuild();
        guild.retrieveMemberById(targetId).queue(targetMember -> {
            guild.removeRoleFromMember(targetMember, muteRole).queue(success -> {
                dataService.removeMute(guild.getId(), targetId);
                dataService.saveModAction(ModAction.ActionType.UNMUTE, event.getUser().getId(), event.getUser().getName(),
                        targetId, targetName, reason, 0, 0);

                event.getHook().editOriginal("✅ User has been unmuted successfully.").setEmbeds().setComponents().queue();
                
                bot.getLoggingService().logModAction(guild, "User Unmuted", event.getUser(), targetMember.getUser(), reason, null, Color.GREEN);
            }, error -> {
                event.getHook().editOriginal("❌ Error: Could not unmute the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").setEmbeds().setComponents().queue());
    }

    public void proceedWithUnmute(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, Map<String, Object> params, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        String targetId = (String) params.get("targetId");
        String targetName = (String) params.get("targetName");
        String reason = (String) params.get("reason");
        String muteRoleId = (String) params.get("muteRoleId");

        Role muteRole = guild.getRoleById(muteRoleId);

        guild.retrieveMemberById(targetId).queue(targetMember -> {
            if (!moderator.canInteract(targetMember)) {
                if (hook != null) hook.editOriginal("❌ You cannot unmute this user due to role hierarchy.").setEmbeds().setComponents().queue();
                return;
            }

            if (!targetMember.getRoles().contains(muteRole)) {
                if (hook != null) hook.editOriginal("❌ This user is not currently muted.").setEmbeds().setComponents().queue();
                return;
            }

            guild.removeRoleFromMember(targetMember, muteRole).queue(success -> {
                dataService.removeMute(guild.getId(), targetId);
                dataService.saveModAction(ModAction.ActionType.UNMUTE, moderator.getUser().getId(), moderator.getUser().getName(),
                        targetId, targetName, reason, 0, 0);

                if (hook != null) hook.editOriginal("✅ User has been unmuted successfully.").setEmbeds().setComponents().queue();
                
                bot.getLoggingService().logModAction(guild, "User Unmuted", moderator.getUser(), targetMember.getUser(), reason, null, Color.GREEN);
            }, error -> {
                if (hook != null) hook.editOriginal("❌ Error: Could not unmute the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> {
            if (hook != null) hook.editOriginal("❌ Error: Cannot find the user in this server.").setEmbeds().setComponents().queue();
        });
    }
}
