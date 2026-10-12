import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles database connectivity for the Village Energy Coordinator.
 * Supports MySQL (e.g., via XAMPP localhost:3306) with automatic fallback
 * to embedded SQLite if MySQL server is not currently running.
 */
public class DatabaseManager {
    private static DatabaseManager instance;

    private String dbType = "UNKNOWN"; // "MySQL (XAMPP)" or "SQLite (Local Embedded)"
    private String connectionUrl = "";
    private String dbStatusMessage = "Initializing...";
    private boolean isConnected = false;

    // Default XAMPP MySQL credentials
    private static final String MYSQL_HOST = "localhost:3306";
    private static final String MYSQL_DB = "energy_coordinator";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASS = "";

    // Local SQLite fallback database file
    private static final String SQLITE_DB_FILE = "energy_coordinator.db";

    private DatabaseManager() {
        initDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public synchronized void initDatabase() {
        // First, attempt MySQL connection (Standard XAMPP default)
        boolean mysqlSuccess = tryConnectMySQL();

        if (!mysqlSuccess) {
            // Fallback to SQLite
            tryConnectSQLite();
        }

        if (isConnected) {
            createTables();
        }
    }

    public synchronized boolean tryConnectMySQL() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Connect to server to verify / create DB
            String serverUrl = "jdbc:mysql://" + MYSQL_HOST + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            try (Connection conn = DriverManager.getConnection(serverUrl, MYSQL_USER, MYSQL_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + MYSQL_DB);
            }

            connectionUrl = "jdbc:mysql://" + MYSQL_HOST + "/" + MYSQL_DB + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            try (Connection conn = DriverManager.getConnection(connectionUrl, MYSQL_USER, MYSQL_PASS)) {
                if (conn.isValid(3)) {
                    dbType = "MySQL (XAMPP localhost:3306)";
                    dbStatusMessage = "Connected to MySQL database '" + MYSQL_DB + "' via XAMPP";
                    isConnected = true;
                    createTables();
                    System.out.println("[DatabaseManager] Successfully connected to MySQL database: " + MYSQL_DB);
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("[DatabaseManager] MySQL not reachable (" + e.getMessage() + "). Will use SQLite.");
        }
        return false;
    }

    public synchronized boolean tryConnectSQLite() {
        try {
            Class.forName("org.sqlite.JDBC");
            connectionUrl = "jdbc:sqlite:" + SQLITE_DB_FILE;
            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                if (conn.isValid(3)) {
                    dbType = "SQLite (Local File)";
                    dbStatusMessage = "Connected to SQLite database file ('" + SQLITE_DB_FILE + "'). (Start MySQL in XAMPP anytime to switch)";
                    isConnected = true;
                    createTables();
                    System.out.println("[DatabaseManager] Successfully connected to SQLite database: " + SQLITE_DB_FILE);
                    return true;
                }
            }
        } catch (Exception e) {
            dbType = "NONE";
            dbStatusMessage = "Failed to connect to database: " + e.getMessage();
            isConnected = false;
            System.err.println("[DatabaseManager] Failed to connect to SQLite: " + e.getMessage());
        }
        return false;
    }

    public Connection getConnection() throws SQLException {
        if (dbType.startsWith("MySQL")) {
            return DriverManager.getConnection(connectionUrl, MYSQL_USER, MYSQL_PASS);
        } else {
            return DriverManager.getConnection(connectionUrl);
        }
    }

    private void createTables() {
        boolean isMySQL = dbType.startsWith("MySQL");
        String idDef = isMySQL ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";

        String createGenerationTable = "CREATE TABLE IF NOT EXISTS hourly_generation ("
                + "hour INT PRIMARY KEY,"
                + "generation_kwh DOUBLE,"
                + "updated_at VARCHAR(64)"
                + ")";

        String createLoadsTable = "CREATE TABLE IF NOT EXISTS consumer_loads ("
                + "id " + idDef + ","
                + "hour INT,"
                + "name VARCHAR(128),"
                + "load_type VARCHAR(64),"
                + "priority VARCHAR(32),"
                + "priority_level INT,"
                + "demand DOUBLE,"
                + "min_required DOUBLE,"
                + "allocated DOUBLE"
                + ")";

        String createResultsTable = "CREATE TABLE IF NOT EXISTS simulation_results ("
                + "hour INT PRIMARY KEY,"
                + "generation DOUBLE,"
                + "total_allocated DOUBLE,"
                + "unmet_demand DOUBLE,"
                + "curtailed_energy DOUBLE,"
                + "battery_charged DOUBLE DEFAULT 0.0,"
                + "battery_discharged DOUBLE DEFAULT 0.0,"
                + "battery_soc DOUBLE DEFAULT 0.0,"
                + "simulated_at VARCHAR(64)"
                + ")";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createGenerationTable);
            stmt.executeUpdate(createLoadsTable);
            stmt.executeUpdate(createResultsTable);

            // Migration safety: Ensure battery columns exist on existing databases
            try { stmt.executeUpdate("ALTER TABLE simulation_results ADD COLUMN battery_charged DOUBLE DEFAULT 0.0"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("ALTER TABLE simulation_results ADD COLUMN battery_discharged DOUBLE DEFAULT 0.0"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("ALTER TABLE simulation_results ADD COLUMN battery_soc DOUBLE DEFAULT 0.0"); } catch (Exception ignored) {}

            System.out.println("[DatabaseManager] Database tables verified / created successfully.");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error creating tables: " + e.getMessage());
        }
    }

