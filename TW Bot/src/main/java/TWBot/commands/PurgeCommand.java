package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.services.DataService;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PurgeCommand implements Command {
    private final TWBot bot;
    private final DataService dataService;

    public PurgeCommand(TWBot bot) {
        this.bot = bot;
        this.dataService = bot.getDataService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("purge", "Delete up to 1000 messages")
                .addOption(OptionType.INTEGER, "amount", "The number of messages to delete (1-1000)", true)
                .addOption(OptionType.USER, "user", "Only delete messages from this user", false));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int amount = event.getOption("amount").getAsInt();
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : null;

        if (amount < 1 || amount > 1000) {
            event.reply("❌ Please provide a number between 1 and 1000.").setEphemeral(true).queue();
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("amount", amount);
        params.put("reason", "Purge " + amount + " messages");
        params.put("channelId", event.getChannel().getId());
        params.put("moderatorName", event.getUser().getName());
        params.put("moderatorId", event.getUser().getId());
        if (targetUser != null) {
            params.put("targetId", targetUser.getId());
            params.put("targetName", targetUser.getName());
            params.put("targetMention", targetUser.getAsMention());
        } else {
            params.put("targetId", "All");
            params.put("targetName", "All Messages");
        }

        // Execute directly without approval
        event.deferReply().setEphemeral(true).queue();
        
        if (targetUser != null) {
            event.getGuild().retrieveMemberById(targetUser.getId()).queue(targetMember -> {
                if (!PermissionUtils.canModerate(event.getMember(), targetMember) && !event.getUser().getId().equals(targetUser.getId())) {
                    event.getHook().editOriginal("❌ You cannot purge messages from this user due to role hierarchy.").queue();
                    return;
                }
                doPurgeManual(event, amount, targetUser);
            }, error -> doPurgeManual(event, amount, targetUser));
        } else {
            doPurgeManual(event, amount, null);
        }
    }

    private void doPurgeManual(SlashCommandInteractionEvent event, int amount, User targetUser) {
        TextChannel channel = event.getChannel().asTextChannel();
        if (targetUser != null) {
            List<Message> userMessages = new ArrayList<>();
            searchUserMessagesBatchManual(channel, event, targetUser, amount, userMessages, 0);
        } else {
            channel.getHistory().retrievePast(Math.min(amount, 100)).queue(messages -> {
                if (messages.isEmpty()) {
                    event.getHook().editOriginal("❌ No messages found to delete.").setEmbeds().setComponents().queue();
                    return;
                }

                bulkDeleteMessages(channel, messages, () -> {
                    try {
                        dataService.saveModAction(ModAction.ActionType.PURGE, event.getUser().getId(), event.getUser().getName(),
                                "channel", channel.getName(), "Purged " + messages.size() + " messages", 0, messages.size());

                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("Messages Purged")
                                .setDescription("Purged " + messages.size() + " messages from " + channel.getAsMention())
                                .addField("Channel", channel.getName(), false)
                                .addField("Messages Deleted", String.valueOf(messages.size()), false)
                                .addField("Moderator", event.getUser().getName(), false)
                                .setColor(new Color(255, 165, 0))
                                .setTimestamp(Instant.now());
                        
                        bot.getLoggingService().logAction(event.getGuild(), "moderation-logs", embed.build());

                        event.getHook().editOriginal("✅ Successfully deleted " + messages.size() + " messages.").setEmbeds().setComponents().queue();
                    } catch (Exception e) {
                        e.printStackTrace();
                        event.getHook().editOriginal("❌ Error: " + e.getMessage()).setEmbeds().setComponents().queue();
                    }
                });
            }, error -> {
                error.printStackTrace();
                event.getHook().editOriginal("❌ Failed to retrieve messages: " + error.getMessage()).setEmbeds().setComponents().queue();
            });
        }
    }

    private void searchUserMessagesBatchManual(TextChannel channel, SlashCommandInteractionEvent event, 
                                               User targetUser, int targetAmount,
                                               List<Message> userMessages, int searches) {
        if (userMessages.size() >= targetAmount || searches >= 10) {
            List<Message> toDelete = userMessages.stream().limit(targetAmount).collect(Collectors.toList());

            if (toDelete.isEmpty()) {
                event.getHook().editOriginal("❌ No messages found from that user to delete.").setEmbeds().setComponents().queue();
                return;
            }

            bulkDeleteMessages(channel, toDelete, () -> {
                try {
                    dataService.saveModAction(ModAction.ActionType.PURGE, event.getUser().getId(), event.getUser().getName(),
                            targetUser.getId(), targetUser.getName(), "Purged " + toDelete.size() + " messages", 0, toDelete.size());

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("Messages Purged")
                            .setDescription("Purged " + toDelete.size() + " messages from " + targetUser.getAsMention())
                            .addField("Channel", event.getChannel().getAsMention(), false)
                            .addField("Target User", targetUser.getName(), false)
                            .addField("Messages Deleted", String.valueOf(toDelete.size()), false)
                            .addField("Moderator", event.getUser().getName(), false)
                            .setColor(new Color(255, 165, 0))
                            .setTimestamp(Instant.now());
                    
                    bot.getLoggingService().logAction(event.getGuild(), "moderation-logs", embed.build());

                    event.getHook().editOriginal("✅ Successfully deleted " + toDelete.size() + " messages from " + targetUser.getName() + ".").setEmbeds().setComponents().queue();
                } catch (Exception e) {
                    e.printStackTrace();
                    event.getHook().editOriginal("❌ Error: " + e.getMessage()).setEmbeds().setComponents().queue();
                }
            });
            return;
        }

        channel.getHistory().retrievePast(100).queue(messages -> {
            try {
                List<Message> userBatch = messages.stream()
                        .filter(message -> message.getAuthor().getId().equals(targetUser.getId()))
                        .collect(Collectors.toList());

                userMessages.addAll(userBatch);
                searchUserMessagesBatchManual(channel, event, targetUser, targetAmount, userMessages, searches + 1);
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().editOriginal("❌ Error while searching messages: " + e.getMessage()).setEmbeds().setComponents().queue();
            }
        }, error -> {
            error.printStackTrace();
            event.getHook().editOriginal("❌ Failed to retrieve message history: " + error.getMessage()).setEmbeds().setComponents().queue();
        });
    }

    public void proceedWithPurge(net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.Member moderator, Map<String, Object> params, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        int amount = (int) params.get("amount");
        String targetId = (String) params.get("targetId");
        String targetName = (String) params.get("targetName");
        String targetMention = (String) params.get("targetMention");
        String channelId = (String) params.get("channelId");
        String moderatorName = (String) params.get("moderatorName");
        String moderatorId = (String) params.get("moderatorId");

        TextChannel channel = channelId != null ? guild.getTextChannelById(channelId) : null;
        if (channel == null) {
            if (hook != null) hook.editOriginal("❌ Original channel not found.").setEmbeds().setComponents().queue();
            return;
        }

        if (targetId != null && !targetId.equals("All")) {
            guild.retrieveMemberById(targetId).queue(targetMember -> {
                if (!PermissionUtils.canModerate(moderator, targetMember) && !moderator.getId().equals(targetId)) {
                    if (hook != null) hook.editOriginal("❌ You cannot purge messages from this user due to role hierarchy.").setEmbeds().setComponents().queue();
                    return;
                }
                doSearchAndDelete(channel, targetId, targetName, targetMention, amount, moderatorName, moderatorId, hook, guild);
            }, error -> doSearchAndDelete(channel, targetId, targetName, targetMention, amount, moderatorName, moderatorId, hook, guild));
        } else {
            channel.getHistory().retrievePast(Math.min(amount, 100)).queue(messages -> {
                if (messages.isEmpty()) {
                    if (hook != null) hook.editOriginal("❌ No messages found to delete.").setEmbeds().setComponents().queue();
                    return;
                }

                bulkDeleteMessages(channel, messages, () -> {
                    try {
                        dataService.saveModAction(ModAction.ActionType.PURGE, moderatorId, moderatorName,
                                "channel", channel.getName(), "Purged " + messages.size() + " messages", 0, messages.size());

                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("Messages Purged")
                                .setDescription("Purged " + messages.size() + " messages from " + channel.getAsMention())
                                .addField("Channel", channel.getName(), false)
                                .addField("Messages Deleted", String.valueOf(messages.size()), false)
                                .addField("Moderator", moderatorName, false)
                                .setColor(new Color(255, 165, 0))
                                .setTimestamp(Instant.now());
                        
                        bot.getLoggingService().logAction(guild, "moderation-logs", embed.build());

                        if (hook != null) hook.editOriginal("✅ Successfully deleted " + messages.size() + " messages in " + channel.getAsMention() + ".").setEmbeds().setComponents().queue();
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (hook != null) hook.editOriginal("❌ Error: " + e.getMessage()).setEmbeds().setComponents().queue();
                    }
                });
            });
        }
    }

    private void doSearchAndDelete(TextChannel channel, String targetId, String targetName, String targetMention, int targetAmount,
                                   String moderatorName, String moderatorId, net.dv8tion.jda.api.interactions.InteractionHook hook, net.dv8tion.jda.api.entities.Guild guild) {
        List<Message> userMessages = new ArrayList<>();
        searchUserMessagesBatchButton(channel, targetId, targetName, targetMention, targetAmount, userMessages, 0, moderatorName, moderatorId, hook, guild);
    }

    private void searchUserMessagesBatchButton(TextChannel channel, String targetId, String targetName, String targetMention, int targetAmount,
                                               List<Message> userMessages, int searches, String moderatorName, String moderatorId, net.dv8tion.jda.api.interactions.InteractionHook hook, net.dv8tion.jda.api.entities.Guild guild) {
        if (userMessages.size() >= targetAmount || searches >= 10) {
            List<Message> toDelete = userMessages.stream().limit(targetAmount).collect(Collectors.toList());

            if (toDelete.isEmpty()) {
                if (hook != null) hook.editOriginal("❌ No messages found from that user to delete.").setEmbeds().setComponents().queue();
                return;
            }

            bulkDeleteMessages(channel, toDelete, () -> {
                try {
                    dataService.saveModAction(ModAction.ActionType.PURGE, moderatorId, moderatorName,
                            targetId, targetName, "Purged " + toDelete.size() + " messages", 0, toDelete.size());

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("Messages Purged")
                            .setDescription("Purged " + toDelete.size() + " messages from " + targetName)
                            .addField("Channel", channel.getAsMention(), false)
                            .addField("Target User", targetName, false)
                            .addField("Messages Deleted", String.valueOf(toDelete.size()), false)
                            .addField("Moderator", moderatorName, false)
                            .setColor(new Color(255, 165, 0))
                            .setTimestamp(Instant.now());
                    
                    bot.getLoggingService().logAction(guild, "moderation-logs", embed.build());

                    if (hook != null) hook.editOriginal("✅ Successfully deleted " + toDelete.size() + " messages from " + targetName + " in " + channel.getAsMention() + ".").setEmbeds().setComponents().queue();
                } catch (Exception e) {
                    e.printStackTrace();
                    if (hook != null) hook.editOriginal("❌ Error: " + e.getMessage()).setEmbeds().setComponents().queue();
                }
            });
            return;
        }

        channel.getHistory().retrievePast(100).queue(messages -> {
            List<Message> userBatch = messages.stream()
                    .filter(message -> message.getAuthor().getId().equals(targetId))
                    .collect(Collectors.toList());

            userMessages.addAll(userBatch);
            searchUserMessagesBatchButton(channel, targetId, targetName, targetMention, targetAmount, userMessages, searches + 1, moderatorName, moderatorId, hook, guild);
        });
    }

    private void bulkDeleteMessages(TextChannel channel, List<Message> messages, Runnable onComplete) {
        if (messages.isEmpty()) {
            onComplete.run();
            return;
        }

        List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += 100) {
            int endIndex = Math.min(i + 100, messages.size());
            List<Message> batch = messages.subList(i, endIndex);
            
            CompletableFuture<Void> future = new CompletableFuture<>();
            deleteFutures.add(future);
            
            if (batch.size() == 1) {
                batch.get(0).delete().queue(s -> future.complete(null), e -> future.complete(null));
            } else {
                channel.deleteMessages(batch).queue(s -> future.complete(null), e -> future.complete(null));
            }
        }
        
        CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0])).thenRun(onComplete);
    }
}
