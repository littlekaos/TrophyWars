package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.FormatUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MuteCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public MuteCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("mute", "Mute a user")
                .addOption(OptionType.USER, "user", "The user to mute", true)
                .addOption(OptionType.STRING, "reason", "The reason for the mute", true)
                .addOption(OptionType.STRING, "duration", "Duration (e.g., 1h, 7d, 30m)", false));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        String durationStr = event.getOption("duration") != null ? event.getOption("duration").getAsString() : null;

        Guild guild = event.getGuild();
        String muteRoleId = dataService.getMuteRoleId(guild.getId());

        if (muteRoleId == null) {
            event.reply("❌ No mute role has been set for this server. Please use `/setmuterole` first.").setEphemeral(true).queue();
            return;
        }

        Role muteRole = guild.getRoleById(muteRoleId);
        if (muteRole == null) {
            event.reply("❌ The configured mute role no longer exists. Please use `/setmuterole` to set a new one.").setEphemeral(true).queue();
            return;
        }

        long parsedDuration = FormatUtils.parseDurationToMinutes(durationStr);
        final Integer duration = (durationStr != null && parsedDuration > 0) ? (int) parsedDuration : null;

        Map<String, Object> params = new HashMap<>();
        params.put("targetId", targetUser.getId());
        params.put("targetName", targetUser.getName());
        params.put("reason", reason);
        params.put("duration", duration);
        params.put("muteRoleId", muteRoleId);

        // Execute directly without approval
        event.deferReply().setEphemeral(true).queue();
        
        event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            if (!PermissionUtils.canModerate(event.getMember(), targetMember)) {
                event.getHook().editOriginal("❌ You cannot mute this user due to role hierarchy.").queue();
                return;
            }
            
            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.getHook().editOriginal("❌ I cannot mute this user due to role hierarchy.").queue();
                return;
            }
            
            doMuteManual(event, targetUser.getId(), targetUser.getName(), reason, duration, muteRole);
        }, error -> event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").queue());
    }

    private void doMuteManual(SlashCommandInteractionEvent event, String targetId, String targetName, String reason, Integer duration, Role muteRole) {
        Guild guild = event.getGuild();
        guild.retrieveMemberById(targetId).queue(targetMember -> {
            guild.addRoleToMember(targetMember, muteRole).queue(success -> {
                String durationText = duration != null ? duration + " minutes" : "Permanent";
                dataService.saveModAction(ModAction.ActionType.MUTE, event.getUser().getId(), event.getUser().getName(),
                        targetId, targetName, reason, duration != null ? duration : 0, 0);

                long unmuteTime = (duration != null) ? System.currentTimeMillis() + (duration * 60 * 1000L) : 0;
                dataService.addMute(guild.getId(), targetId, unmuteTime);

                event.getHook().editOriginal("✅ User has been muted successfully.").setEmbeds().setComponents().queue();
                
                bot.getLoggingService().logModAction(guild, "User Muted", event.getUser(), targetMember.getUser(), reason, durationText, Color.RED);
            }, error -> {
                event.getHook().editOriginal("❌ Error: Could not mute the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> event.getHook().editOriginal("❌ Error: Cannot find the user in this server.").setEmbeds().setComponents().queue());
    }

    public void proceedWithMute(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, Map<String, Object> params, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        String targetId = (String) params.get("targetId");
        String targetName = (String) params.get("targetName");
        String reason = (String) params.get("reason");
        Integer duration = (Integer) params.get("duration");
        String muteRoleId = (String) params.get("muteRoleId");

        Role muteRole = guild.getRoleById(muteRoleId);

        guild.retrieveMemberById(targetId).queue(targetMember -> {
            if (!PermissionUtils.canModerate(moderator, targetMember)) {
                if (hook != null) hook.editOriginal("❌ You cannot mute this user due to role hierarchy.").setEmbeds().setComponents().queue();
                return;
            }

            if (!guild.getSelfMember().canInteract(targetMember)) {
                if (hook != null) hook.editOriginal("❌ I cannot mute this user due to role hierarchy.").setEmbeds().setComponents().queue();
                return;
            }

            guild.addRoleToMember(targetMember, muteRole).queue(success -> {
                String durationText = duration != null ? duration + " minutes" : "Permanent";
                dataService.saveModAction(ModAction.ActionType.MUTE, moderator.getUser().getId(), moderator.getUser().getName(),
                        targetId, targetName, reason, duration != null ? duration : 0, 0);

                long unmuteTime = (duration != null) ? System.currentTimeMillis() + (duration * 60 * 1000L) : 0;
                dataService.addMute(guild.getId(), targetId, unmuteTime);

                if (hook != null) hook.editOriginal("✅ User has been muted successfully.").setEmbeds().setComponents().queue();
                
                bot.getLoggingService().logModAction(guild, "User Muted", moderator.getUser(), targetMember.getUser(), reason, durationText, Color.RED);
            }, error -> {
                if (hook != null) hook.editOriginal("❌ Error: Could not mute the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> {
            if (hook != null) hook.editOriginal("❌ Error: Cannot find the user in this server.").setEmbeds().setComponents().queue();
        });
    }
}
