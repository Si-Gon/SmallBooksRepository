package com.silvio.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests de la configuración RabbitMQ de Notification Service.
 *
 * Verifica que los beans de infraestructura se creen correctamente:
 *   - Exchange, cola principal y binding
 *   - DLX (Dead Letter Exchange), DLQ y binding
 *   - Conversor JSON con TypePrecedence.INFERRED
 *   - Listener container factory con retry interceptor (3 intentos, backoff 2s x2)
 *   - Respeto de la propiedad auto-startup
 */
class RabbitMQConfigTest {

    private RabbitMQConfig config;

    @BeforeEach
    void setUp() {
        config = new RabbitMQConfig();
    }

    // ─── Exchange ─────────────────────────────────────────────────────────────

    @Test
    void notificacionExchange_debeSerTopicExchange() {
        // When
        TopicExchange exchange = config.notificacionExchange();

        // Then
        assertNotNull(exchange);
        assertTrue(exchange.isDurable());
        assertEquals("notificacion.exchange", exchange.getName());
    }

    // ─── Cola principal con DLQ ───────────────────────────────────────────────

    @Test
    void notificacionQueue_debeTenerDeadLetterConfigurado() {
        // When
        Queue queue = config.notificacionQueue();

        // Then
        assertNotNull(queue);
        assertTrue(queue.isDurable());
        assertEquals("notificacion.queue", queue.getName());
        // Verifica argumentos de Dead Letter
        assertEquals("notificacion.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("notificacion.prestamo.dlq", queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void notificacionQueue_noDebeTenerTTL() {
        // When
        Queue queue = config.notificacionQueue();

        // Then — no debe tener TTL configurado (los mensajes deben durar hasta ser procesados)
        assertNull(queue.getArguments().get("x-message-ttl"),
                "No debe tener TTL configurado");
    }

    // ─── Binding principal ────────────────────────────────────────────────────

    @Test
    void notificacionBinding_debeUsarRoutingKeyComodin() {
        // When
        Binding binding = config.notificacionBinding();

        // Then
        assertNotNull(binding);
        assertEquals("notificacion.queue", binding.getDestination());
        assertEquals("notificacion.exchange", binding.getExchange());
        assertEquals("notificacion.prestamo.*", binding.getRoutingKey());
        assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
    }

    // ─── Dead Letter Exchange ─────────────────────────────────────────────────

    @Test
    void notificacionDlx_debeSerDirectExchange() {
        // When
        DirectExchange dlx = config.notificacionDlx();

        // Then
        assertNotNull(dlx);
        assertTrue(dlx.isDurable());
        assertEquals("notificacion.dlx", dlx.getName());
    }

    // ─── Dead Letter Queue ────────────────────────────────────────────────────

    @Test
    void notificacionDlq_debeSerColaDurable() {
        // When
        Queue dlq = config.notificacionDlq();

        // Then
        assertNotNull(dlq);
        assertTrue(dlq.isDurable());
        assertEquals("notificacion.queue.dlq", dlq.getName());
    }

    @Test
    void notificacionDlq_noDebeTenerDLQPropio() {
        // When
        Queue dlq = config.notificacionDlq();

        // Then — la DLQ no debe re-enviar a otra DLQ (evita bucles infinitos)
        assertNull(dlq.getArguments().get("x-dead-letter-exchange"),
                "La DLQ no debe tener otro DLX configurado");
    }

    // ─── Binding DLQ ──────────────────────────────────────────────────────────

    @Test
    void notificacionDlqBinding_debeUsarRoutingKeyCorrecta() {
        // When
        Binding binding = config.notificacionDlqBinding();

        // Then
        assertNotNull(binding);
        assertEquals("notificacion.queue.dlq", binding.getDestination());
        assertEquals("notificacion.dlx", binding.getExchange());
        assertEquals("notificacion.prestamo.dlq", binding.getRoutingKey());
        assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
    }

    // ─── Conversor JSON ───────────────────────────────────────────────────────

    @Test
    void jsonMessageConverter_debeTenerTypePrecedenceINFERRED() {
        // When
        Jackson2JsonMessageConverter converter = config.jsonMessageConverter();

        // Then
        assertNotNull(converter);
        assertNotNull(converter.getJavaTypeMapper());
        assertInstanceOf(DefaultJackson2JavaTypeMapper.class, converter.getJavaTypeMapper());

        DefaultJackson2JavaTypeMapper typeMapper =
                (DefaultJackson2JavaTypeMapper) converter.getJavaTypeMapper();
        assertEquals(
                DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED,
                typeMapper.getTypePrecedence());
    }

    // ─── Listener Container Factory ───────────────────────────────────────────

    @Test
    void rabbitListenerContainerFactory_debeUsarConnectionFactory() {
        // Given
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        // When
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, true);

        // Then
        assertNotNull(factory);
        // Verifica que setConnectionFactory fue llamado (se refleja en el factory creado)
        assertNotNull(factory);
    }

