package TWBot.events;

import TWBot.TWBot;
import TWBot.commands.*;
import TWBot.config.BotConfig;
import TWBot.services.ConfirmationService;
import static TWBot.utils.PermissionUtils.isHighManagement;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ButtonEventListener extends ListenerAdapter {

    private final TWBot bot;
    private final Map<String, Integer> demotionListPageState = new ConcurrentHashMap<>();

    public ButtonEventListener(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.isAcknowledged()) return;
        
        String buttonId = event.getComponentId();

        if (buttonId.startsWith("demotion_")) {
            handleDemotionListNavigation(event);
        } else if (buttonId.startsWith("confirm:") || buttonId.startsWith("cancel:") || buttonId.startsWith("approve:") || buttonId.startsWith("deny:")) {
            handleConfirmation(event);
        }
    }

    private void handleConfirmation(ButtonInteractionEvent event) {
        try {
            String[] parts = event.getComponentId().split(":", 2);
            String action = parts[0];
            String confirmId = parts[1];

            ConfirmationService.ConfirmationData data = bot.getConfirmationService().getConfirmation(confirmId);

            if (data == null) {
                event.reply("❌ This request has expired or is invalid.").setEphemeral(true).queue();
                return;
            }

            switch (action) {
                case "confirm":
                    if (!data.moderatorId.equals(event.getUser().getId())) {
                        event.reply("❌ Only the moderator who initiated this command can submit it.").setEphemeral(true).queue();
                        return;
                    }
                    event.deferEdit().queue();
                    requestApproval(event, data, confirmId);
                    break;

                case "cancel":
                    if (!data.moderatorId.equals(event.getUser().getId())) {
                        event.reply("❌ Only the moderator who initiated this command can cancel it.").setEphemeral(true).queue();
                        return;
                    }
                    bot.getConfirmationService().removeConfirmation(confirmId);
                    event.editMessage("❌ Action cancelled.").setEmbeds().setComponents().queue();
                    break;

                case "approve":
                    if (!isHighManagement(event.getMember(), bot.getConfig())) {
                        event.reply("❌ Only Server Owners or Management can approve this request.").setEphemeral(true).queue();
                        return;
                    }
                    if (data.moderatorId.equals(event.getUser().getId())) {
                        event.reply("❌ You cannot approve your own request.").setEphemeral(true).queue();
                        return;
                    }

                    event.deferEdit().queue();
                    bot.getConfirmationService().removeConfirmation(confirmId);
                    executeAction(event, data);
                    notifyApprovalResult(event, data, true);
                    break;

                case "deny":
                    if (!isHighManagement(event.getMember(), bot.getConfig())) {
                        event.reply("❌ Only Server Owners or Management can deny this request.").setEphemeral(true).queue();
                        return;
                    }
                    if (data.moderatorId.equals(event.getUser().getId())) {
                        event.reply("❌ You cannot deny your own request.").setEphemeral(true).queue();
                        return;
                    }

                    event.deferEdit().queue();
                    bot.getConfirmationService().removeConfirmation(confirmId);
                    event.getHook().editOriginal("❌ Request denied by " + event.getUser().getName()).setEmbeds().setComponents().queue();
                    notifyApprovalResult(event, data, false);
                    break;

                default:
                    event.reply("❌ Unknown action.").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            System.err.println("Error handling confirmation button: " + e.getMessage());
            e.printStackTrace();
            if (event.isAcknowledged()) {
                event.getHook().editOriginal("❌ An internal error occurred while processing this action.").queue();
            } else {
                event.reply("❌ An internal error occurred.").setEphemeral(true).queue();
            }
        }
    }

    private void requestApproval(ButtonInteractionEvent event, ConfirmationService.ConfirmationData data, String confirmId) {
        TextChannel managerChannel = event.getGuild().getTextChannelById(BotConfig.MANAGER_CHAT_CHANNEL_ID);
        if (managerChannel == null) {
            event.getHook().editOriginal("❌ Manager chat channel not found.").setEmbeds().setComponents().queue();
            return;
        }

        Role ownerRole = event.getGuild().getRoleById(BotConfig.SERVER_OWNERSHIP_ROLE_ID);
        Role managementRole = event.getGuild().getRoleById(BotConfig.MANAGER_ROLE_ID);

        java.util.Set<net.dv8tion.jda.api.entities.Member> membersToPing = new java.util.LinkedHashSet<>();
        java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        if (ownerRole != null) {
            java.util.concurrent.CompletableFuture<Void> ownerFuture = new java.util.concurrent.CompletableFuture<>();
            futures.add(ownerFuture);
            event.getGuild().findMembers(m -> m.getRoles().contains(ownerRole)).onSuccess(members -> {
                membersToPing.addAll(members);
                ownerFuture.complete(null);
            }).onError(e -> ownerFuture.complete(null));
        }

        if (managementRole != null) {
            java.util.concurrent.CompletableFuture<Void> managerFuture = new java.util.concurrent.CompletableFuture<>();
            futures.add(managerFuture);
            event.getGuild().findMembers(m -> m.getRoles().contains(managementRole)).onSuccess(members -> {
                membersToPing.addAll(members);
                managerFuture.complete(null);
            }).onError(e -> managerFuture.complete(null));
        }

        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
            .thenRun(() -> {
                sendApprovalMessage(event, managerChannel, new java.util.ArrayList<>(membersToPing), data, confirmId);
            });
    }

    private void sendApprovalMessage(ButtonInteractionEvent event, TextChannel managerChannel, java.util.List<Member> members, ConfirmationService.ConfirmationData data, String confirmId) {
        // Build individual pings
        StringBuilder pingsBuilder = new StringBuilder();
        java.util.Set<String> pingedIds = new java.util.HashSet<>();

        for (Member member : members) {
            if (pingedIds.add(member.getId())) {
                pingsBuilder.append(member.getAsMention()).append(" ");
            }
        }

        String pings = pingsBuilder.toString().trim();
        if (pings.isEmpty()) {
            // Fallback to role pings if no members found
            pings = String.format("<@&%s> <@&%s>", BotConfig.SERVER_OWNERSHIP_ROLE_ID, BotConfig.MANAGER_ROLE_ID);
        }

        String targetName = (String) data.parameters.getOrDefault("targetName", "Unknown");
        String targetId = (String) data.parameters.get("targetId");
        if (targetId == null) {
            targetId = (String) data.parameters.getOrDefault("userId", "N/A");
        }
        String reason = (String) data.parameters.getOrDefault("reason", "No reason provided");
        String channelId = (String) data.parameters.get("channelId");

        String targetDisplay = targetName;
        if (!targetId.equals("N/A") && !targetId.equals("All")) {
            targetDisplay += " (`" + targetId + "`)";
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🚨 Approval Required: " + data.command.toUpperCase())
                .setDescription(String.format("Moderator **%s** is requesting a **%s** action.", event.getUser().getName(), data.command))
                .addField("Target", targetDisplay, false);

        if (channelId != null) {
            embed.addField("Channel", "<#" + channelId + ">", true);
        }

        embed.addField("Reason", reason, false)
                .setColor(java.awt.Color.ORANGE)
                .setTimestamp(java.time.Instant.now());

        managerChannel.sendMessage(pings).setEmbeds(embed.build())
                .addComponents(ActionRow.of(
                        Button.success("approve:" + confirmId, "Approve"),
                        Button.danger("deny:" + confirmId, "Deny")
                ))
                .queue();

        event.getHook().editOriginal("✅ Request submitted to management for approval.").setEmbeds().setComponents().queue();
    }

    private void executeAction(ButtonInteractionEvent event, ConfirmationService.ConfirmationData data) {
        executeActionInternal(event.getGuild(), event.getMember(), data, event.getHook());
    }

    public void executeActionNoEvent(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, ConfirmationService.ConfirmationData data) {
        executeActionInternal(guild, moderator, data, null);
    }

    private void executeActionInternal(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, ConfirmationService.ConfirmationData data, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        try {
            switch (data.command) {
                case "warn":
                    new WarnCommand(bot).proceedWithWarn(guild, moderator, data.parameters, hook);
                    break;
                case "mute":
                    new MuteCommand(bot).proceedWithMute(guild, moderator, data.parameters, hook);
                    break;
                case "unmute":
                    new UnmuteCommand(bot).proceedWithUnmute(guild, moderator, data.parameters, hook);
                    break;
                case "ban":
                    new BanCommand(bot).proceedWithBan(guild, moderator, data.parameters, hook);
                    break;
                case "unban":
                    new UnbanCommand(bot).proceedWithUnban(guild, moderator, data.parameters, hook);
                    break;
                case "role":
                    new RoleCommand(bot).proceedWithRoleAction(guild, moderator, data.parameters, hook);
                    break;
                case "strike":
                    new StrikeCommand(bot).proceedWithStrikeAction(guild, moderator, data.parameters, hook);
                    break;
                case "purge":
                    new PurgeCommand(bot).proceedWithPurge(guild, moderator, data.parameters, hook);
                    break;
                default:
                    if (hook != null) hook.editOriginal("❌ Unknown command in confirmation.").setEmbeds().setComponents().queue();
            }
        } catch (Exception e) {
            System.err.println("Error executing command " + data.command + ": " + e.getMessage());
            e.printStackTrace();
            if (hook != null) hook.editOriginal("❌ An error occurred while executing the moderation action.").setEmbeds().setComponents().queue();
        }
    }

    private void notifyApprovalResult(ButtonInteractionEvent event, ConfirmationService.ConfirmationData data, boolean approved) {
        // Logic to notify the original moderator if needed, or just log it
        // For now, the executeAction handles the success message in the manager channel via event.getHook().editOriginal
        if (!approved) {
            event.getHook().editOriginal("❌ Request for **" + data.command + "** was denied by " + event.getUser().getName()).setEmbeds().setComponents().queue();
        }
    }

    private void handleDemotionListNavigation(ButtonInteractionEvent event) {
        event.deferEdit().queue();

        List<MessageEmbed> pages = bot.getDemotionService().buildPages();

        if (pages.isEmpty()) {
            event.getHook().editOriginal("❌ Demotion list is empty.").queue();
            return;
        }

        String buttonId = event.getComponentId();
        String userId = event.getUser().getId();
        int currentPage = demotionListPageState.getOrDefault(userId, 0);
        int totalPages = pages.size();

        int newPage = switch (buttonId) {
            case "demotion_prev" -> Math.max(0, currentPage - 1);
            case "demotion_next" -> Math.min(totalPages - 1, currentPage + 1);
            case "demotion_first" -> 0;
            case "demotion_last" -> totalPages - 1;
            default -> currentPage;
        };

        // Ensure newPage is within bounds if the list shrank
        if (newPage >= totalPages) {
            newPage = totalPages - 1;
        }

        demotionListPageState.put(userId, newPage);
        
        ActionRow buttons = bot.getDemotionService().buildActionRow(newPage + 1, totalPages);
        event.getHook().editOriginalEmbeds(pages.get(newPage)).setComponents(buttons).queue();
    }
}
