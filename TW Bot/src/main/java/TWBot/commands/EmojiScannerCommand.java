package TWBot.commands;

import TWBot.TWBot;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class EmojiScannerCommand implements Command {
    private final TWBot bot;

    public EmojiScannerCommand(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("emojiscan", "Scans and lists all emojis in the server"));
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isModerator(event.getMember(), bot.getConfig());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        List<RichCustomEmoji> emojis = event.getGuild().getEmojis();
        if (emojis.isEmpty()) {
            event.getHook().sendMessage("No custom emojis found in this server.").queue();
            return;
        }

        List<String> emojiStrings = new ArrayList<>();
        for (RichCustomEmoji emoji : emojis) {
            emojiStrings.add(String.format("%s: %s (ID: `%s`)", emoji.getName(), emoji.getAsMention(), emoji.getId()));
        }

        StringBuilder currentMessage = new StringBuilder("**Guild Emojis Scanned:**\n\n");
        boolean first = true;

        for (String emojiStr : emojiStrings) {
            if (currentMessage.length() + emojiStr.length() + 2 > 2000) {
                event.getHook().sendMessage(currentMessage.toString()).queue();
                first = false;
                currentMessage = new StringBuilder();
            }
            currentMessage.append(emojiStr).append("\n");
        }

        if (currentMessage.length() > 0) {
            event.getHook().sendMessage(currentMessage.toString()).queue();
        }
    }
}
