package org.example.workqueue;

import com.rabbitmq.client.Channel;
import org.example.util.RabbitMQUtils;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

/**
 * @author zail
 * @date 2022/6/28
 */
public class Producer {
    
    private static final String QUEUE_NAME = "hello";
    
    public static void main(String[] args) {
        try (Channel channel = RabbitMQUtils.getChannel()) {
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            // 从控制台当中接受信息
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNext()) {
                String message = scanner.next();
                channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
                System.out.println("发送消息完成:" + message);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