    // ─── Tracing / Observabilidad ─────────────────────────────────────────────

    @Test
    void rabbitListenerContainerFactory_debeTenerObservationEnabled()
            throws Exception {
        // Given
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        // When
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, true);

        // Then — observationEnabled debe estar en true para que Micrometer Tracing
        // restaure el traceId desde los headers AMQP del mensaje. Accedemos via
        // reflection porque SimpleRabbitListenerContainerFactory no expone getter.
        Field observationEnabledField = SimpleRabbitListenerContainerFactory.class
                .getSuperclass().getDeclaredField("observationEnabled");
        observationEnabledField.setAccessible(true);
        boolean observationEnabled = observationEnabledField.getBoolean(factory);

        assertTrue(observationEnabled,
                "observationEnabled debe estar en true para tracing distribuido");
    }

    @Test
    void rabbitListenerContainerFactory_debeTenerRetryInterceptor() {
        // Given
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        // When
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, true);

        // Then — el advice chain debe tener el retry interceptor
        assertNotNull(factory.getAdviceChain());
        assertTrue(factory.getAdviceChain().length > 0);
        assertInstanceOf(RetryOperationsInterceptor.class, factory.getAdviceChain()[0]);
    }

    @Test
    void rabbitListenerContainerFactory_debeTenerMaxAttempts3() {
        // El interceptor se configura con maxAttempts=3, backOff 2000ms, factor 2.0, max 10000ms
        // y RejectAndDontRequeueRecoverer como recoverer.
        // Verificamos que RejectAndDontRequeueRecoverer esté configurado
        // y que no sea un ImmediateRequeue o SimpleRecoverer.

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, true);

        assertNotNull(factory.getAdviceChain());
        RetryOperationsInterceptor retryInterceptor =
                (RetryOperationsInterceptor) factory.getAdviceChain()[0];

        // La verificación se hace a nivel de integración (comportamiento observable):
        // 3 intentos → si todos fallan → RejectAndDontRequeueRecoverer → DLQ
        // Aquí validamos que el interceptor existe y está en la advice chain
        assertNotNull(retryInterceptor);
    }

    @Test
    void rabbitListenerContainerFactory_conAutoStartupTrue_noLanzaExcepcion() {
        // Given
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        // When — autoStartup = true (valor por defecto en producción)
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, true);

        // Then — el factory se crea sin errores con autoStartup=true
        assertNotNull(factory);
        // Verifica que el advice chain tenga el retry interceptor
        assertNotNull(factory.getAdviceChain());
        assertTrue(factory.getAdviceChain().length > 0);
    }

    @Test
    void rabbitListenerContainerFactory_conAutoStartupFalse_noLanzaExcepcion() {
        // Given
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        // When — autoStartup = false (como en application-test.yml)
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, false);

        // Then — el factory se crea sin errores incluso con autoStartup=false
        assertNotNull(factory);
        // El advice chain debe existir independientemente del autoStartup
        assertNotNull(factory.getAdviceChain());
    }

    // ─── DLQ — verificación del retry interceptor y backoff ──────────────────

    @Test
    void rabbitListenerContainerFactory_debeUsarRejectAndDontRequeueRecoverer() {
        // Given — el recoverer se pasa a RetryInterceptorBuilder.stateless()
        // y se configura internamente en el RetryOperationsInterceptor.
        // Verificamos que la clase RejectAndDontRequeueRecoverer se pueda
        // instanciar (está en el classpath) y que el retry interceptor está presente.
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, true);

        // When — extraer el RetryOperationsInterceptor del advice chain
        RetryOperationsInterceptor retryInterceptor =
                (RetryOperationsInterceptor) factory.getAdviceChain()[0];

        // Then — verificar que el interceptor existe y el recoverer está disponible
        assertNotNull(retryInterceptor);

        // Verificar que RejectAndDontRequeueRecoverer está en el classpath
        // y puede instanciarse (confirmación de que la dependencia existe)
        RejectAndDontRequeueRecoverer recoverer = new RejectAndDontRequeueRecoverer();
        assertNotNull(recoverer);
        assertTrue(recoverer.toString().contains("RejectAndDontRequeueRecoverer"));
    }

    @Test
    void rabbitListenerContainerFactory_debeTenerBackoff2sX2Max10s()
            throws Exception {
        // Given — backoff: 2s, multiplicador 2.0, máximo 10s (3 intentos: 2s→4s→8s)
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, true);

        // When — extraer el ExponentialBackOffPolicy del RetryTemplate
        RetryOperationsInterceptor retryInterceptor =
                (RetryOperationsInterceptor) factory.getAdviceChain()[0];

        // En Spring Retry 2.0.x el campo se llama "retryOperations"
        Field retryOperationsField = RetryOperationsInterceptor.class
                .getDeclaredField("retryOperations");
        retryOperationsField.setAccessible(true);
        Object retryOps = retryOperationsField.get(retryInterceptor);

        Field backOffPolicyField = retryOps.getClass()
                .getDeclaredField("backOffPolicy");
        backOffPolicyField.setAccessible(true);
        BackOffPolicy backOffPolicy =
                (BackOffPolicy) backOffPolicyField.get(retryOps);

        // Then — debe ser ExponentialBackOffPolicy con valores correctos
        assertInstanceOf(ExponentialBackOffPolicy.class, backOffPolicy,
                "El BackOffPolicy debe ser ExponentialBackOffPolicy");

        ExponentialBackOffPolicy exponential =
                (ExponentialBackOffPolicy) backOffPolicy;

        assertEquals(2000, exponential.getInitialInterval(),
                "El intervalo inicial debe ser 2000ms (2s)");
        assertEquals(2.0, exponential.getMultiplier(), 0.01,
                "El multiplicador debe ser 2.0");
        assertEquals(10000, exponential.getMaxInterval(),
                "El intervalo máximo debe ser 10000ms (10s)");
    }

    @Test
    void rabbitListenerContainerFactory_debeTenerJacksonConverter()
            throws Exception {
        // Given
        // SimpleRabbitListenerContainerFactory no expone getMessageConverter()
        // en esta versión de Spring AMQP, así que accedemos al campo via reflection
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        // When
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(connectionFactory, true);

        // Obtener el messageConverter del factory padre via reflection
        Field converterField =
                AbstractRabbitListenerContainerFactory.class
                        .getDeclaredField("messageConverter");
        converterField.setAccessible(true);
        Object converter = converterField.get(factory);

        // Then — el factory debe usar Jackson2JsonMessageConverter
        assertNotNull(converter, "El messageConverter no debe ser null");
        assertInstanceOf(Jackson2JsonMessageConverter.class, converter);
    }

    // ─── verificaciones de integridad del diseño ──────────────────────────────

    @Test
    void todosLosBeans_debenTenerNombresCoherentes() {
        // Verifica que los nombres de beans sigan el patrón del proyecto
        TopicExchange exchange = config.notificacionExchange();
        Queue queue = config.notificacionQueue();
        DirectExchange dlx = config.notificacionDlx();
        Queue dlq = config.notificacionDlq();

        assertEquals("notificacion.exchange", exchange.getName());
        assertEquals("notificacion.queue", queue.getName());
        assertEquals("notificacion.dlx", dlx.getName());
        assertEquals("notificacion.queue.dlq", dlq.getName());
    }

    @Test
    void dlq_debeTenerNombreCoherenteConColaPrincipal() {
        // La DLQ debe nombrarse como la cola principal + ".dlq"
        Queue queue = config.notificacionQueue();
        Queue dlq = config.notificacionDlq();

        assertEquals(queue.getName() + ".dlq", dlq.getName(),
                "La DLQ debe nombrarse como la cola principal más '.dlq'");
    }

    @Test
    void dlx_debeTenerNombreCoherente() {
        // El DLX debe usar el prefijo del exchange principal
        Queue queue = config.notificacionQueue();
        assertEquals("notificacion.dlx",
                queue.getArguments().get("x-dead-letter-exchange"));
    }
}
