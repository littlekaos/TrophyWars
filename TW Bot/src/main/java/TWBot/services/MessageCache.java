package TWBot.services;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MessageCache {

    private final Map<String, String> messageCache = new ConcurrentHashMap<>(1000);
    private final Map<String, String> userCache = new ConcurrentHashMap<>(1000);
    private final Map<String, Boolean> botFlags = new ConcurrentHashMap<>(1000);

    /** Full content cache for human messages. */
    public void cacheMessage(Message message, String authorId) {
        if (message == null) return;

        String attachments = message.getAttachments().stream()
                .map(Attachment::getUrl)
                .collect(Collectors.joining("\n"));

        String content = message.getContentDisplay() +
                (attachments.isEmpty() ? "" : "\nAttachments:\n" + attachments);

        messageCache.put(message.getId(), content);
        userCache.put(message.getId(), authorId);
        botFlags.put(message.getId(), false);
    }

    /** Lightweight tracking so bot deletes can be skipped without storing content. */
    public void trackAuthor(String messageId, String authorId, boolean isBot) {
        if (messageId == null) return;
        if (authorId != null) userCache.put(messageId, authorId);
        botFlags.put(messageId, isBot);
    }

    public String getMessageContent(String messageId) {
        return messageCache.getOrDefault(messageId, "Unknown content");
    }

    public String getMessageAuthorId(String messageId) {
        return userCache.get(messageId);
    }

    public boolean isBotMessage(String messageId) {
        return Boolean.TRUE.equals(botFlags.get(messageId));
    }

    public boolean hasAuthor(String messageId) {
        return userCache.containsKey(messageId);
    }

    public void removeMessage(String messageId) {
        messageCache.remove(messageId);
        userCache.remove(messageId);
        botFlags.remove(messageId);
    }

    public void removeMessages(Iterable<String> messageIds) {
        for (String id : messageIds) {
            removeMessage(id);
        }
    }

    public int getMessageCacheSize() {
        return messageCache.size();
    }

    public int getUserCacheSize() {
        return userCache.size();
    }
}
