package TWBot.utils;

import TWBot.config.BotConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.util.List;

public class PermissionUtils {

    public static boolean isModerator(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> modRoles = config.getModRoles();
        return member.getRoles().stream()
                .anyMatch(role -> modRoles.contains(role.getId()));
    }

    public static boolean isAdmin(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> adminRoles = config.getAdminRoles();
        
        return member.getRoles().stream()
                .anyMatch(role -> adminRoles.contains(role.getId()));
    }

    public static boolean isHighManagement(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        // Literal server owner is always high management
        if (member.isOwner()) return true;

        // Only Management and Owner (Overseer is removed from here for approval logic)
        List<String> highManagementRoles = java.util.Arrays.asList(
                BotConfig.MANAGER_ROLE_ID,
                BotConfig.SERVER_OWNERSHIP_ROLE_ID
        );

        return member.getRoles().stream()
                .anyMatch(role -> highManagementRoles.contains(role.getId()));
    }



    public static boolean isLead(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }

        List<String> leadRoles = config.getLeadRoles();
        return member.getRoles().stream()
                .anyMatch(role -> leadRoles.contains(role.getId()));
    }

    public static boolean canModerate(Member moderator, Member target) {
        if (moderator == null || target == null) return false;
        
        // Server owner can always moderate anyone else
        if (moderator.isOwner()) return true;
        
        // Cannot moderate the server owner
        if (target.isOwner()) return false;

        // follows Discord's native role hierarchy order
        return moderator.canInteract(target);
    }
}