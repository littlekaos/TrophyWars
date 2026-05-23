package TWBot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.Instant;

public class EmbedUtils {

    public static MessageEmbed createEmbed(Color color, String description) {
        return new EmbedBuilder()
                .setColor(color)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .build();
    }

    public static MessageEmbed createEmbed(String title, Color color, String description) {
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .build();
    }

    public static MessageEmbed createSuccessEmbed(String description) {
        return createEmbed(Color.GREEN, "✅ " + description);
    }

    public static MessageEmbed createErrorEmbed(String description) {
        return createEmbed(Color.RED, "❌ " + description);
    }

    public static MessageEmbed createWarningEmbed(String description) {
        return new EmbedBuilder()
                .setTitle("⚠️ Warning")
                .setDescription(description)
                .setColor(Color.ORANGE)
                .setFooter("This message will self-destruct in 5 seconds", null)
                .setTimestamp(Instant.now())
                .build();
    }

    public static MessageEmbed createInfoEmbed(String description) {
        return createEmbed(Color.BLUE, "ℹ️ " + description);
    }
}