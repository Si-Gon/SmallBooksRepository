package com.silvio.elending.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Configuración RabbitMQ para el productor de eventos de notificación.
// Define el conversor JSON y el RabbitTemplate que usa NotificacionPublisher
// para enviar eventos al exchange declarado en notification-service.
@Configuration
public class RabbitMQConfig {

    // Conversor JSON para serializar/deserializar objetos como mensajes RabbitMQ
    // TypePrecedence.INFERRED evita que el header __TypeId__ contenga el FQCN del
    // productor, permitiendo que el consumidor use su propia clase NotificacionEvent.
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    // RabbitTemplate configurado con JSON — usado por NotificacionPublisher
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
