package TWBot.events;

import TWBot.TWBot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import org.jetbrains.annotations.NotNull;

public class GuildEventListener extends net.dv8tion.jda.api.hooks.ListenerAdapter {
    private final TWBot bot;

    public GuildEventListener(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("Bot is ready! Connected to " + event.getGuildTotalCount() + " guilds");
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        System.out.println("New member joined: " + event.getUser().getName());
        bot.getUserCache().cacheUser(event.getUser());

        // Check for active mute
        if (bot.getDataService().isMuted(event.getGuild().getId(), event.getUser().getId())) {
            String muteRoleId = bot.getDataService().getMuteRoleId(event.getGuild().getId());
            if (muteRoleId != null && !muteRoleId.isEmpty()) {
                net.dv8tion.jda.api.entities.Role muteRole = event.getGuild().getRoleById(muteRoleId);
                if (muteRole != null) {
                    event.getGuild().addRoleToMember(event.getMember(), muteRole)
                        .reason("Mute-on-rejoin: User has an active mute in the database.")
                        .queue(
                            success -> System.out.println("Re-applied mute to " + event.getUser().getName()),
                            error -> System.err.println("Failed to re-apply mute to " + event.getUser().getName() + ": " + error.getMessage())
                        );
                }
            }
        }

        sendWelcomeMessage(event.getGuild(), event.getMember());
    }

    private void sendWelcomeMessage(Guild guild, Member member) {
        String welcomeChannelId = bot.getConfig().getWelcomeChannelId();
        TextChannel welcomeChannel = guild.getTextChannelById(welcomeChannelId);

        if (welcomeChannel != null) {
            String welcomeMessage = "Welcome to the server, " + member.getAsMention() + " ! 👋";
            
            welcomeChannel.sendMessage(welcomeMessage).queue(message -> {
                message.addReaction(Emoji.fromCustom("pepehello_tw", 1466258806492237938L, false)).queue();
            });
        } else {
            System.out.println("Welcome channel not found with ID: " + welcomeChannelId);
        }
    }
}
