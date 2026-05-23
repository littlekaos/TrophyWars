package TWBot.commands;

import TWBot.TWBot;
import TWBot.config.BotConfig;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;
import java.util.Objects;

public class SayCommand implements Command {

    private final TWBot bot;

    public SayCommand(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("say", "Make the bot say something")
                .addOption(OptionType.STRING, "message", "The message you want the bot to say", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String message = Objects.requireNonNull(event.getOption("message")).getAsString();
        
        if (message.contains("@everyone") || message.contains("@here")) {
            event.reply("❌ You cannot include `@everyone` or `@here` in the message.").setEphemeral(true).queue();
            return;
        }
        
        event.getChannel().sendMessage(message).queue();
        event.reply("✅ Message sent!").setEphemeral(true).queue();
    }

    @Override
    public boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return PermissionUtils.isAdmin(event.getMember(), bot.getConfig()) || event.getUser().getId().equals(BotConfig.OWNER_USER_ID);
    }
}
