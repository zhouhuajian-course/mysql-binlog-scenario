import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class BinlogClient {

    static Connection mysqlConn;
    static PreparedStatement stat;

    static {
        try {
            mysqlConn = DriverManager.getConnection("jdbc:mysql://192.168.1.201:3306?user=repl&password=repl");
            stat = mysqlConn.prepareStatement("REPLACE INTO bank.accounts_0? VALUES (?, ?, ?)");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        BinaryLogClient client = new BinaryLogClient(
                "192.168.1.201", 3306, "repl", "repl");
        client.setBinlogFilename("binlog.000040");
        client.setBinlogPosition(157);

        // 表信息缓存
        Map<Long, TableMapEventData> tableInfoCache = new HashMap<>();

        client.registerEventListener(new BinaryLogClient.EventListener() {

            @Override
            public void onEvent(Event event) {
                System.out.println(event);
                // 获取事件头部信息
                EventHeaderV4 header = event.getHeader();
                // 如果事件类型是 TABLE_MAP，则把表信息记录到缓存
                if (header.getEventType() == EventType.TABLE_MAP) {
                    TableMapEventData data = event.getData();
                    tableInfoCache.put(data.getTableId(), data);
                }
                // 如果事件类型是 EXT_WRITE_ROWS
                else if (header.getEventType() == EventType.EXT_WRITE_ROWS) {
                    WriteRowsEventData data = event.getData();
                    // 表ID 数据库名 表名
                    long tableId = data.getTableId();
                    String databaseName = tableInfoCache.get(tableId).getDatabase();
                    String tableName = tableInfoCache.get(tableId).getTable();
                    if (databaseName.equals("bank") && tableName.equals("accounts")) {
                        for (Serializable[] row : data.getRows()) {
                            int id = (int)row[0];
                            String name = (String)row[1];
                            BigDecimal money = (BigDecimal)row[2];
                            System.out.println(id);
                            System.out.println(name);
                            System.out.println(money);
                            try {
                                stat.setInt(1, id % 10);
                                stat.setInt(2, id);
                                stat.setString(3, name);
                                stat.setBigDecimal(4, money);
                                stat.executeUpdate();
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
                // 如果事件类型是 EXT_UPDATE_ROWS
                else if (header.getEventType() == EventType.EXT_UPDATE_ROWS) {
                    UpdateRowsEventData data = event.getData();
                    // 表ID 数据库名 表名
                    long tableId = data.getTableId();
                    String databaseName = tableInfoCache.get(tableId).getDatabase();
                    String tableName = tableInfoCache.get(tableId).getTable();
                    if (databaseName.equals("bank") && tableName.equals("transfer_logs")) {
                        // 向相关人员告警，告警方式可以是发送邮件、发送短信等等
                        System.out.println("警告：银行转账日志被人修改了，请相关人员核实相关情况。");
                    } else if (databaseName.equals("bank") && tableName.equals("accounts")) {
                        for (Map.Entry<Serializable[], Serializable[]> row : data.getRows()) {
                            Serializable[] rowAfter = row.getValue();
                            // 数据修改后的id name money
                            int id = (int)rowAfter[0];
                            String name = (String)rowAfter[1];
                            BigDecimal money = (BigDecimal)rowAfter[2];
                            System.out.println(id);
                            System.out.println(name);
                            System.out.println(money);
                            try {
                                stat.setInt(1, id % 10);
                                stat.setInt(2, id);
                                stat.setString(3, name);
                                stat.setBigDecimal(4, money);
                                stat.executeUpdate();
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                } else if (header.getEventType() == EventType.EXT_DELETE_ROWS) {
                    DeleteRowsEventData data = event.getData();
                    // 表ID 数据库名 表名
                    long tableId = data.getTableId();
                    String databaseName = tableInfoCache.get(tableId).getDatabase();
                    String tableName = tableInfoCache.get(tableId).getTable();
                    if (databaseName.equals("bank") && tableName.equals("transfer_logs")) {
                        // 向相关人员告警，告警方式可以是发送邮件、发送短信等等
                        System.out.println("警告：银行转账日志被人删除了，请相关人员核实相关情况。");
                    }
                }
            }
        });
        client.connect();
    }
}
