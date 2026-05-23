package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.services.DemotionService;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DemotionCommand implements Command {

    private final TWBot bot;
    private final DemotionService demotionService;

    public DemotionCommand(TWBot bot) {
        this.bot = bot;
        this.demotionService = bot.getDemotionService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("initdemotionlist", "Initialize the demotion list message (Admin Only)"),
                Commands.slash("adddemotion", "Add a single user to demotion list (Admin Only)")
                        .addOption(OptionType.USER, "user", "User to add to demotion list", true)
                        .addOption(OptionType.STRING, "type", "temp or perm", true),
                Commands.slash("bulkadddemotions", "Bulk add users to demotion list (Admin Only)")
                        .addOption(OptionType.STRING, "userids", "Comma-separated user IDs", true)
                        .addOption(OptionType.STRING, "type", "temp or perm", true),
                Commands.slash("removedemotion", "Remove a user from the demotion list (Admin Only)")
                        .addOption(OptionType.USER, "user", "User to remove from demotion list", true),
                Commands.slash("bulkremovedemotion", "Bulk remove users from demotion list (Admin Only)")
                        .addOption(OptionType.STRING, "userids", "Comma-separated user IDs", true),
                Commands.slash("updatedemotionlist", "Manually update the demotion list message (Admin Only)"),
                Commands.slash("viewdemotionroles", "View stored roles for a demoted user (Admin Only)")
                        .addOption(OptionType.USER, "user", "The user to check", true)
        );
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isAdmin(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        switch (commandName) {
            case "initdemotionlist" -> handleInit(event);
            case "adddemotion" -> handleAdd(event);
            case "bulkadddemotions" -> handleBulkAdd(event);
            case "removedemotion" -> handleRemove(event);
            case "bulkremovedemotion" -> handleBulkRemove(event);
            case "updatedemotionlist" -> handleUpdate(event);
            case "viewdemotionroles" -> handleViewRoles(event);
        }
    }

    private void handleInit(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        TextChannel channel = event.getChannel().asTextChannel();
        
        List<MessageEmbed> pages = demotionService.buildPages();
        if (pages.isEmpty()) {
            event.getHook().editOriginal("❌ Cannot initialize: No demoted users found.").queue();
            return;
        }

        channel.sendMessageEmbeds(pages.get(0))
                .setComponents(demotionService.buildActionRow(1, pages.size()))
                .queue(message -> {
                    bot.getStrikeService().getDatabase().setDemotionListMessageId(message.getId());
                    event.getHook().editOriginal("✅ Demotion list initialized! Message ID: " + message.getId()).queue();
                });
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        String type = Objects.requireNonNull(event.getOption("type")).getAsString().toLowerCase();

        if (type.equals("temp")) {
            event.getGuild().retrieveMember(user).queue(member -> {
                if (!PermissionUtils.canModerate(event.getMember(), member)) {
                    event.getHook().editOriginal("❌ You cannot demote this user due to role hierarchy.").queue();
                    return;
                }

                List<String> removedRoles = removeStaffRoles(member);
                LocalDateTime restoreDate = LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(BotConfig.TEMP_DEMOTION_DAYS);
                demotionService.addTemporaryDemotion(user.getId(), removedRoles, restoreDate);
                demotionService.updateDemotionListMessage(event.getJDA());

                event.getHook().editOriginal("✅ Added " + user.getAsMention() + " to temporary demotions and removed their staff roles.").queue();
            }, error -> {
                // If member not in guild, still allow temp demotion tracking
                demotionService.addTemporaryDemotion(user.getId(), new ArrayList<>(), LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(BotConfig.TEMP_DEMOTION_DAYS));
                demotionService.updateDemotionListMessage(event.getJDA());
                event.getHook().editOriginal("✅ Added " + user.getAsMention() + " (not in server) to temporary demotions.").queue();
            });
        } else if (type.equals("perm")) {
            event.getGuild().retrieveMember(user).queue(member -> {
                if (!PermissionUtils.canModerate(event.getMember(), member)) {
                    event.getHook().editOriginal("❌ You cannot demote this user due to role hierarchy.").queue();
                    return;
                }
                
                removeStaffRoles(member);
                demotionService.addPermanentDemotion(user.getId());
                demotionService.updateDemotionListMessage(event.getJDA());
                event.getHook().editOriginal("✅ Added " + user.getAsMention() + " to permanent demotions and removed their staff roles.").queue();
            }, error -> {
                // If member not in guild, still allow perm demotion
                demotionService.addPermanentDemotion(user.getId());
                demotionService.updateDemotionListMessage(event.getJDA());
                event.getHook().editOriginal("✅ Added " + user.getAsMention() + " to permanent demotions.").queue();
            });
        } else {
            event.getHook().editOriginal("❌ Invalid type. Use 'temp' or 'perm'.").queue();
        }
    }

    private List<String> removeStaffRoles(net.dv8tion.jda.api.entities.Member member) {
        List<String> removedRoleIds = new ArrayList<>();
        for (Role role : member.getRoles()) {
            if (BotConfig.isStaffOrModRole(role.getId()) 
                    || BotConfig.isSupportRole(role.getId())) {
                removedRoleIds.add(role.getId());
                member.getGuild().removeRoleFromMember(member, role).queue();
            }
        }
        return removedRoleIds;
    }

    private void handleBulkAdd(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String userIds = Objects.requireNonNull(event.getOption("userids")).getAsString();
        String type = Objects.requireNonNull(event.getOption("type")).getAsString().toLowerCase();

        if (!type.equals("temp") && !type.equals("perm")) {
            event.getHook().editOriginal("❌ Invalid type. Use 'temp' or 'perm'.").queue();
            return;
        }

        String[] ids = userIds.split(",");
        for (String id : ids) {
            id = id.trim();
            final String finalId = id;
            if (type.equals("temp")) {
                LocalDateTime restoreDate = LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(BotConfig.TEMP_DEMOTION_DAYS);
                
                event.getGuild().retrieveMemberById(finalId).queue(
                    member -> {
                        List<String> removedRoles = removeStaffRoles(member);
                        demotionService.addTemporaryDemotion(finalId, removedRoles, restoreDate);
                    },
                    error -> demotionService.addTemporaryDemotion(finalId, new ArrayList<>(), restoreDate)
                );
            } else {
                event.getGuild().retrieveMemberById(finalId).queue(
                    this::removeStaffRoles,
                    error -> {} // Ignore if not in guild
                );
                demotionService.addPermanentDemotion(finalId);
            }
        }
        demotionService.updateDemotionListMessage(event.getJDA());
        event.getHook().editOriginal("✅ Bulk added " + ids.length + " users to " + type + " demotions.").queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        demotionService.removeDemotion(user.getId(), event.getJDA());
        event.getHook().editOriginal("✅ Removed " + user.getAsMention() + " from demotions.").queue();
    }

    private void handleBulkRemove(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String userIds = Objects.requireNonNull(event.getOption("userids")).getAsString();
        String[] ids = userIds.split(",");
        for (String id : ids) {
            id = id.trim();
            demotionService.removeDemotion(id, event.getJDA());
        }
        event.getHook().editOriginal("✅ Bulk removed " + ids.length + " users from demotions.").queue();
    }

    private void handleUpdate(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        demotionService.updateDemotionListMessage(event.getJDA());
        event.getHook().editOriginal("✅ Demotion list update triggered.").queue();
    }

    private void handleViewRoles(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        
        List<String> roleIds = bot.getStrikeService().getDatabase().getTemporaryDemotionRoles(user.getId());
        
        if (roleIds.isEmpty()) {
            event.getHook().editOriginal("❌ No stored roles found for " + user.getAsMention() + " in the demotion database.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 **Stored Roles for ").append(user.getName()).append(":**\n");
        for (String roleId : roleIds) {
            Role role = event.getGuild().getRoleById(roleId);
            if (role != null) {
                sb.append("- ").append(role.getAsMention()).append(" (`").append(roleId).append("`)\n");
            } else {
                sb.append("- Unknown Role (`").append(roleId).append("`)\n");
            }
        }

        event.getHook().editOriginal(sb.toString()).queue();
    }
}
