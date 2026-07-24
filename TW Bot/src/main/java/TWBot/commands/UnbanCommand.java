package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
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

public class UnbanCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public UnbanCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("unban", "Unban a user via their Discord ID")
                .addOption(OptionType.STRING, "user_id", "The ID of the user to unban", true)
                .addOption(OptionType.STRING, "reason", "The reason for the unban", true));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getOption("user_id").getAsString();
        String reason = event.getOption("reason").getAsString();

        if (!userId.matches("\\d{17,19}")) {
            event.reply("❌ Invalid user ID format.").setEphemeral(true).queue();
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("targetId", userId);
        params.put("targetName", "User ID " + userId);
        params.put("reason", reason);

        // Execute directly without approval
        event.deferReply().setEphemeral(true).queue();
        doUnbanManual(event, userId, reason);
    }

    private void doUnbanManual(SlashCommandInteractionEvent event, String userId, String reason) {
        Guild guild = event.getGuild();

        guild.retrieveBanList().queue(banList -> {
            Guild.Ban ban = banList.stream()
                    .filter(b -> b.getUser().getId().equals(userId))
                    .findFirst()
                    .orElse(null);

            if (ban == null) {
                event.getHook().editOriginal("❌ User with ID `" + userId + "` is not banned from this server.").setEmbeds().setComponents().queue();
                return;
            }

            User bannedUser = ban.getUser();
            guild.unban(bannedUser).reason(reason).queue(success -> {
                dataService.saveModAction(ModAction.ActionType.UNBAN, event.getUser().getId(), event.getUser().getName(),
                        userId, bannedUser.getName(), reason, 0, 0);

                event.getHook().editOriginal("✅ User **" + bannedUser.getName() + "** has been unbanned successfully.").setEmbeds().setComponents().queue();
                
                bot.getLoggingService().logModAction(guild, "User Unbanned", event.getUser(), bannedUser, reason, null, Color.GREEN);
            }, error -> {
                event.getHook().editOriginal("❌ Error: Could not unban the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> {
            event.getHook().editOriginal("❌ Error: Could not retrieve ban list. " + error.getMessage()).setEmbeds().setComponents().queue();
        });
    }

    public void proceedWithUnban(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, Map<String, Object> params, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        String userId = (String) params.get("userId");
        String reason = (String) params.get("reason");

        guild.retrieveBanList().queue(banList -> {
            Guild.Ban ban = banList.stream()
                    .filter(b -> b.getUser().getId().equals(userId))
                    .findFirst()
                    .orElse(null);

            if (ban == null) {
                if (hook != null) hook.editOriginal("❌ User with ID `" + userId + "` is not banned from this server.").setEmbeds().setComponents().queue();
                return;
            }

            User bannedUser = ban.getUser();
            guild.unban(bannedUser).reason(reason).queue(success -> {
                dataService.saveModAction(ModAction.ActionType.UNBAN, moderator.getUser().getId(), moderator.getUser().getName(),
                        userId, bannedUser.getName(), reason, 0, 0);

                if (hook != null) hook.editOriginal("✅ User **" + bannedUser.getName() + "** has been unbanned successfully.").setEmbeds().setComponents().queue();
                
                bot.getLoggingService().logModAction(guild, "User Unbanned", moderator.getUser(), bannedUser, reason, null, Color.GREEN);
            }, error -> {
                if (hook != null) hook.editOriginal("❌ Error: Could not unban the user. " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }, error -> {
            if (hook != null) hook.editOriginal("❌ Error: Could not retrieve ban list. " + error.getMessage()).setEmbeds().setComponents().queue();
        });
    }
}
