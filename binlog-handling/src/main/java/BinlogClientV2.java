
import binlogclientv2.Handler;
import binlogclientv2.RowData;
import binlogclientv2.handlers.CompanyNewsHandler;
import binlogclientv2.handlers.CompanyVideosHandler;
import binlogclientv2.handlers.SchoolArticlesHandler;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;


import java.io.IOException;
import java.io.Serializable;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class BinlogClientV2 {

    static Connection mysqlConn;
    static PreparedStatement stat;
    // 表信息缓存
    static Map<Long, Map<String, Object>> tableInfoCache = new HashMap<>();
    // 处理器
    static Map<String, Handler> handlers = new HashMap<>();

    static {
        try {
            mysqlConn = DriverManager.getConnection("jdbc:mysql://192.168.1.201:3306?user=repl&password=repl");
            stat = mysqlConn.prepareStatement("SELECT * FROM information_schema.columns WHERE table_schema = ? and table_name = ? ORDER BY ordinal_position ASC");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        handlers.put("company.news", new CompanyNewsHandler());
        // handlers.put("company.videos", new CompanyVideosHandler());
        handlers.put("school.articles", new SchoolArticlesHandler());

    }

    public static void main(String[] args) throws IOException {
        BinaryLogClient client = new BinaryLogClient(
                "192.168.1.201", 3306, "repl", "repl");
        client.setBinlogFilename("binlog.000070");
        client.setBinlogPosition(1511);

        client.registerEventListener(new BinaryLogClient.EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(event);
                // 获取事件头部信息
                EventHeaderV4 header = event.getHeader();
                // 如果事件类型是 TABLE_MAP，则把表信息记录到缓存
                if (header.getEventType() == EventType.TABLE_MAP) {
                    TableMapEventData data = event.getData();
                    // 表ID、数据库名、表名
                    // 表ID是存在在内存中临时分配的ID，如果表结构修改，例如修改、增加、删除列，那么表ID会变化
                    Long tableId = data.getTableId();
                    String databaseName = data.getDatabase();
                    String tableName = data.getTable();

                    if (tableInfoCache.get(tableId) == null) {
                        // 根据数据库名、表名，获取该表所有列名
                        List<String> columns = getTableColumns(databaseName, tableName);
                        // 表信息
                        Map<String, Object> tableInfo = new HashMap<>();
                        tableInfo.put("database", data.getDatabase());
                        tableInfo.put("table", data.getTable());
                        tableInfo.put("columns", columns);
                        tableInfoCache.put(tableId, tableInfo);
                    }

                    System.out.println("表信息：" + tableInfoCache);
                }
                // 如果事件类型是 EXT_WRITE_ROWS
                else if (header.getEventType() == EventType.EXT_WRITE_ROWS) {
                    WriteRowsEventData data = event.getData();
                    Map<String, Object> tableInfo = tableInfoCache.get(data.getTableId());
                    if (tableInfo == null) {
                       return;
                    }
                    String databaseName = (String) tableInfo.get("database");
                    String tableName = (String) tableInfo.get("table");
                    List<String> columns = (ArrayList<String>) tableInfo.get("columns");
                    System.out.println(databaseName);
                    System.out.println(tableName);
                    System.out.println(columns);
                    for (Serializable[] serializables : data.getRows()) {
                        RowData rowData = new RowData();
                        rowData.type = "insert";
                        rowData.database = databaseName;
                        rowData.table = tableName;
                        rowData.data = new HashMap<>();
                        for (int i = 0; i < serializables.length; i++) {
                            rowData.data.put(columns.get(i), serializables[i]);
                        }
                        dispatchRowData(rowData);
                    }
                }
                // 如果事件类型是 EXT_UPDATE_ROWS
                else if (header.getEventType() == EventType.EXT_UPDATE_ROWS) {
                    UpdateRowsEventData data = event.getData();
                    Map<String, Object> tableInfo = tableInfoCache.get(data.getTableId());
                    if (tableInfo == null) {
                        return;
                    }
                    String databaseName = (String) tableInfo.get("database");
                    String tableName = (String) tableInfo.get("table");
                    List<String> columns = (ArrayList<String>) tableInfo.get("columns");

                    for (Map.Entry<Serializable[], Serializable[]> entry : data.getRows()) {
                        RowData rowData = new RowData();
                        rowData.type = "update";
                        rowData.database = databaseName;
                        rowData.table = tableName;
                        rowData.data = new HashMap<>();
                        rowData.old = new HashMap<>();
                        // entry.getKey() 修改前 数据数组
                        // entry.getValue() 修改后 数字数组
                        Serializable[] serializables = entry.getValue();
                        for (int i = 0; i < serializables.length; i++) {
                            rowData.data.put(columns.get(i), serializables[i]);
                        }
                        serializables = entry.getKey();
                        for (int i = 0; i < serializables.length; i++) {
                            rowData.old.put(columns.get(i), serializables[i]);
                        }

                        dispatchRowData(rowData);
                    }
                }
                // 如果事件类型是 EXT_DELETE_ROWS
                else if (header.getEventType() == EventType.EXT_DELETE_ROWS) {
                    DeleteRowsEventData data = event.getData();
                    Map<String, Object> tableInfo = tableInfoCache.get(data.getTableId());
                    if (tableInfo == null) {
                        return;
                    }
                    String databaseName = (String) tableInfo.get("database");
                    String tableName = (String) tableInfo.get("table");
                    List<String> columns = (ArrayList<String>) tableInfo.get("columns");

                    for (Serializable[] serializables : data.getRows()) {
                        RowData rowData = new RowData();
                        rowData.type = "delete";
                        rowData.database = databaseName;
                        rowData.table = tableName;
                        rowData.data = new HashMap<>();
                        for (int i = 0; i < serializables.length; i++) {
                            rowData.data.put(columns.get(i), serializables[i]);
                        }
                        dispatchRowData(rowData);
                    }
                }
            }
        });
        client.connect();
    }

    private static void dispatchRowData(RowData rowData) {
        System.out.println("行数据：" + rowData);
        Handler handler = handlers.get(rowData.database + "." + rowData.table);
        if (handler == null) {
            return;
        }
        handler.handler(rowData);
    }

    private static List<String> getTableColumns(String databaseName, String tableName) {
        List<String> columns = new ArrayList<>();
        try {
            stat.setString(1, databaseName);
            stat.setString(2, tableName);
            ResultSet resultSet = stat.executeQuery();
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return columns;
    }
}
