package binlogclientv2;

import java.util.Map;

public class RowData {
    public String type;
    public String database;
    public String table;
    public Map<String, Object> data;
    public Map<String, Object> old;

    @Override
    public String toString() {
        return "RowData{" +
                "type='" + type + '\'' +
                ", database='" + database + '\'' +
                ", table='" + table + '\'' +
                ", data=" + data +
                ", old=" + old +
                '}';
    }
}
