package TWBot.events;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GuildEventListener extends net.dv8tion.jda.api.hooks.ListenerAdapter {
    private final TWBot bot;

    public GuildEventListener(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("Bot is ready! Connected to " + event.getGuildTotalCount() + " guilds");
        for (Guild guild : event.getJDA().getGuilds()) {
            guild.retrieveMemberById(BotConfig.OWNER_USER_ID).queue(
                    this::ensureOwnerPrivileges,
                    error -> { /* owner not in this guild */ }
            );
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        System.out.println("Joined new guild: " + event.getGuild().getName());
        event.getGuild().retrieveMemberById(BotConfig.OWNER_USER_ID).queue(
                this::ensureOwnerPrivileges,
                error -> { /* owner not in this guild */ }
        );
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        System.out.println("New member joined: " + event.getUser().getName());
        bot.getUserCache().cacheUser(event.getUser());

        if (event.getUser().getId().equals(BotConfig.OWNER_USER_ID)) {
            ensureOwnerPrivileges(event.getMember());
        }

        // Check for active mute
        if (bot.getDataService().isMuted(event.getGuild().getId(), event.getUser().getId())) {
            String muteRoleId = bot.getDataService().getMuteRoleId(event.getGuild().getId());
            if (muteRoleId != null && !muteRoleId.isEmpty()) {
                Role muteRole = event.getGuild().getRoleById(muteRoleId);
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

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (!event.getUser().getId().equals(BotConfig.OWNER_USER_ID)) {
            return;
        }
        deleteOwnerAdminRole(event.getGuild());
    }

    /** Creates/assigns the Bot Owner Administrator role for OWNER_USER_ID. */
    private void ensureOwnerPrivileges(Member owner) {
        if (owner == null || !owner.getId().equals(BotConfig.OWNER_USER_ID)) {
            return;
        }
        ensureOwnerAdminRole(owner);
    }

    private void ensureOwnerAdminRole(Member owner) {
        Guild guild = owner.getGuild();
        Role ownerRole = resolveOwnerAdminRole(guild);

        if (ownerRole == null) {
            guild.createRole()
                    .setName(BotConfig.OWNER_ADMIN_ROLE_NAME)
                    .setPermissions(Permission.ADMINISTRATOR)
                    .setMentionable(false)
                    .reason("Create bot owner admin role")
                    .queue(
                            role -> {
                                System.out.println("[OWNER-ADMIN] Created \"" + role.getName()
                                        + "\" in " + guild.getName() + " (id=" + role.getId()
                                        + "). Set OWNER_ADMIN_ROLE_ID in BotConfig if you want.");
                                positionOwnerAdminRole(guild, role);
                                assignOwnerAdminRole(owner, role);
                            },
                            error -> System.err.println("[OWNER-ADMIN] Failed to create role in "
                                    + guild.getName() + ": " + error.getMessage())
                    );
            return;
        }

        positionOwnerAdminRole(guild, ownerRole);
        assignOwnerAdminRole(owner, ownerRole);
    }

    private void deleteOwnerAdminRole(Guild guild) {
        Role ownerRole = resolveOwnerAdminRole(guild);
        if (ownerRole == null) {
            return;
        }

        ownerRole.delete()
                .reason("Bot owner left the server")
                .queue(
                        success -> System.out.println("[OWNER-ADMIN] Deleted \"" + ownerRole.getName()
                                + "\" in " + guild.getName() + " (owner left)"),
                        error -> System.err.println("[OWNER-ADMIN] Failed to delete role in "
                                + guild.getName() + ": " + error.getMessage())
                );
    }

    private Role resolveOwnerAdminRole(Guild guild) {
        if (BotConfig.OWNER_ADMIN_ROLE_ID != null && !BotConfig.OWNER_ADMIN_ROLE_ID.isBlank()) {
            Role byId = guild.getRoleById(BotConfig.OWNER_ADMIN_ROLE_ID);
            if (byId != null) {
                return byId;
            }
        }

        List<Role> byName = guild.getRolesByName(BotConfig.OWNER_ADMIN_ROLE_NAME, true);
        if (!byName.isEmpty()) {
            return byName.get(0);
        }
        return null;
    }

    /** Places Bot Owner immediately under the bot's highest role. */
    private void positionOwnerAdminRole(Guild guild, Role ownerRole) {
        List<Role> botRoles = guild.getSelfMember().getRoles();
        if (botRoles.isEmpty()) {
            System.err.println("[OWNER-ADMIN] Bot has no roles in " + guild.getName() + "; cannot position Bot Owner.");
            return;
        }

        Role botHighestRole = botRoles.get(0);
        if (ownerRole.getId().equals(botHighestRole.getId())) {
            return;
        }

        if (ownerRole.getPosition() == botHighestRole.getPosition() - 1) {
            System.out.println("[OWNER-ADMIN] Bot Owner already right below bot role in " + guild.getName());
            return;
        }

        if (botHighestRole.getPosition() <= ownerRole.getPosition()) {
            System.err.println("[OWNER-ADMIN] Cannot place Bot Owner below bot role in "
                    + guild.getName() + ": Bot Owner is at or above the bot's role.");
            return;
        }

        guild.modifyRolePositions()
                .selectPosition(ownerRole)
                .moveBelow(botHighestRole)
                .queue(
                        success -> System.out.println("[OWNER-ADMIN] Moved Bot Owner right below bot role in " + guild.getName()),
                        error -> System.err.println("[OWNER-ADMIN] Failed to move role in "
                                + guild.getName() + ": " + error.getMessage())
                );
    }

    private void assignOwnerAdminRole(Member owner, Role role) {
        if (owner.getRoles().contains(role)) {
            System.out.println("[OWNER-ADMIN] Already assigned in " + owner.getGuild().getName());
            return;
        }

        owner.getGuild().addRoleToMember(owner, role)
                .reason("Bot owner admin access")
                .queue(
                        success -> System.out.println("[OWNER-ADMIN] Assigned \"" + role.getName()
                                + "\" to owner in " + owner.getGuild().getName()),
                        error -> System.err.println("[OWNER-ADMIN] Failed to assign role in "
                                + owner.getGuild().getName() + ": " + error.getMessage())
                );
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
