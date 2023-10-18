package binlogclientv2.handlers;

import binlogclientv2.Handler;
import binlogclientv2.RowData;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.io.Serializable;

public class SchoolArticlesHandler implements Handler {
    static class Article {
        int id;
        String content;
        public Article(int id, String content) {
            this.id = id;
            this.content = content;
        }
        public int getId() {
            return id;
        }
        public void setId(int id) {
            this.id = id;
        }
        public String getContent() {
            return content;
        }
        public void setContent(String content) {
            this.content = content;
        }
    }

    static ElasticsearchClient esClient;

    static {
        String serverUrl = "http://192.168.1.202:9200";
        RestClient restClient = RestClient
                .builder(HttpHost.create(serverUrl))
                .build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        esClient = new ElasticsearchClient(transport);
    }

    @Override
    public void handler(RowData rowData) {
        // 假设文章不做删除操作
        if (rowData.type.equals("insert") || rowData.type.equals("update")) {
            int id = (int) rowData.data.get("id");
            String content = (String) rowData.data.get("content");
            Article article = new Article(id, content);

            try {
                IndexResponse response = esClient.index(i -> i
                        .index("articles")  // 相当于mysql的表
                        .id(String.valueOf(id))
                        .document(article)  // 相当于mysql的行
                );
                System.out.println(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

