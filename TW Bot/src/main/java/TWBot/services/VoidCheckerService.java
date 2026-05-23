package TWBot.services;

import TWBot.models.EventNameData;
import TWBot.models.UserData;
import TWBot.repositories.SQLiteEventNameRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class VoidCheckerService {
    private final SQLiteEventNameRepository eventNameRepository;

    public VoidCheckerService(SQLiteEventNameRepository eventNameRepository) {
        this.eventNameRepository = eventNameRepository;
    }

    public void checkUserReaction(MessageChannel messageChannel, String messageId, String queryName, User targetUser, Guild guild, Consumer<UserCheckResult> onSuccess, Runnable onUserNotFound, Runnable onNoReactions, Runnable onNoValidReactions, Consumer<String> onMessageNotFound, Consumer<Throwable> onError) {
        try {
            messageChannel.retrieveMessageById(messageId).queue(targetMessage -> {
                try {
                    List<MessageReaction> reactions = targetMessage.getReactions();
                    if (reactions.isEmpty()) {
                        onNoReactions.run();
                        return;
                    }

                    System.out.println("Starting to collect user data...");
                    collectUserData(reactions, guild).whenComplete((userData, error) -> {
                        System.out.println("Finished collecting user data: " + userData.size() + " users found");
                        if (error != null) {
                            onError.accept(error);
                            return;
                        }

                        if (userData.isEmpty()) {
                            onNoValidReactions.run();
                            return;
                        }

                        UserData foundUser = findUser(userData, queryName, targetUser);
                        System.out.println("Found user " + foundUser);

                        if (foundUser == null) {
                            onUserNotFound.run();
                            return;
                        }
                        onSuccess.accept(new UserCheckResult(foundUser, userData.size()));
                    });


                } catch (Exception e) {
                    onError.accept(e);
                }
            }, error -> onMessageNotFound.accept(error.getMessage()));
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    public String formatUserCheckResult(UserCheckResult result) {
        UserData foundUser = result.getUserData();
        int totalReactions = result.getTotalReactions();

        return "🌍 **" + foundUser.getUserName() + " (" + foundUser.getUserId() + ") is reacted (out of `" + totalReactions + "` reacts):** \n\n" + "Here is all the info I was able to find on the user you searched for...\n" + "```\n" + "USERNAME: " + foundUser.getUserName() + "\n" + "DISPLAY NAME: " + foundUser.getDisplayName() + "\n" + "NICKNAME: " + (foundUser.getNickname() != null ? foundUser.getNickname() : "None") + "\n" + "EVENTNAME: " + (foundUser.getEventName() != null ? foundUser.getEventName() : "None") + "\n" + "USERID: " + foundUser.getUserId() + "\n" + "```\n\n" + "As long as the IGN of this user is any of the names above, this user's wins **should not be voided.**";
    }


    private CompletableFuture<Map<String, UserData>> collectUserData(List<MessageReaction> reactions, Guild guild) {
        Map<String, UserData> userData = new HashMap<>();
        System.out.println("Processing " + reactions.size() + " reactions...");
        
        List<CompletableFuture<Void>> allFutures = new ArrayList<>();

        for (MessageReaction reaction : reactions) {
            System.out.println("Processing reaction: " + reaction.getEmoji() + " with count: " + reaction.getCount());

            CompletableFuture<Void> reactionFuture = new CompletableFuture<>();
            List<User> allUsers = new ArrayList<>();

            reaction.retrieveUsers().forEachAsync(user -> {
                if (!user.isBot()) {
                    allUsers.add(user);
                }
                return true;
            }).whenComplete((v, error) -> {
                if (error != null) {
                    System.err.println("Error retrieving users for reaction " + reaction.getEmoji() + ": " + error.getMessage());
                    reactionFuture.complete(null);
                    return;
                }

                System.out.println("Retrieved " + allUsers.size() + " users for reaction " + reaction.getEmoji());

                List<CompletableFuture<Void>> memberFutures = new ArrayList<>();
                for (User user : allUsers) {
                    CompletableFuture<Void> memberFuture = new CompletableFuture<>();
                    guild.retrieveMemberById(user.getId()).queue(member -> {
                        if (member == null) {
                            memberFuture.complete(null);
                            return;
                        }

                        String eventName = null;
                        EventNameData data = eventNameRepository.getEventNameByUser(user.getId());
                        if (data != null) {
                            eventName = data.getName();
                        }

                        synchronized (userData) {
                            userData.put(user.getId(), new UserData(user.getName().toLowerCase(), member.getEffectiveName().toLowerCase(), member.getNickname() != null ? member.getNickname().toLowerCase() : null, eventName != null ? eventName.toLowerCase() : null, user.getId()));
                        }
                        memberFuture.complete(null);
                    }, memberError -> {
                        if (memberError.getMessage() != null && !memberError.getMessage().contains("10007")) {
                            System.err.println("Error processing user " + user.getId() + ": " + memberError.getMessage());
                        }
                        memberFuture.complete(null);
                    });
                    memberFutures.add(memberFuture);
                }
                CompletableFuture.allOf(memberFutures.toArray(new CompletableFuture[0]))
                    .whenComplete((v2, error2) -> reactionFuture.complete(null));
            });
            allFutures.add(reactionFuture);
        }

        return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                System.out.println("All reactions processed, returning " + userData.size() + " users");
                return userData;
            });
    }

    private UserData findUser(Map<String, UserData> userData, String queryName, User targetUser) {
        System.out.println("Entering findUser with queryName = " + queryName + ", targetUser = " + targetUser);
        if (queryName != null && targetUser != null) {
            UserData data = userData.get(targetUser.getId());
            System.out.println("data " + data);
            return data;
        }

        if (targetUser != null) {
            System.out.println("targetUser " + targetUser);
            return userData.get(targetUser.getId());
        }

        if (queryName != null) {
            for (UserData data : userData.values()) {
                if (data.getUserName().equals(queryName) || data.getDisplayName().equals(queryName) || (data.getNickname() != null && data.getNickname().equals(queryName)) || (data.getEventName() != null && data.getEventName().equals(queryName))) {
                    System.out.println("data " + data);
                    return data;
                }
            }
        }

        return null;
    }

    public CompletableFuture<Boolean> doesMessageExist(MessageChannel messageChannel, String messageId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        messageChannel.retrieveMessageById(messageId).queue(message -> future.complete(true), error -> future.complete(false));

        return future;
    }

    public static class UserCheckResult {
        private final UserData userData;
        private final int totalReactions;

        public UserCheckResult(UserData userData, int totalReactions) {
            this.userData = userData;
            this.totalReactions = totalReactions;
        }

        public UserData getUserData() {
            return userData;
        }

        public int getTotalReactions() {
            return totalReactions;
        }
    }
}