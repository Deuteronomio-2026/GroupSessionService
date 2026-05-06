package com.mindbridge.group_session_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Bean
    public TopicExchange mindbridgeExchange() {
        return new TopicExchange(exchange);
    }

    @Bean public Queue sessionRequestedQueue() { return new Queue("session.requested.queue", true); }
    @Bean public Queue sessionApprovedQueue()  { return new Queue("session.approved.queue", true); }
    @Bean public Queue sessionEnrolledQueue()  { return new Queue("session.enrolled.queue", true); }
    @Bean public Queue sessionCancelledQueue() { return new Queue("session.cancelled.queue", true); }

    @Bean public Binding sessionRequestedBinding() { return BindingBuilder.bind(sessionRequestedQueue()).to(mindbridgeExchange()).with("session.requested"); }
    @Bean public Binding sessionApprovedBinding()  { return BindingBuilder.bind(sessionApprovedQueue()).to(mindbridgeExchange()).with("session.approved"); }
    @Bean public Binding sessionEnrolledBinding()  { return BindingBuilder.bind(sessionEnrolledQueue()).to(mindbridgeExchange()).with("session.enrolled"); }
    @Bean public Binding sessionCancelledBinding() { return BindingBuilder.bind(sessionCancelledQueue()).to(mindbridgeExchange()).with("session.cancelled"); }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}