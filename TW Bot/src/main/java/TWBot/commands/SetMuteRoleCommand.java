package TWBot.commands;

import TWBot.TWBot;
import TWBot.services.DataService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class SetMuteRoleCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public SetMuteRoleCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = new DataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("setmuterole", "Set the role used for muting users")
                .addOption(OptionType.ROLE, "role", "The role to use for muting", true));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isAdmin(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        Role muteRole = event.getOption("role").getAsRole();
        if (muteRole.isManaged()) {
            event.getHook().sendMessage("❌ Cannot use managed roles for muting.").queue();
            return;
        }

        Guild guild = event.getGuild();
        dataService.setMuteRoleId(guild.getId(), muteRole.getId());

        for (TextChannel channel : guild.getTextChannels()) {
            channel.getPermissionContainer().upsertPermissionOverride(muteRole)
                    .deny(Permission.MESSAGE_SEND,
                            Permission.MESSAGE_SEND_IN_THREADS,
                            Permission.CREATE_PUBLIC_THREADS,
                            Permission.CREATE_PRIVATE_THREADS,
                            Permission.MESSAGE_ADD_REACTION)
                    .queue(null, e -> {});
        }

        for (VoiceChannel channel : guild.getVoiceChannels()) {
            channel.getPermissionContainer().upsertPermissionOverride(muteRole)
                    .deny(Permission.VOICE_SPEAK)
                    .queue(null, e -> {});
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔇 Mute Role Set")
                .setDescription(String.format("Role **%s** will now be used for muting users.", muteRole.getName()))
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}
