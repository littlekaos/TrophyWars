package TWBot.services;

import TWBot.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EmojiScannerService {
    private final JDA jda;
    private final BotConfig config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private List<RichCustomEmoji> cachedEmojis = Collections.synchronizedList(new ArrayList<>());
    private int currentIndex = 0;

    public EmojiScannerService(JDA jda, BotConfig config) {
        this.jda = jda;
        this.config = config;
    }

    public void start() {
        // Initial scan
        scanEmojis();
        
        // Schedule scan every 30 minutes
        scheduler.scheduleAtFixedRate(this::scanEmojis, 30, 30, TimeUnit.MINUTES);
    }

    private void scanEmojis() {
        try {
            String guildId = config.getGuildId();
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                List<RichCustomEmoji> currentEmojis = guild.getEmojis();
                cachedEmojis.clear();
                cachedEmojis.addAll(currentEmojis);
                System.out.println("EmojiScannerService: Scanned and cached " + cachedEmojis.size() + " emojis.");
            } else {
                System.err.println("EmojiScannerService: Could not find guild with ID: " + guildId);
            }
        } catch (Exception e) {
            System.err.println("EmojiScannerService: Error scanning emojis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<RichCustomEmoji> getCachedEmojis() {
        return new ArrayList<>(cachedEmojis);
    }

    public synchronized List<RichCustomEmoji> getNextEmojis(int count) {
        if (cachedEmojis.isEmpty()) {
            return new ArrayList<>();
        }

        List<RichCustomEmoji> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (currentIndex >= cachedEmojis.size()) {
                currentIndex = 0;
            }
            result.add(cachedEmojis.get(currentIndex));
            currentIndex++;
        }
        return result;
    }

    public synchronized List<RichCustomEmoji> getRandomEmojis(int count) {
        if (cachedEmojis.isEmpty()) {
            return new ArrayList<>();
        }

        List<RichCustomEmoji> copy = new ArrayList<>(cachedEmojis);
        Collections.shuffle(copy);
        return copy.stream().limit(count).collect(java.util.stream.Collectors.toList());
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
