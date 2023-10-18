package binlogclientv2.handlers;

import binlogclientv2.Handler;
import binlogclientv2.RowData;
import redis.clients.jedis.Jedis;


public class CompanyNewsHandler implements Handler {
    static Jedis jedis = new Jedis("192.168.1.202", 6379);
    @Override
    public void handler(RowData rowData) {
        // 如果是插入或更新，将数据缓存到redis一个月
        if (rowData.type.equals("insert") || rowData.type.equals("update")) {
            // 30 * 24 * 60 * 60 = 2592000
            jedis.setex("company:news:" + rowData.data.get("id"), 60, rowData.data.get("content").toString());
        }
        // 如果是删除，删除redis中对应的缓存
        else if (rowData.type.equals("delete")) {
            jedis.del("company:news:" + rowData.data.get("id"));
        }
    }
}
