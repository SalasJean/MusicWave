package com.salas.musicwave.infrastructure.messaging;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DownloadMessagingTopology {

    public static final String EXCHANGE = "musicwave.downloads";
    public static final String DEAD_LETTER_EXCHANGE = "musicwave.downloads.dlx";
    public static final String EXECUTION_QUEUE = "musicwave.downloads.execute";
    public static final String DEAD_LETTER_QUEUE = "musicwave.downloads.execute.dlq";
    public static final String EXECUTE_ROUTING_KEY = "download.execute";
    public static final String DEAD_ROUTING_KEY = "download.execute.dead";

    @Bean
    DirectExchange downloadExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange downloadDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue downloadExecutionQueue() {
        return QueueBuilder.durable(EXECUTION_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", DEAD_ROUTING_KEY))
                .build();
    }

    @Bean
    Queue downloadDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding downloadExecutionBinding(Queue downloadExecutionQueue,
                                     DirectExchange downloadExchange) {
        return BindingBuilder.bind(downloadExecutionQueue)
                .to(downloadExchange)
                .with(EXECUTE_ROUTING_KEY);
    }

    @Bean
    Binding downloadDeadLetterBinding(Queue downloadDeadLetterQueue,
                                      DirectExchange downloadDeadLetterExchange) {
        return BindingBuilder.bind(downloadDeadLetterQueue)
                .to(downloadDeadLetterExchange)
                .with(DEAD_ROUTING_KEY);
    }
}
