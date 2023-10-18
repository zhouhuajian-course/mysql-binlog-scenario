package binlogclientv2;

public interface Handler {
    void handler(RowData rowData);
}
