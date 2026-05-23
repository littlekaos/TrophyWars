package TWBot.services;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.Permission;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RestrictionService {
    private final DataService dataService;
    private final Set<String> noMessageChannels = new HashSet<>();
    private final Set<String> noMediaChannels = new HashSet<>();
    private final Set<String> noContentChannels = new HashSet<>();
    private final Set<String> screenshotOnlyChannels = new HashSet<>();
    private final Set<String> mediaOnlyChannels = new HashSet<>();
    private final Set<String> mediaWithTextChannels = new HashSet<>();
    private final Set<String> textOnlyChannels = new HashSet<>();

    public RestrictionService(DataService dataService) {
        this.dataService = dataService;
        loadRestrictions();
    }

    public void loadRestrictions() {
        noMessageChannels.clear();
        noMediaChannels.clear();
        noContentChannels.clear();
        screenshotOnlyChannels.clear();
        mediaOnlyChannels.clear();
        mediaWithTextChannels.clear();
        textOnlyChannels.clear();

        List<DataService.RestrictionEntry> restrictions = dataService.getAllChannelRestrictions();
        for (DataService.RestrictionEntry entry : restrictions) {
            addRestrictionToCache(entry.channelId(), entry.type());
        }
    }

    private void addRestrictionToCache(String channelId, String type) {
        switch (type) {
            case "NO_MESSAGE": noMessageChannels.add(channelId); break;
            case "NO_MEDIA": noMediaChannels.add(channelId); break;
            case "NO_CONTENT": noContentChannels.add(channelId); break;
            case "SCREENSHOT_ONLY": screenshotOnlyChannels.add(channelId); break;
            case "MEDIA_ONLY": mediaOnlyChannels.add(channelId); break;
            case "MEDIA_WITH_TEXT": mediaWithTextChannels.add(channelId); break;
            case "TEXT_ONLY": textOnlyChannels.add(channelId); break;
        }
    }

    public void addRestriction(String channelId, String type) {
        dataService.addChannelRestriction(channelId, type);
        addRestrictionToCache(channelId, type);
    }

    public void removeRestriction(String channelId, String type) {
        dataService.removeChannelRestriction(channelId, type);
        switch (type) {
            case "NO_MESSAGE": noMessageChannels.remove(channelId); break;
            case "NO_MEDIA": noMediaChannels.remove(channelId); break;
            case "NO_CONTENT": noContentChannels.remove(channelId); break;
            case "SCREENSHOT_ONLY": screenshotOnlyChannels.remove(channelId); break;
            case "MEDIA_ONLY": mediaOnlyChannels.remove(channelId); break;
            case "MEDIA_WITH_TEXT": mediaWithTextChannels.remove(channelId); break;
            case "TEXT_ONLY": textOnlyChannels.remove(channelId); break;
        }
    }

    public Set<String> getNoMessageChannels() { return noMessageChannels; }
    public Set<String> getNoMediaChannels() { return noMediaChannels; }
    public Set<String> getNoContentChannels() { return noContentChannels; }
    public Set<String> getScreenshotOnlyChannels() { return screenshotOnlyChannels; }
    public Set<String> getMediaOnlyChannels() { return mediaOnlyChannels; }
    public Set<String> getMediaWithTextChannels() { return mediaWithTextChannels; }
    public Set<String> getTextOnlyChannels() { return textOnlyChannels; }
}
