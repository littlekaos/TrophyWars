package TWBot.events;

import TWBot.TWBot;
import TWBot.models.ModAction;
import TWBot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SelectMenuEventListener extends ListenerAdapter {
    private final TWBot bot;
    private final Map<String, String> userRestrictionChoice = new ConcurrentHashMap<>();

    public SelectMenuEventListener(TWBot bot) {
        this.bot = bot;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.isAcknowledged()) return;
        
        if (!event.getComponentId().equals("restriction_type_select")) return;

        if (!PermissionUtils.isAdmin(event.getMember(), bot.getConfig())) {
            event.reply("❌ You need **Administrator or Overseer** permissions to use this setup.").setEphemeral(true).queue();
            return;
        }

        String type = event.getValues().get(0);
        userRestrictionChoice.put(event.getUser().getId(), type);

        EntitySelectMenu channelMenu = EntitySelectMenu.create("restriction_channel_select", EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("Select channel(s) to apply this restriction to")
                .setChannelTypes(ChannelType.TEXT)
                .setMinValues(1)
                .setMaxValues(25)
                .build();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Channel Restriction Setup")
                .setDescription("Selected Type: **" + type + "**\n\nNow, select the channels you want to apply this restriction to.")
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());

        event.editMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(channelMenu))
                .queue();
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (event.isAcknowledged()) return;
        
        if (!event.getComponentId().equals("restriction_channel_select")) return;

        if (!PermissionUtils.isAdmin(event.getMember(), bot.getConfig())) {
            event.reply("❌ You need **Administrator or Overseer** permissions to use this setup.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        String type = userRestrictionChoice.remove(event.getUser().getId());
        if (type == null) {
            event.getHook().sendMessage("❌ Session expired. Please start over with `/restrict-setup`.").setEphemeral(true).queue();
            return;
        }

        List<GuildChannel> channels = event.getMentions().getChannels();
        StringBuilder sb = new StringBuilder();

        for (GuildChannel channel : channels) {
            bot.getRestrictionService().addRestriction(channel.getId(), type);
            bot.getDataService().saveModAction(ModAction.ActionType.RESTRICT, event.getUser().getId(), event.getUser().getName(),
                    channel.getId(), channel.getName(), "Setup Restriction: " + type, 0, 0);
            sb.append("• ").append(channel.getAsMention()).append("\n");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Setup Complete!")
                .setDescription(String.format("Restriction **%s** has been applied to the following channels:\n\n%s", type, sb))
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(embed.build())
                .setComponents(List.of())
                .queue();
    }
}
