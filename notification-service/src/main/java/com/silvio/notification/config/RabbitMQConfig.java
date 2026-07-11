package com.silvio.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

// Configuración completa de RabbitMQ para Notification Service.
// Declara:
//   - Exchange tópico "notificacion.exchange" (recibe eventos de E-Lending)
//   - Cola principal "notificacion.queue" con Dead Letter Exchange
//   - DLX "notificacion.dlx" y DLQ "notificacion.queue.dlq"
//   - Reintentos: 3 intentos con backoff, luego mensaje → DLQ
//   - Conversor JSON para deserializar los eventos entrantes
@Configuration
public class RabbitMQConfig {

    // ─── Exchange ─────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange notificacionExchange() {
        return new TopicExchange("notificacion.exchange");
    }

    // ─── Cola principal con Dead Letter ───────────────────────────────────────

    @Bean
    public Queue notificacionQueue() {
        return QueueBuilder.durable("notificacion.queue")
                .deadLetterExchange("notificacion.dlx")
                .deadLetterRoutingKey("notificacion.prestamo.dlq")
                .build();
    }

    @Bean
    public Binding notificacionBinding() {
        return BindingBuilder
                .bind(notificacionQueue())
                .to(notificacionExchange())
                .with("notificacion.prestamo.*");
    }

    // ─── Dead Letter Queue ───────────────────────────────────────────────────

    @Bean
    public DirectExchange notificacionDlx() {
        return new DirectExchange("notificacion.dlx");
    }

    @Bean
    public Queue notificacionDlq() {
        return QueueBuilder.durable("notificacion.queue.dlq").build();
    }

    @Bean
    public Binding notificacionDlqBinding() {
        return BindingBuilder
                .bind(notificacionDlq())
                .to(notificacionDlx())
                .with("notificacion.prestamo.dlq");
    }

    // ─── Conversor JSON ──────────────────────────────────────────────────────
    // Configura TypePrecedence.INFERRED para que el consumidor use el tipo del
    // parámetro del @RabbitListener en lugar del header __TypeId__ del productor.
    // Esto es necesario porque el productor (elending-service) tiene la clase
    // NotificacionEvent en un paquete diferente al del consumidor.

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    // ─── Factory con reintentos y DLQ ─────────────────────────────────────────
    // 3 intentos: 2s → 4s → 8s (backoff multiplicativo)
    // Si se agotan los reintentos → RejectAndDontRequeueRecoverer
    //   → el mensaje va al DLX "notificacion.dlx" → cola "notificacion.queue.dlq"

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAutoStartup(autoStartup);  // false en tests (application-test.yml)

        RetryOperationsInterceptor retry = RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2000, 2.0, 10000)  // 2s, x2, max 10s
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
        factory.setAdviceChain(retry);

        return factory;
    }
}
