package TWBot.services;

import TWBot.TWBot;
import TWBot.models.ModAction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MuteService {
    private final TWBot bot;
    private final JDA jda;
    private final DataService dataService;
    private final ScheduledExecutorService scheduler;

    public MuteService(TWBot bot) {
        this.bot = bot;
        this.jda = bot.getJda();
        this.dataService = bot.getDataService();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkMutes, 1, 1, TimeUnit.MINUTES);
    }

    private void checkMutes() {
        List<DataService.MuteEntry> expiredMutes = dataService.getExpiredMutes(System.currentTimeMillis());
        
        for (DataService.MuteEntry entry : expiredMutes) {
            unmuteUser(entry.guildId(), entry.userId());
        }
    }

    private void unmuteUser(String guildId, String userId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;

        String muteRoleId = dataService.getMuteRoleId(guildId);
        if (muteRoleId == null) {
            dataService.removeMute(guildId, userId);
            return;
        }

        Role muteRole = guild.getRoleById(muteRoleId);
        if (muteRole == null) {
            dataService.removeMute(guildId, userId);
            return;
        }

        guild.retrieveMemberById(userId).queue(member -> {
            guild.removeRoleFromMember(member, muteRole).queue(success -> {
                dataService.removeMute(guildId, userId);
                dataService.saveModAction(ModAction.ActionType.UNMUTE, jda.getSelfUser().getId(), jda.getSelfUser().getName(),
                        userId, member.getUser().getName(), "Auto-unmute: Duration expired", 0, 0);
                System.out.println("Auto-unmuted " + member.getUser().getName() + " in guild " + guild.getName());
            }, error -> {
                System.err.println("Failed to auto-unmute " + member.getUser().getName() + ": " + error.getMessage());
                // If we can't remove the role (e.g. permission issue), we should probably still remove it from DB 
                // to avoid infinite loops, or just log it.
                // For now, let's keep it in DB so we can try again later if it was a transient error.
            });
        }, error -> {
            // User left the server?
            dataService.removeMute(guildId, userId);
            System.out.println("Removed expired mute for user " + userId + " who is no longer in guild " + guild.getName());
        });
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
