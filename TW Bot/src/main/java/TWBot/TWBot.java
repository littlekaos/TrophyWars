package TWBot;

import TWBot.config.BotConfig;
import TWBot.database.DatabaseManager;
import TWBot.events.*;
import TWBot.repositories.SQLiteEventNameRepository;
import TWBot.services.AppealScannerService;
import TWBot.services.AppealService;
import TWBot.services.ConfirmationService;
import TWBot.services.DataService;
import TWBot.services.DemotionService;
import TWBot.services.DemotionSyncService;
import TWBot.services.EventsSetupManager;
import TWBot.services.LoggingService;
import TWBot.services.MessageCache;
import TWBot.services.MuteService;
import TWBot.services.OwnershipPingService;
import TWBot.services.RestrictionService;
import TWBot.services.RoleRestorationService;
import TWBot.services.StrikeScannerService;
import TWBot.services.StrikeService;
import TWBot.services.UserCache;
import TWBot.services.VoiceChannelManager;
import TWBot.services.VoiceChannelService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class TWBot {
    private JDA jda;
    private final BotConfig config;
    private SQLiteEventNameRepository eventNameRepository;
    private StrikeService strikeService;
    private StrikeScannerService strikeScannerService;
    private AppealService appealService;
    private AppealScannerService appealScannerService;
    private RoleRestorationService roleRestorationService;
    private DemotionService demotionService;
    private DemotionSyncService demotionSyncService;
    private RestrictionService restrictionService;
    private DataService dataService;
    private LoggingService loggingService;
    private MuteService muteService;
    private ConfirmationService confirmationService;
    private VoiceChannelService voiceChannelService;
    private VoiceChannelManager voiceChannelManager;
    private EventsSetupManager eventsSetupManager;
    private OwnershipPingService ownershipPingService;
    private MessageCache messageCache;
    private UserCache userCache;

    public TWBot() {
        this.config = new BotConfig();
    }

    public void start() {
        try {
            System.out.println("Starting TWBot...");

            String token = config.getToken();
            if (token == null || token.isEmpty()) {
                System.err.println("Bot token not found! Please set BOT_TOKEN in environment variables or .env file.");
                return;
            }

            DatabaseManager dbManager = DatabaseManager.getInstance();
            if (!dbManager.testConnection()) {
                System.err.println("Failed to connect to database! Please check your database configuration.");
                return;
            }
            System.out.println("Database connection successful!");
            dbManager.startPeriodicBackups();

            this.eventNameRepository = new SQLiteEventNameRepository();
            System.out.println("Event name repository initialized!");

            this.loggingService = new LoggingService();
            this.dataService = new DataService();
            this.confirmationService = new ConfirmationService();
            this.voiceChannelService = new VoiceChannelService();
            this.voiceChannelManager = new VoiceChannelManager(voiceChannelService);
            this.eventsSetupManager = new EventsSetupManager(voiceChannelService);
            this.messageCache = new MessageCache();
            this.userCache = new UserCache();
            this.restrictionService = new RestrictionService(dataService);
            this.strikeService = new StrikeService();
            this.strikeService.importInitialStrikes();
            this.strikeScannerService = new StrikeScannerService(strikeService, config);
            this.appealService = new AppealService();
            this.demotionService = new DemotionService(strikeService, config);
            this.demotionSyncService = new DemotionSyncService(demotionService, strikeService.getDatabase(), config);
            this.appealService.setDemotionService(demotionService);
            this.appealScannerService = new AppealScannerService(appealService, strikeService, config);
            this.roleRestorationService = new RoleRestorationService(demotionService, config);
            this.demotionService.setRoleRestorationService(roleRestorationService);
            this.appealService.setRoleRestorationService(roleRestorationService);

            this.jda = JDABuilder.createDefault(token)
                    .setActivity(Activity.streaming(config.getStatusText(), config.getStatusUrl()))
                    .setStatus(config.getOnlineStatus())
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.GUILD_VOICE_STATES,
                            GatewayIntent.GUILD_INVITES,
                            GatewayIntent.GUILD_EXPRESSIONS
                    )
                    .addEventListeners(
                            new CommandEventListener(this),
                            new GuildEventListener(this),
                            new MessageEventListener(this),
                            new ModalEventListener(this),
                            new ButtonEventListener(this),
                            new SelectMenuEventListener(this),
                            new DemotionProtectionListener(demotionService),
                            new VoiceEventListener(this, voiceChannelManager, eventsSetupManager),
                            new EventsSetupCommandListener(this),
                            new ServerLogEventListener(this)
                    )
                    .build()
                    .awaitReady();

            CommandEventListener.registerCommands(jda, config, this);
            this.userCache.setJDA(jda);
            this.appealService.setJda(jda);

            this.appealScannerService.initialize(jda);
            this.roleRestorationService.initialize(jda);
            this.demotionSyncService.initialize(jda);
            this.demotionService.updateDemotionListMessage(jda);

            this.ownershipPingService = new OwnershipPingService(jda, dataService);
            this.ownershipPingService.start();

            this.strikeScannerService.initialize(jda);

            jda.getGuilds().forEach(guild -> {
                dataService.setMuteRoleId(guild.getId(), BotConfig.MUTE_ROLE_ID);
            });

            this.muteService = new MuteService(this);
            muteService.start();
            System.out.println("Mute service started!");

            System.out.println("TWBot started successfully!");

        } catch (Exception e) {
            System.err.println("Failed to start bot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public BotConfig getConfig() {
        return config;
    }

    public SQLiteEventNameRepository getEventNameRepository() {
        return eventNameRepository;
    }

    public JDA getJda() {
        return jda;
    }

    public StrikeService getStrikeService() {
        return strikeService;
    }

    public StrikeScannerService getStrikeScannerService() {
        return strikeScannerService;
    }

    public AppealService getAppealService() {
        return appealService;
    }

    public AppealScannerService getAppealScannerService() {
        return appealScannerService;
    }

    public RoleRestorationService getRoleRestorationService() {
        return roleRestorationService;
    }

    public DemotionService getDemotionService() {
        return demotionService;
    }

    public RestrictionService getRestrictionService() {
        return restrictionService;
    }

    public DataService getDataService() {
        return dataService;
    }

    public LoggingService getLoggingService() {
        return loggingService;
    }

    public ConfirmationService getConfirmationService() {
        return confirmationService;
    }

    public MessageCache getMessageCache() {
        return messageCache;
    }

    public UserCache getUserCache() {
        return userCache;
    }

    public VoiceChannelService getVoiceChannelService() {
        return voiceChannelService;
    }

    public VoiceChannelManager getVoiceChannelManager() {
        return voiceChannelManager;
    }

    public EventsSetupManager getEventsSetupManager() {
        return eventsSetupManager;
    }

    public void shutdown() {
        System.out.println("Shutting down TWBot...");

        if (muteService != null) {
            muteService.shutdown();
        }

        if (confirmationService != null) {
            confirmationService.shutdown();
        }

        if (roleRestorationService != null) {
            roleRestorationService.shutdown();
        }

        if (demotionSyncService != null) {
            demotionSyncService.shutdown();
        }

        if (appealScannerService != null) {
            appealScannerService.shutdown();
        }

        if (ownershipPingService != null) {
            ownershipPingService.shutdown();
        }

        if (jda != null) {
            jda.shutdown();
        }

        if (DatabaseManager.getInstance() != null) {
            DatabaseManager.getInstance().shutdown();
        }

        System.out.println("TWBot shutdown complete.");
    }

    public static void main(String[] args) {
        TWBot bot = new TWBot();

        Runtime.getRuntime().addShutdownHook(new Thread(bot::shutdown));

        bot.start();
    }
}
