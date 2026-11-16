package com.backend.notificationservice.config;

import com.backend.notificationservice.messaging.NotificationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    private static final String NOTIFICATION_CHANNEL = "notifications";

    @Bean
    public MessageListenerAdapter messageListenerAdapter(NotificationListener notificationListener) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(notificationListener, "onMessage");
        // Publishers send plain JSON strings; the default JDK serializer would choke on them
        adapter.setSerializer(new StringRedisSerializer());
        return adapter;
    }

    @Bean
    public ChannelTopic topic() {
        return new ChannelTopic(NOTIFICATION_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListenerAdapter, topic());
        return container;
    }
}
