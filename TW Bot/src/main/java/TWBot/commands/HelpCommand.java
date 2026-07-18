package TWBot.commands;

import TWBot.config.BotConfig;
import TWBot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class HelpCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("help", "View a detailed list of all bot commands and features")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 TW Bot - Command Help")
                .setDescription("A comprehensive guide to all available commands and features\n")
                .setColor(new Color(0, 150, 255))
                .setThumbnail("https://cdn.discordapp.com/emojis/" + BotConfig.TW_EMOJI_ID + ".png");

        addGeneralCommands(embed);
        addEventNameCommands(embed);
        addModerationCommands(embed);
        addStrikeCommands(embed);
        addVoiceCommands(embed);
        addAdminCommands(embed);
        addNeedHelpSection(embed);

        embed.setFooter("Use /[command] to execute. Moderator commands require proper permissions.")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void addGeneralCommands(EmbedBuilder embed) {
        embed.addField(
                "📌 General Commands",
                "**`/help`** - View this help menu\n" +
                "**`/test`** - Check if the bot is operational",
                false
        );
    }

    private void addEventNameCommands(EmbedBuilder embed) {
        embed.addField(
                "📝 Event Name Management",
                "**`/eventname submit`** - Register your in-game name for events\n" +
                "**`/eventname check`** - Look up a user's event name **(Moderator+)**",
                false
        );
    }

    private void addModerationCommands(EmbedBuilder embed) {
        embed.addField(
                "🔨 Moderation Actions",
                "**`/warn`** - Warn a user **(Moderator+)**\n" +
                "**`/mute`** - Mute a user (timed or permanent) **(Moderator+)**\n" +
                "**`/unmute`** - Unmute a user **(Moderator+)**\n" +
                "**`/timeout`** - Timeout a user **(Moderator+)**\n" +
                "**`/untimeout`** - Remove timeout from a user **(Moderator+)**\n" +
                "**`/kick`** - Kick a user **(Moderator+)**\n" +
                "**`/ban`** - Ban a user **(Moderator+)**\n" +
                "**`/unban`** - Unban a user **(Moderator+)**\n" +
                "**`/purge`** - Delete multiple messages **(Moderator+)**\n" +
                "**`/reason`** - View ban/unban history **(Moderator+)**\n" +
                "**`/role add/remove`** - Manage user roles **(Moderator+ / Lead)**\n" +
                "**`/setmuterole`** - Set the mute role **(Admin+)**\n" +
                "**`/restrict`** - Add channel restriction **(Admin+)**\n" +
                "**`/unrestrict`** - Remove channel restriction **(Admin+)**\n" +
                "**`/restrict-setup`** - Setup restrictions **(Admin+)**\n" +
                "**`/emojiscan`** - List all server emojis **(Moderator+)**",
                false
        );
    }

    private void addStrikeCommands(EmbedBuilder embed) {
        embed.addField(
                "⛔ Strike & Appeal System",
                "**`/strike`** - Issue a strike **(Moderator+)**\n" +
                "**`/strikes`** - View user strikes\n" +
                "**`/removestrike`** - Remove a specific strike **(Moderator+)**\n" +
                "**`/clearstrikes`** - Clear all strikes **(Admin+)**\n" +
                "**`/editstrike`** - Edit a strike reason **(Admin+)**\n" +
                "**`/appeal`** - Appeal your strikes\n" +
                "**`/myappeals`** - View your appeals\n" +
                "**`/pendingappeals`** - View pending appeals **(Moderator+)**\n" +
                "**`/reviewappeal`** - Approve/deny appeal **(Moderator+)**\n" +
                "**`/undoappeal`** - Reset a user's appeal **(Admin+)**",
                false
        );
    }

    private void addVoiceCommands(EmbedBuilder embed) {
        embed.addField(
                "🎙️ Voice Channel Management",
                "**`/vchelp`** - Detailed help for voice commands\n" +
                "**`/setup`** - Interactive setup for managed voice channels **(Admin+)**\n" +
                "**`/createvoice`** - Create a temporary voice channel\n" +
                "**`/deletevoice`** - Delete a channel you created\n" +
                "**`/mychannels`** - View your creation history\n" +
                "**`/activechannels`** - View currently active channels\n" +
                "**`/vcstats`** - View usage statistics\n" +
                "**`/vcdbinfo`** - View voice database stats **(Admin+)**",
                false
        );
    }

    private void addAdminCommands(EmbedBuilder embed) {
        embed.addField(
                "⚙️ Administrator Tools",
                "**`/dbinfo`** - View strike system statistics **(Admin+)**\n" +
                "**`/backupstrikes`** - Create a full database backup **(Admin+)**\n" +
                "**`/checkroles`** - Trigger manual role restoration check **(Admin+)**\n" +
                "**`/appealscanner`** - Manage the automated appeal scanner **(Admin+)**\n" +
                "**`/rolerestoration`** - Manage the role restoration service **(Admin+)**\n" +
                "**`/initdemotionlist`** - Initialize demotion list message **(Admin+)**\n" +
                "**`/updatedemotionlist`** - Force update demotion list **(Admin+)**\n" +
                "**`/adddemotion`** - Manually add to demotion list **(Admin+)**\n" +
                "**`/bulkadddemotions`** - Bulk add to demotion list **(Admin+)**\n" +
                "**`/removedemotion`** - Remove from demotion list **(Admin+)**\n" +
                "**`/bulkremovedemotion`** - Bulk remove from demotion list **(Admin+)**\n" +
                "**`/void-checker`** - Analyze message reactions **(Moderator+)**\n" +
                "**`/say`** - Make the bot say something **(Admin+)**\n" +
                "**`/cmdperm`** - Manage command permissions **(Ownership Only)**\n" +
                "**`/qcheck`** - View ownership schedule status **(Ownership Only)**",
                false
        );
    }

    private void addNeedHelpSection(EmbedBuilder embed) {
        embed.addField(
                "📞 Need Help?",
                "Reach out to <@" + BotConfig.OWNER_USER_ID + "> if it's an immediate emergency.",
                false
        );
    }
}
