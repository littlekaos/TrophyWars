package TWBot.commands;

import TWBot.TWBot;
import TWBot.models.VoiceChannelRecord;
import TWBot.services.VoiceChannelManager;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class VoiceCommand implements Command {
    private final TWBot bot;
    private final VoiceChannelManager channelManager;

    public VoiceCommand(TWBot bot) {
        this.bot = bot;
        this.channelManager = bot.getVoiceChannelManager();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
            Commands.slash("vchelp", "View detailed help about voice commands"),
            Commands.slash("setup", "Set up the Voice Channel Manager for this server"),
            Commands.slash("createvoice", "Create a new voice channel")
                .addOption(OptionType.STRING, "name", "The name of the voice channel", true)
                .addOption(OptionType.INTEGER, "limit", "User limit (0 for no limit)", false),
            Commands.slash("deletevoice", "Delete a voice channel you created")
                .addOption(OptionType.CHANNEL, "channel", "The voice channel to delete", true),
            Commands.slash("vcstats", "View voice channel statistics")
                .addOption(OptionType.STRING, "type", "Type of stats (server/global/user)", true)
                .addOption(OptionType.USER, "user", "User to view stats for (optional)", false),
            Commands.slash("mychannels", "View your recently created voice channels"),
            Commands.slash("activechannels", "View currently active voice channels"),
            Commands.slash("vcdbinfo", "View voice channel database information (Admin only)")
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "vchelp":
                handleHelp(event);
                break;
            case "setup":
                // Handled by EventsSetupCommandListener
                break;
            case "createvoice":
                handleCreate(event);
                break;
            case "deletevoice":
                handleDelete(event);
                break;
            case "vcstats":
                handleStats(event);
                break;
            case "mychannels":
                handleMyChannels(event);
                break;
            case "activechannels":
                handleActive(event);
                break;
            case "vcdbinfo":
                handleDbInfo(event);
                break;
        }
    }

    private void handleHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎙️ Voice Channel Manager Help")
                .setColor(new Color(0, 200, 100))
                .setDescription("Manage your own voice channels and track activity.\n")
                .addField("Commands",
                        "**`/setup`** - Interactive setup for managed VCs (Admin only)\n" +
                        "**`/createvoice`** - Create a temporary voice channel\n" +
                        "**`/deletevoice`** - Delete a channel you created\n" +
                        "**`/mychannels`** - View your creation history\n" +
                        "**`/activechannels`** - View currently active channels\n" +
                        "**`/vcstats`** - View usage statistics\n" +
                        "**`/vcdbinfo`** - View database stats (Admin only)", false)
                .setTimestamp(Instant.now());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private boolean hasVoiceAdminPermissions(Member member) {
        if (member == null) return false;
        return PermissionUtils.isAdmin(member, bot.getConfig());
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        if (!hasVoiceAdminPermissions(event.getMember())) {
            event.reply("You don't have permission to create voice channels. Only managers and overseers can use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        String name = event.getOption("name").getAsString();
        int limit = event.getOption("limit") != null ? event.getOption("limit").getAsInt() : 0;

        if (limit < 0 || limit > 99) {
            event.reply("Limit must be between 0 and 99.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        channelManager.createCustomVoiceChannel(event.getGuild(), event.getUser(), name, limit, 
            () -> event.getHook().sendMessage("✅ Voice channel **" + name + "** created!").queue(),
            error -> event.getHook().sendMessage("❌ Failed to create channel: " + error.getMessage()).queue()
        );
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        VoiceChannel channel = event.getOption("channel").getAsChannel().asVoiceChannel();
        
        boolean isCreator = channelManager.isVoiceChannelCreator(event.getUser(), channel);
        boolean isAdmin = hasVoiceAdminPermissions(event.getMember());

        if (!isCreator && !isAdmin) {
            event.reply("You can only delete voice channels that you created or if you have admin permissions.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        channel.delete().queue(v -> {
            channelManager.deleteUserVoiceChannel(event.getUser(), channel);
            event.getHook().sendMessage("✅ Channel deleted.").queue();
        }, error -> {
            event.getHook().sendMessage("❌ Failed to delete channel: " + error.getMessage()).queue();
        });
    }

    private void handleStats(SlashCommandInteractionEvent event) {
        String type = event.getOption("type").getAsString().toLowerCase();
        User user = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();

        EmbedBuilder embed = new EmbedBuilder().setTimestamp(Instant.now());

        if (type.equals("user")) {
            List<VoiceChannelRecord> channels = channelManager.getVoiceService().getUserCreatedChannels(user.getId(), event.getGuild().getId());
            embed.setTitle("📊 User Stats: " + user.getName())
                 .setColor(Color.BLUE)
                 .setDescription("Total channels created: " + channels.size());
            if (!channels.isEmpty()) {
                StringBuilder sb = new StringBuilder("**Recent Channels:**\n");
                for (int i = 0; i < Math.min(channels.size(), 5); i++) {
                    sb.append("• ").append(channels.get(i).getChannelName()).append("\n");
                }
                embed.addField("", sb.toString(), false);
            }
        } else if (type.equals("server")) {
            int activeCount = channelManager.getVoiceService().getActiveChannelCount(event.getGuild().getId());
            embed.setTitle("📊 Server Stats")
                 .setColor(Color.GREEN)
                 .addField("Active Channels", String.valueOf(activeCount), false);
        } else {
            int total = channelManager.getVoiceService().getTotalChannelsCreated();
            embed.setTitle("🌍 Global Stats")
                 .setColor(Color.ORANGE)
                 .addField("Total Channels Created", String.valueOf(total), false);
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleMyChannels(SlashCommandInteractionEvent event) {
        List<VoiceChannelRecord> channels = channelManager.getVoiceService().getUserCreatedChannels(event.getUser().getId(), event.getGuild().getId());
        if (channels.isEmpty()) {
            event.reply("You haven't created any channels yet.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Your Voice Channels")
                .setColor(Color.CYAN);
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(channels.size(), 10); i++) {
            VoiceChannelRecord r = channels.get(i);
            sb.append("**").append(r.getChannelName()).append("** - ").append(r.isActive() ? "🟢 Active" : "🔴 Deleted").append("\n");
        }
        embed.setDescription(sb.toString());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleActive(SlashCommandInteractionEvent event) {
        List<VoiceChannelRecord> channels = channelManager.getVoiceService().getActiveChannels(event.getGuild().getId());
        if (channels.isEmpty()) {
            event.reply("No active managed channels.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔊 Active Channels")
                .setColor(Color.GREEN);
        
        StringBuilder sb = new StringBuilder();
        for (VoiceChannelRecord r : channels) {
            sb.append("• **").append(r.getChannelName()).append("** (Created by ").append(r.getCreatorName()).append(")\n");
        }
        embed.setDescription(sb.toString());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleDbInfo(SlashCommandInteractionEvent event) {
        if (!hasVoiceAdminPermissions(event.getMember())) {
            event.reply("You do not have permission to view database info.").setEphemeral(true).queue();
            return;
        }

        int totalChannels = channelManager.getVoiceService().getTotalChannelsCreated();
        int activeChannels = channelManager.getVoiceService().getActiveChannelCount(event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Voice Database Information")
                .setColor(Color.CYAN)
                .addField("Total Channels Tracked", String.valueOf(totalChannels), false)
                .addField("Active Channels (This Server)", String.valueOf(activeChannels), false)
                .setFooter("Voice Channel Management System")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
