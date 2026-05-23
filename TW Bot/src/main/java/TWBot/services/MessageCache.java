package TWBot.services;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MessageCache {

    private final Map<String, String> messageCache = new ConcurrentHashMap<>(1000);
    private final Map<String, String> userCache = new ConcurrentHashMap<>(1000);

    public void cacheMessage(Message message, String authorId) {
        if (message == null) return;

        String attachments = message.getAttachments().stream()
                .map(Attachment::getUrl)
                .collect(Collectors.joining("\n"));

        String content = message.getContentDisplay() +
                (attachments.isEmpty() ? "" : "\nAttachments:\n" + attachments);

        messageCache.put(message.getId(), content);
        userCache.put(message.getId(), authorId);
    }

    public String getMessageContent(String messageId) {
        return messageCache.getOrDefault(messageId, "Unknown content");
    }

    public String getMessageAuthorId(String messageId) {
        return userCache.get(messageId);
    }

    public void removeMessage(String messageId) {
        messageCache.remove(messageId);
        userCache.remove(messageId);
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
