package TWBot.commands;

import TWBot.TWBot;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;

public interface Command {

    List<CommandData> getCommandDataList();

    void execute(SlashCommandInteractionEvent event);

    default boolean hasPermission(SlashCommandInteractionEvent event, TWBot bot) {
        return true;
    }
}