    public synchronized void saveGeneration(int hour, double generation) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql;
        if (dbType.startsWith("MySQL")) {
            sql = "INSERT INTO hourly_generation (hour, generation_kwh, updated_at) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE generation_kwh = VALUES(generation_kwh), updated_at = VALUES(updated_at)";
        } else {
            sql = "INSERT OR REPLACE INTO hourly_generation (hour, generation_kwh, updated_at) VALUES (?, ?, ?)";
        }

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, hour);
            pstmt.setDouble(2, generation);
            pstmt.setString(3, now);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error saving generation: " + e.getMessage());
        }
    }

    public synchronized void saveLoad(int hour, ConsumerLoad load, String loadType, double minRequired) {
        String sql = "INSERT INTO consumer_loads (hour, name, load_type, priority, priority_level, demand, min_required, allocated) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, hour);
            pstmt.setString(2, load.getName());
            pstmt.setString(3, loadType);
            pstmt.setString(4, load.getPriority().name());
            pstmt.setInt(5, load.getPriority().getLevel());
            pstmt.setDouble(6, load.getDemand());
            pstmt.setDouble(7, minRequired);
            pstmt.setDouble(8, load.getAllocatedEnergy());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error saving load: " + e.getMessage());
        }
    }

    public synchronized void updateLoadAllocations(int hour, List<ConsumerLoad> loads) {
        // Update allocated column for loads of this hour
        String sql = "UPDATE consumer_loads SET allocated = ? WHERE hour = ? AND name = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (ConsumerLoad load : loads) {
                pstmt.setDouble(1, load.getAllocatedEnergy());
                pstmt.setInt(2, hour);
                pstmt.setString(3, load.getName());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error updating load allocations: " + e.getMessage());
        }
    }

    public synchronized void saveSimulationResult(IntervalAllocation result) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql;
        if (dbType.startsWith("MySQL")) {
            sql = "INSERT INTO simulation_results (hour, generation, total_allocated, unmet_demand, curtailed_energy, battery_charged, battery_discharged, battery_soc, simulated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE generation = VALUES(generation), total_allocated = VALUES(total_allocated), "
                + "unmet_demand = VALUES(unmet_demand), curtailed_energy = VALUES(curtailed_energy), "
                + "battery_charged = VALUES(battery_charged), battery_discharged = VALUES(battery_discharged), "
                + "battery_soc = VALUES(battery_soc), simulated_at = VALUES(simulated_at)";
        } else {
            sql = "INSERT OR REPLACE INTO simulation_results (hour, generation, total_allocated, unmet_demand, curtailed_energy, battery_charged, battery_discharged, battery_soc, simulated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, result.getHour());
            pstmt.setDouble(2, result.getTotalGeneration());
            pstmt.setDouble(3, result.getTotalAllocated());
            pstmt.setDouble(4, result.getUnmetDemand());
            pstmt.setDouble(5, result.getCurtailedEnergy());
            pstmt.setDouble(6, result.getBatteryCharged());
            pstmt.setDouble(7, result.getBatteryDischarged());
            pstmt.setDouble(8, result.getBatterySoC());
            pstmt.setString(9, now);
            pstmt.executeUpdate();

            // Also update allocations in consumer_loads table
            updateLoadAllocations(result.getHour(), result.getLoadsSnapshot());
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error saving simulation result: " + e.getMessage());
        }
    }

    public synchronized void clearDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM simulation_results");
            stmt.executeUpdate("DELETE FROM consumer_loads");
            stmt.executeUpdate("DELETE FROM hourly_generation");
            System.out.println("[DatabaseManager] All tables cleared.");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error clearing database: " + e.getMessage());
        }
    }

    public synchronized Map<String, Object> getDatabaseInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("dbType", dbType);
        info.put("statusMessage", dbStatusMessage);
        info.put("isConnected", isConnected);
        
        int genCount = 0;
        int loadCount = 0;
        int resCount = 0;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM hourly_generation");
            if (rs.next()) genCount = rs.getInt(1);

            rs = stmt.executeQuery("SELECT COUNT(*) FROM consumer_loads");
            if (rs.next()) loadCount = rs.getInt(1);

            rs = stmt.executeQuery("SELECT COUNT(*) FROM simulation_results");
            if (rs.next()) resCount = rs.getInt(1);
        } catch (Exception e) {
            // Ignore error if not ready
        }

        info.put("generationRows", genCount);
        info.put("loadRows", loadCount);
        info.put("resultRows", resCount);
        return info;
    }

    public synchronized List<Map<String, Object>> getTableRecords(String tableName) {
        List<Map<String, Object>> records = new ArrayList<>();
        // Only allow known table names to prevent injection
        if (!tableName.equals("hourly_generation") && !tableName.equals("consumer_loads") && !tableName.equals("simulation_results")) {
            return records;
        }

        String sql = "SELECT * FROM " + tableName + (tableName.equals("consumer_loads") ? " ORDER BY hour, priority_level DESC" : " ORDER BY hour ASC");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            java.sql.ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    String colName = meta.getColumnName(i);
                    Object val = rs.getObject(i);
                    row.put(colName, val);
                }
                records.add(row);
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error fetching records from " + tableName + ": " + e.getMessage());
        }
        return records;
    }

    public String getDbType() {
        return dbType;
    }

    public String getDbStatusMessage() {
        return dbStatusMessage;
    }

    public boolean isConnected() {
        return isConnected;
    }
}
