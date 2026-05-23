package TWBot.services;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.events.ButtonEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConfirmationService {
    public static class ConfirmationData {
        public final String command;
        public final String moderatorId;
        public final String guildId;
        public final Map<String, Object> parameters;
        public final long timestamp;
        public boolean isSubmitted = false;
        public long submittedAt = 0;
        public String managerMessageId = null;

        public ConfirmationData(String command, String moderatorId, String guildId, Map<String, Object> parameters) {
            this.command = command;
            this.moderatorId = moderatorId;
            this.guildId = guildId;
            this.parameters = parameters;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Map<String, ConfirmationData> pendingConfirmations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private TWBot bot;

    public ConfirmationService() {
    }

    public void setBot(TWBot bot) {
        this.bot = bot;
        // Clean up and check for auto-approvals every minute
        scheduler.scheduleAtFixedRate(this::checkAutoApprovals, 1, 1, TimeUnit.MINUTES);
    }

    public String addConfirmation(String command, String moderatorId, String guildId, Map<String, Object> parameters) {
        String id = UUID.randomUUID().toString();
        pendingConfirmations.put(id, new ConfirmationData(command, moderatorId, guildId, parameters));
        return id;
    }

    public void markAsSubmitted(String id, String managerMessageId) {
        ConfirmationData data = pendingConfirmations.get(id);
        if (data != null) {
            data.isSubmitted = true;
            data.submittedAt = System.currentTimeMillis();
            data.managerMessageId = managerMessageId;
        }
    }

    private void checkAutoApprovals() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ConfirmationData> entry : pendingConfirmations.entrySet()) {
            String id = entry.getKey();
            ConfirmationData data = entry.getValue();

            // Auto-approve if submitted and 2 hours have passed
            if (data.isSubmitted && data.submittedAt > 0 && (now - data.submittedAt > TimeUnit.HOURS.toMillis(2))) {
                autoApprove(id, data);
            }
            // Cleanup if it's been sitting unsubmitted for 15 minutes
            else if (!data.isSubmitted && (now - data.timestamp > TimeUnit.MINUTES.toMillis(15))) {
                pendingConfirmations.remove(id);
            }
        }
    }

    private void autoApprove(String id, ConfirmationData data) {
        pendingConfirmations.remove(id);
        if (bot != null) {
            bot.getJda().retrieveUserById(data.moderatorId).queue(moderator -> {
                bot.getJda().getGuildById(data.guildId).retrieveMember(moderator).queue(member -> {
                    // We need a way to execute without a ButtonInteractionEvent
                    // For now, let's notify the manager channel it was auto-approved
                    net.dv8tion.jda.api.entities.channel.concrete.TextChannel managerChannel = 
                        bot.getJda().getGuildById(data.guildId).getTextChannelById(BotConfig.MANAGER_CHAT_CHANNEL_ID);
                    
                    if (managerChannel != null && data.managerMessageId != null) {
                        managerChannel.retrieveMessageById(data.managerMessageId).queue(message -> {
                            message.editMessage("✅ **Auto-approved due to 2-hour timeout.**").setEmbeds().setComponents().queue();
                            
                            // Trigger the actual execution logic
                            // This part is tricky because we need to refactor commands to support non-event execution
                            triggerExecution(data, member);
                        }, error -> triggerExecution(data, member));
                    } else {
                        triggerExecution(data, member);
                    }
                });
            });
        }
    }

    public ConfirmationData getConfirmation(String id) {
        return pendingConfirmations.get(id);
    }

    public void removeConfirmation(String id) {
        pendingConfirmations.remove(id);
    }

    private void triggerExecution(ConfirmationData data, net.dv8tion.jda.api.entities.Member moderator) {
        if (bot != null) {
            // Use ButtonEventListener's execution logic but with null event/hook
            ButtonEventListener listener = new ButtonEventListener(bot);
            listener.executeActionNoEvent(moderator.getGuild(), moderator, data);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
