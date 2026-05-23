package TWBot.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BanService {

    public void banUser(
            Guild guild,
            User targetUser,
            String reason,
            TextChannel modLogChannel,
            User moderatorUser,
            Consumer<Void> onSuccess,
            Consumer<Throwable> onError) {
        try {
            guild.ban(targetUser, 0, TimeUnit.DAYS)
                    .reason(reason)
                    .queue(
                            success -> {
                                logBanAction(modLogChannel, moderatorUser, targetUser, reason);
                                onSuccess.accept(success);
                            },
                            error -> onError.accept(error)
                    );
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    public void unbanUser(Guild guild, String userIdToUnban, TextChannel modLogChannel,
                          User moderatorUser, Consumer<Void> onSuccess,
                          Consumer<Throwable> onError, Runnable onUserNotBanned) {
        try {
            guild.retrieveBan(UserSnowflake.fromId(userIdToUnban)).queue(
                    ban -> {
                        guild.unban(UserSnowflake.fromId(userIdToUnban)).queue(
                                success -> {
                                    logUnbanAction(modLogChannel, moderatorUser, userIdToUnban);
                                    onSuccess.accept(success);
                                },
                                error -> onError.accept(error)
                        );
                    },
                    error -> onUserNotBanned.run()
            );
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    public CompletableFuture<Boolean> isUserBanned(Guild guild, String userId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        guild.retrieveBan(UserSnowflake.fromId(userId)).queue(
                ban -> future.complete(true),
                error -> future.complete(false)
        );

        return future;
    }

    private void logBanAction(TextChannel modLogChannel, User moderator, User target, String reason) {
        if (modLogChannel != null) {
            modLogChannel.sendMessage(moderator.getAsMention() + " (`" + moderator.getId() +
                    "`) **banned a user**:\n> User: " + target.getName() + " (`" + target.getId() +
                    "`)\n> Reason: " + reason).queue();
        }
    }

    private void logUnbanAction(TextChannel modLogChannel, User moderator, String targetId) {
        if (modLogChannel != null) {
            modLogChannel.sendMessage(moderator.getAsMention() + " (`" + moderator.getId() +
                    "`) **unbanned a user**:\n> User: (`" + targetId + "`)").queue();
        }
    }
}