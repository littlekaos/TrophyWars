package TWBot.commands;

import TWBot.TWBot;
import TWBot.repositories.SQLiteEventNameRepository;
import TWBot.services.VoidCheckerService;
import TWBot.utils.EmbedUtils;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.User;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;

public class VoidCheckerCommand implements Command {
    private final TWBot bot;
    private final SQLiteEventNameRepository eventNameRepository;
    private final VoidCheckerService voidCheckerService;

    private static final OptionData[] VOID_CHECKER_OPTIONS = {
            new OptionData(OptionType.STRING, "reaction-message", "The ID of the message to check", true),
            new OptionData(OptionType.STRING, "user-name", "Check an EVENTNAME, username, nickname, or user ID", false),
            new OptionData(OptionType.USER, "user", "Check a user", false)
    };

    public VoidCheckerCommand(TWBot bot) {
        this.bot = bot;
        this.eventNameRepository = bot.getEventNameRepository();
        this.voidCheckerService = new VoidCheckerService(this.eventNameRepository);
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("void-checker", "Check a message for user reactions")
                .addOptions(VOID_CHECKER_OPTIONS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean hasPermission = PermissionUtils.isModerator(event.getMember(), bot.getConfig());

        if (!hasPermission) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You don't have permission to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        String messageId = event.getOption("reaction-message").getAsString();
        OptionMapping userNameOption = event.getOption("user-name");
        OptionMapping userOption = event.getOption("user");

        String queryName = userNameOption != null ? userNameOption.getAsString().toLowerCase() : null;
        User targetUser = userOption != null ? userOption.getAsUser() : null;
        System.out.println("targetUser " + targetUser);

        if (targetUser == null && queryName == null) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "You must provide an input option for a user scanner"
            )).queue();
            return;
        }

        voidCheckerService.checkUserReaction(
                event.getChannel(),
                messageId,
                queryName,
                targetUser,
                event.getGuild(),
                userCheckResult -> {
                    String formattedMessage = voidCheckerService.formatUserCheckResult(userCheckResult);
                    event.getHook().sendMessageEmbeds(EmbedUtils.createEmbed(
                            Color.BLUE,
                            formattedMessage
                    )).queue();
                },

                // onUserNotFound
                () -> {
                    event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "That user is not reacted. **Try checking the user individually, or check the user name and not the discord name shown.** " +
                                    "If you are checking an event name, the win should be **voided.**"
                    )).queue();
                },

                //onNoReactions
                () -> {
                    event.getHook().sendMessageEmbeds(EmbedUtils.createEmbed(
                            Color.YELLOW,
                            "⚠️ No reactions found on the message"
                    )).queue();
                },

                // onNoValidReactions
                () -> {
                    event.getHook().sendMessageEmbeds(EmbedUtils.createEmbed(
                            Color.YELLOW,
                            "⚠️ No valid user reactions found"
                    )).queue();
                },

                // onMessageNotFound
                onMessageNotFound -> {
                    event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Could not find that message (Error " + onMessageNotFound +
                                    "). **RUN THIS COMMAND IN THE CHANNEL THE MESSAGE IS IN**"
                    )).queue();
                },

                // onError
                throwable -> {
                    throwable.printStackTrace();
                    event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "An error occurred while processing the message: " + throwable.getMessage()
                    )).queue();
                }
        );
    }


}