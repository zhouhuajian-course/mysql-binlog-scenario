package util;

import java.math.BigDecimal;
import java.sql.*;

public class BankAccountDataSync {
    public static void main(String[] args) throws Exception {
        Connection mysqlConn = DriverManager.getConnection("jdbc:mysql://192.168.1.201:3306?user=repl&password=repl");
        // 查询语句
        PreparedStatement stat = mysqlConn.prepareStatement("SELECT * FROM bank.accounts WHERE id > ? ORDER BY id ASC LIMIT 1000");
        // 替换语句
        PreparedStatement stat2 = mysqlConn.prepareStatement("REPLACE INTO bank.accounts_0? VALUES (?, ?, ?)");
        int maxId = 0;
        while (true) {
            // 设置查询时的最大用户ID
            stat.setInt(1, maxId);
            ResultSet resultSet = stat.executeQuery();
            boolean isEmpty = true;
            while (resultSet.next()) {
                isEmpty = false;
                // 账户 id name money
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                BigDecimal money = resultSet.getBigDecimal("money");
                System.out.println("正在同步数据：id = " + id);
                // 更新 最大用户ID
                maxId = id;
                // REPLACE INTO 插入到新表
                stat2.setInt(1, id % 10);
                stat2.setInt(2, id);
                stat2.setString(3, name);
                stat2.setBigDecimal(4, money);
                stat2.executeUpdate();
            }
            if (isEmpty) {
                break;
            }
        }
    }
}