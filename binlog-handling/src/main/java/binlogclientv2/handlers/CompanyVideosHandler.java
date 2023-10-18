package binlogclientv2.handlers;

import binlogclientv2.Handler;
import binlogclientv2.RowData;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientConfigurationBuilder;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;

public class CompanyVideosHandler implements Handler {
    static ClientServiceProvider provider;
    static Producer producer;
    static String topic1 = "CONVERT_VIDEO_TO_1080P";
    static String topic2 = "CONVERT_VIDEO_TO_720P";

    static {
        String endpoint = "192.168.1.202:8081";
        provider = ClientServiceProvider.loadService();
        ClientConfigurationBuilder builder = ClientConfiguration.newBuilder().setEndpoints(endpoint);
        builder.enableSsl(false);
        ClientConfiguration configuration = builder.build();
        try {
            producer = provider.newProducerBuilder()
                    .setTopics(topic1, topic2)
                    .setClientConfiguration(configuration)
                    .build();
        } catch (ClientException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handler(RowData rowData) {
        // 如果上传了新视频，那么需要离线转码成1080P和720P的视频，向MQ两个队列分别发送消息
        if (rowData.type.equals("insert")) {
            String videoId = rowData.data.get("id").toString();
            Message message1 = provider.newMessageBuilder()
                    .setTopic(topic1)
                    .setBody(videoId.getBytes())
                    .build();
            Message message2 = provider.newMessageBuilder()
                    .setTopic(topic2)
                    .setBody(videoId.getBytes())
                    .build();
            try {
                SendReceipt sendReceipt1 = producer.send(message1);
                SendReceipt sendReceipt2 = producer.send(message2);
                System.out.println("发送结果：" + sendReceipt1);
                System.out.println("发送结果：" + sendReceipt2);
            } catch (ClientException e) {
            }
        }
    }
}
