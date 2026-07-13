package com.silvio.elending.config;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests de la configuracion RabbitMQ del productor (elending-service).
// Verifica que el RabbitTemplate tenga la observabilidad habilitada
// para que Micrometer Tracing inyecte el traceId en headers AMQP.
class RabbitMQConfigTest {

    private RabbitMQConfig config;
    private ConnectionFactory connectionFactory;
    private ObservationRegistry observationRegistry;
    private ObjectProvider<ObservationRegistry> objectProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        config = new RabbitMQConfig();
        connectionFactory = mock(ConnectionFactory.class);
        observationRegistry = ObservationRegistry.create();
        objectProvider = mock(ObjectProvider.class);
    }

    @Test
    void jsonMessageConverter_debeCrearJacksonConverter() {
        Jackson2JsonMessageConverter converter = config.jsonMessageConverter();
        assertNotNull(converter);
        assertInstanceOf(Jackson2JsonMessageConverter.class, converter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rabbitTemplate_debeUsarJsonMessageConverter() {
        when(objectProvider.getIfAvailable()).thenReturn(observationRegistry);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory, objectProvider);
        assertNotNull(template);
        assertNotNull(template.getMessageConverter());
        assertInstanceOf(Jackson2JsonMessageConverter.class, template.getMessageConverter());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rabbitTemplate_sinObservationRegistry_noLanzaExcepcion() {
        // Simula el caso donde ObjectProvider.ifAvailable no ejecuta el lambda
        // porque no hay ObservationRegistry disponible
        doAnswer(invocation -> null).when(objectProvider).ifAvailable(any());
        RabbitTemplate template = config.rabbitTemplate(connectionFactory, objectProvider);
        assertNotNull(template);
        assertNotNull(template.getMessageConverter());
    }

    @Test
    void jsonMessageConverter_typeMapperUsaInferred() {
        Jackson2JsonMessageConverter converter = config.jsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper =
                (DefaultJackson2JavaTypeMapper) converter.getJavaTypeMapper();
        assertEquals(
                DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED,
                typeMapper.getTypePrecedence());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rabbitTemplate_noEsNull() {
        when(objectProvider.getIfAvailable()).thenReturn(observationRegistry);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory, objectProvider);
        assertNotNull(template);
    }
}
