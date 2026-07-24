package TWBot.database;

import TWBot.config.BotConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class DatabaseManager {
    private static final long PERIODIC_BACKUP_HOURS = 6;

    private static volatile DatabaseManager instance;
    private final BotConfig config;
    private final String jdbcUrl;
    private final Path dbPath;
    private final Path backupDir;
    private final ScheduledExecutorService backupScheduler;
    private Connection sharedConnection;

    private DatabaseManager() {
        this.config = new BotConfig();

        String dbUrl = config.getEnvOrDefault("DATABASE_URL", null);
        Path resolvedDbPath;

        if (dbUrl == null || dbUrl.isEmpty()) {
            String path = config.getEnvOrDefault("DATABASE_PATH", "tw-bot.db");
            resolvedDbPath = Path.of(path).toAbsolutePath().normalize();
            dbUrl = "jdbc:sqlite:" + resolvedDbPath;
        } else if (dbUrl.startsWith("jdbc:sqlite:")) {
            String rawPath = dbUrl.substring("jdbc:sqlite:".length());
            if (rawPath.startsWith("//")) {
                rawPath = rawPath.substring(2);
            }
            resolvedDbPath = Path.of(rawPath).toAbsolutePath().normalize();
            dbUrl = "jdbc:sqlite:" + resolvedDbPath;
        } else {
            resolvedDbPath = null;
        }

        this.jdbcUrl = dbUrl;
        this.dbPath = resolvedDbPath;
        this.backupDir = resolvedDbPath != null
                ? resolvedDbPath.getParent().resolve("backups")
                : Path.of("backups").toAbsolutePath().normalize();
        this.backupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-backup");
            t.setDaemon(true);
            return t;
        });

        System.out.println("Using SQLite DB: " + jdbcUrl);

        restoreFromLatestBackupIfMissing();

        try {
            this.sharedConnection = createNewConnection();
        } catch (SQLException e) {
            System.err.println("CRITICAL: Failed to create initial database connection: " + e.getMessage());
        }
    }

    private Connection createNewConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout = 10000;"); // Increase to 10s for stability
            stmt.execute("PRAGMA journal_mode = DELETE;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        }
        return conn;
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                if (instance == null) {
                    instance = new DatabaseManager();
                }
            }
        }
        return instance;
    }

    /**
     * Returns a wrapper around the shared connection that ignores close() calls.
     * This allows multiple threads to use try-with-resources safely with a single connection.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (sharedConnection == null || sharedConnection.isClosed()) {
            sharedConnection = createNewConnection();
        }
        return new NoCloseConnection(sharedConnection);
    }

    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            System.err.println("SQLite connection failed: " + e.getMessage());
            return false;
        }
    }

    public Path getDbPath() {
        return dbPath;
    }

    public Path getBackupDir() {
        return backupDir;
    }

    public void startPeriodicBackups() {
        if (dbPath == null) {
            System.out.println("[DatabaseBackup] Skipping periodic backups (non-file database).");
            return;
        }

        try {
            Path initial = createBackup();
            System.out.println("[DatabaseBackup] Startup backup created: " + initial);
        } catch (Exception e) {
            System.err.println("[DatabaseBackup] Startup backup failed: " + e.getMessage());
        }

        backupScheduler.scheduleAtFixedRate(() -> {
            try {
                Path backup = createBackup();
                System.out.println("[DatabaseBackup] Periodic backup created: " + backup);
            } catch (Exception e) {
                System.err.println("[DatabaseBackup] Periodic backup failed: " + e.getMessage());
            }
        }, PERIODIC_BACKUP_HOURS, PERIODIC_BACKUP_HOURS, TimeUnit.HOURS);
        System.out.println("[DatabaseBackup] Periodic backups scheduled every " + PERIODIC_BACKUP_HOURS + " hours.");
    }

    /**
     * Creates a consistent SQLite snapshot, overwriting the single backup file.
     */
    public synchronized Path createBackup() throws SQLException, IOException {
        if (dbPath == null) {
            throw new SQLException("Backups are only supported for SQLite file databases.");
        }
        if (sharedConnection == null || sharedConnection.isClosed()) {
            sharedConnection = createNewConnection();
        }

        Files.createDirectories(backupDir);

        Path latestBackup = getLatestBackupPath();
        Path tempBackup = backupDir.resolve(dbPath.getFileName().toString().replace(".db", "") + ".backup.tmp");

        Files.deleteIfExists(tempBackup);

        String sqlitePath = tempBackup.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
        try (Statement stmt = sharedConnection.createStatement()) {
            stmt.execute("VACUUM INTO '" + sqlitePath + "'");
        }

        Files.move(tempBackup, latestBackup, StandardCopyOption.REPLACE_EXISTING);
        pruneExtraBackups(latestBackup);

        System.out.println("[DatabaseBackup] Saved backup to " + latestBackup);
        return latestBackup;
    }

    private Path getLatestBackupPath() {
        return backupDir.resolve(dbPath.getFileName().toString().replace(".db", "") + ".latest.db");
    }

    private void restoreFromLatestBackupIfMissing() {
        if (dbPath == null) {
            return;
        }
        try {
            if (Files.exists(dbPath) && Files.size(dbPath) > 0) {
                return;
            }
            Path latestBackup = getLatestBackupPath();
            if (!Files.exists(latestBackup) || Files.size(latestBackup) == 0) {
                return;
            }
            Files.createDirectories(dbPath.getParent());
            Files.copy(latestBackup, dbPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[DatabaseBackup] Restored missing database from " + latestBackup);
        } catch (IOException e) {
            System.err.println("[DatabaseBackup] Failed to restore from backup: " + e.getMessage());
        }
    }

    /** Deletes any leftover timestamped backups so only the single latest file remains. */
    private void pruneExtraBackups(Path keep) throws IOException {
        if (!Files.isDirectory(backupDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(backupDir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                if (!path.equals(keep)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    public void shutdown() {
        backupScheduler.shutdownNow();

        try {
            if (dbPath != null && sharedConnection != null && !sharedConnection.isClosed()) {
                createBackup();
                System.out.println("[DatabaseBackup] Shutdown backup complete.");
            }
        } catch (Exception e) {
            System.err.println("[DatabaseBackup] Shutdown backup failed: " + e.getMessage());
        }

        try {
            if (sharedConnection != null && !sharedConnection.isClosed()) {
                sharedConnection.close();
                System.out.println("✅ Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }

    /**
     * Inner class to prevent users of the connection from closing the shared handle.
     */
    private static class NoCloseConnection implements Connection {
        private final Connection inner;

        public NoCloseConnection(Connection inner) {
            this.inner = inner;
        }

        @Override public void close() throws SQLException { /* DO NOTHING */ }
        @Override public boolean isClosed() throws SQLException { return inner.isClosed(); }
        
        // Delegate all other methods
        @Override public Statement createStatement() throws SQLException { return inner.createStatement(); }
        @Override public PreparedStatement prepareStatement(String sql) throws SQLException { return inner.prepareStatement(sql); }
        @Override public CallableStatement prepareCall(String sql) throws SQLException { return inner.prepareCall(sql); }
        @Override public String nativeSQL(String sql) throws SQLException { return inner.nativeSQL(sql); }
        @Override public void setAutoCommit(boolean autoCommit) throws SQLException { inner.setAutoCommit(autoCommit); }
        @Override public boolean getAutoCommit() throws SQLException { return inner.getAutoCommit(); }
        @Override public void commit() throws SQLException { inner.commit(); }
        @Override public void rollback() throws SQLException { inner.rollback(); }
        @Override public DatabaseMetaData getMetaData() throws SQLException { return inner.getMetaData(); }
        @Override public void setReadOnly(boolean readOnly) throws SQLException { inner.setReadOnly(readOnly); }
        @Override public boolean isReadOnly() throws SQLException { return inner.isReadOnly(); }
        @Override public void setCatalog(String catalog) throws SQLException { inner.setCatalog(catalog); }
        @Override public String getCatalog() throws SQLException { return inner.getCatalog(); }
        @Override public void setTransactionIsolation(int level) throws SQLException { inner.setTransactionIsolation(level); }
        @Override public int getTransactionIsolation() throws SQLException { return inner.getTransactionIsolation(); }
        @Override public SQLWarning getWarnings() throws SQLException { return inner.getWarnings(); }
        @Override public void clearWarnings() throws SQLException { inner.clearWarnings(); }
        @Override public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException { return inner.createStatement(resultSetType, resultSetConcurrency); }
        @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return inner.prepareStatement(sql, resultSetType, resultSetConcurrency); }
        @Override public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return inner.prepareCall(sql, resultSetType, resultSetConcurrency); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() throws SQLException { return inner.getTypeMap(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException { inner.setTypeMap(map); }
        @Override public void setHoldability(int holdability) throws SQLException { inner.setHoldability(holdability); }
        @Override public int getHoldability() throws SQLException { return inner.getHoldability(); }
        @Override public Savepoint setSavepoint() throws SQLException { return inner.setSavepoint(); }
        @Override public Savepoint setSavepoint(String name) throws SQLException { return inner.setSavepoint(name); }
        @Override public void rollback(Savepoint savepoint) throws SQLException { inner.rollback(savepoint); }
        @Override public void releaseSavepoint(Savepoint savepoint) throws SQLException { inner.releaseSavepoint(savepoint); }
        @Override public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return inner.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return inner.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return inner.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException { return inner.prepareStatement(sql, autoGeneratedKeys); }
        @Override public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException { return inner.prepareStatement(sql, columnIndexes); }
        @Override public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException { return inner.prepareStatement(sql, columnNames); }
        @Override public Clob createClob() throws SQLException { return inner.createClob(); }
        @Override public Blob createBlob() throws SQLException { return inner.createBlob(); }
        @Override public NClob createNClob() throws SQLException { return inner.createNClob(); }
        @Override public SQLXML createSQLXML() throws SQLException { return inner.createSQLXML(); }
        @Override public boolean isValid(int timeout) throws SQLException { return inner.isValid(timeout); }
        @Override public void setClientInfo(String name, String value) throws SQLClientInfoException { inner.setClientInfo(name, value); }
        @Override public void setClientInfo(Properties properties) throws SQLClientInfoException { inner.setClientInfo(properties); }
        @Override public String getClientInfo(String name) throws SQLException { return inner.getClientInfo(name); }
        @Override public Properties getClientInfo() throws SQLException { return inner.getClientInfo(); }
        @Override public Array createArrayOf(String typeName, Object[] elements) throws SQLException { return inner.createArrayOf(typeName, elements); }
        @Override public Struct createStruct(String typeName, Object[] attributes) throws SQLException { return inner.createStruct(typeName, attributes); }
        @Override public void setSchema(String schema) throws SQLException { inner.setSchema(schema); }
        @Override public String getSchema() throws SQLException { return inner.getSchema(); }
        @Override public void abort(Executor executor) throws SQLException { inner.abort(executor); }
        @Override public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException { inner.setNetworkTimeout(executor, milliseconds); }
        @Override public int getNetworkTimeout() throws SQLException { return inner.getNetworkTimeout(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return inner.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return inner.isWrapperFor(iface); }
    }
}
