package net.twilightmc.skReflectabase;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DatabaseManager {
    private final Plugin plugin;
    private Connection connection;
    private final String url;
    private final String username;
    private final String password;

    public DatabaseManager(Plugin plugin, String url, String username, String password) throws SQLException {
        this.plugin = plugin;
        this.url = url;
        this.username = username;
        this.password = password;
        connect();
    }

    private synchronized void connect() throws SQLException {
        if (connection != null) {
            try {
                if (!connection.isClosed() && connection.isValid(1)) {
                    return;
                }
            } catch (SQLException e) {
                // Connection is invalid, continue to create new one
            }
            try {
                connection.close();
            } catch (SQLException ignored) {}
        }
        connection = DriverManager.getConnection(url, username, password);
        connection.setAutoCommit(true);
    }

    public void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
        }
    }

    // Sync Methods with improved resource management

    public QueryResult executeQuery(String query) throws SQLException {
        return executeQuery(query, new Object[0]);
    }

    public QueryResult executeQuery(String query, Object... params) throws SQLException {
        connect();
        try (PreparedStatement statement = prepareStatement(query, params)) {
            ResultSet rs = statement.executeQuery();
            return new QueryResult(rs);
        }
    }

    public int executeUpdate(String query) throws SQLException {
        return executeUpdate(query, new Object[0]);
    }

    public int executeUpdate(String query, Object... params) throws SQLException {
        connect();
        try (PreparedStatement statement = prepareStatement(query, params)) {
            return statement.executeUpdate();
        }
    }

    // Async Methods with improved error handling

    public CompletableFuture<QueryResult> executeQueryAsync(String query) {
        return executeQueryAsync(query, new Object[0]);
    }

    public CompletableFuture<QueryResult> executeQueryAsync(String query, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeQuery(query, params);
            } catch (SQLException e) {
                throw new RuntimeException("Error executing async query: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<Integer> executeUpdateAsync(String query) {
        return executeUpdateAsync(query, new Object[0]);
    }

    public CompletableFuture<Integer> executeUpdateAsync(String query, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeUpdate(query, params);
            } catch (SQLException e) {
                throw new RuntimeException("Error executing async update: " + e.getMessage(), e);
            }
        });
    }

    // Callback-based Async Methods with improved safety

    public void executeQueryAsync(String query, Consumer<QueryResult> callback) {
        executeQueryAsync(query, callback, new Object[0]);
    }

    public void executeQueryAsync(String query, Consumer<QueryResult> callback, Object... params) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                QueryResult result = executeQuery(query, params);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
            } catch (SQLException e) {
                plugin.getLogger().severe("Error in async query: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            }
        });
    }

    public void executeUpdateAsync(String query, Consumer<Integer> callback) {
        executeUpdateAsync(query, callback, new Object[0]);
    }

    public void executeUpdateAsync(String query, Consumer<Integer> callback, Object... params) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int result = executeUpdate(query, params);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
            } catch (SQLException e) {
                plugin.getLogger().severe("Error in async update: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(-1));
            }
        });
    }

    // Utility Methods with improved error handling

    public boolean tableExists(String tableName) throws SQLException {
        connect();
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    public CompletableFuture<Boolean> tableExistsAsync(String tableName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return tableExists(tableName);
            } catch (SQLException e) {
                throw new RuntimeException("Error checking table existence: " + e.getMessage(), e);
            }
        });
    }

    public List<String> getTableColumns(String tableName) throws SQLException {
        connect();
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    public CompletableFuture<List<String>> getTableColumnsAsync(String tableName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getTableColumns(tableName);
            } catch (SQLException e) {
                throw new RuntimeException("Error getting table columns: " + e.getMessage(), e);
            }
        });
    }

    private PreparedStatement prepareStatement(String query, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }

    // Connection getter with validation
    public synchronized Connection getConnection() throws SQLException {
        connect();
        return connection;
    }

    // QueryResult class to safely handle ResultSet data
    public static class QueryResult {
        private final List<Map<String, Object>> rows;
        private final List<String> columnNames;

        public QueryResult(ResultSet rs) throws SQLException {
            this.rows = new ArrayList<>();
            this.columnNames = new ArrayList<>();

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Store column names
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(metaData.getColumnName(i));
            }

            // Store all rows
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (String columnName : columnNames) {
                    row.put(columnName, rs.getObject(columnName));
                }
                rows.add(row);
            }
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public List<String> getColumnNames() {
            return columnNames;
        }

        public Object getValue(int rowIndex, String columnName) {
            if (rowIndex >= 0 && rowIndex < rows.size()) {
                return rows.get(rowIndex).get(columnName);
            }
            return null;
        }

        public int getRowCount() {
            return rows.size();
        }
    }
}