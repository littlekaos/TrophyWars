package TWBot.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.*;

public class EventsSetupManager {
    private final VoiceChannelService voiceService;
    private final Map<String, SetupSession> activeSessions = new HashMap<>();

    public EventsSetupManager(VoiceChannelService voiceService) {
        this.voiceService = voiceService;
    }

    public void startSetupSession(String guildId, String userId) {
        activeSessions.put(guildId, new SetupSession(guildId, userId));
    }

    public SetupSession getSession(String guildId) {
        return activeSessions.get(guildId);
    }

    public void completeSetupSession(String guildId) {
        activeSessions.remove(guildId);
    }

    public void setSelectedCategory(String guildId, String categoryId) {
        SetupSession session = getSession(guildId);
        if (session != null) {
            session.setSelectedCategory(categoryId);
        }
    }

    public void setManagedVoiceChannels(String guildId, List<String> vcIds) {
        SetupSession session = getSession(guildId);
        if (session != null) {
            session.setManagedVoiceChannels(vcIds);
        }
    }

    public void saveSetup(String guildId) {
        SetupSession session = getSession(guildId);
        if (session != null) {
            String categoryId = session.getSelectedCategory();
            List<String> vcIds = session.getManagedVoiceChannels();
            String vcIdsStr = String.join(",", vcIds);
            voiceService.saveServerSetup(guildId, categoryId, vcIdsStr);
        }
    }

    public String getCategoryId(String guildId) {
        return voiceService.getCategoryId(guildId);
    }

    public List<String> getManagedVoiceChannels(String guildId) {
        String vcIdsStr = voiceService.getManagedVcIds(guildId);
        if (vcIdsStr == null || vcIdsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(vcIdsStr.split(","));
    }

    public boolean hasServerSetup(String guildId) {
        return voiceService.hasServerSetup(guildId);
    }

    public List<Category> getServerCategories(Guild guild) {
        return guild.getCategories();
    }

    public List<VoiceChannel> getVoiceChannelsInCategory(Guild guild, String categoryId) {
        Category category = guild.getCategoryById(categoryId);
        if (category != null) {
            return category.getVoiceChannels();
        }
        return new ArrayList<>();
    }

    public static class SetupSession {
        private final String guildId;
        private final String userId;
        private String selectedCategory;
        private List<String> managedVoiceChannels = new ArrayList<>();

        public SetupSession(String guildId, String userId) {
            this.guildId = guildId;
            this.userId = userId;
        }

        public String getGuildId() {
            return guildId;
        }

        public String getUserId() {
            return userId;
        }

        public String getSelectedCategory() {
            return selectedCategory;
        }

        public void setSelectedCategory(String selectedCategory) {
            this.selectedCategory = selectedCategory;
        }

        public List<String> getManagedVoiceChannels() {
            return managedVoiceChannels;
        }

        public void setManagedVoiceChannels(List<String> managedVoiceChannels) {
            this.managedVoiceChannels = managedVoiceChannels;
        }
    }
}
