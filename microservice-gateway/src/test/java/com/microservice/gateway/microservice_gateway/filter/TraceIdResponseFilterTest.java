package com.microservice.gateway.microservice_gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests unitarios del TraceIdResponseFilter.
//
// El filtro agrega el header X-Trace-Id a la respuesta HTTP usando el
// Tracer de Micrometer. Se ejecuta al final de la cadena (LOWEST_PRECEDENCE)
// para que la respuesta ya este generada cuando se lea el traceId.
//
// Estrategia de testing:
// Construimos el filtro manualmente con un mock de Tracer y verificamos
// que el header se agregue solo cuando hay un span activo.
class TraceIdResponseFilterTest {

    private static final String TRACE_ID = "abcdef1234567890abcdef1234567890";

    private Tracer tracer;
    private Span span;
    private TraceContext traceContext;
    private TraceIdResponseFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        span = mock(Span.class);
        traceContext = mock(TraceContext.class);
        filter = new TraceIdResponseFilter(tracer);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void traceHeaderPresent_cuandoHaySpan() {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(TRACE_ID);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo(TRACE_ID);
    }

    @Test
    void sinSpan_noAgregaHeader() {
        when(tracer.currentSpan()).thenReturn(null);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isNull();
    }

    @Test
    void tieneOrdenLowestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void chain_filter_siempreSeEjecuta() {
        when(tracer.currentSpan()).thenReturn(null);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void traceHeader_formatoHex32() {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(TRACE_ID);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        String header = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
        assertThat(header).isEqualTo(TRACE_ID);
        assertThat(header).hasSize(32);
        assertThat(header).matches("[0-9a-f]{32}");
    }

    @Test
    void exchangeDiferentes_tienenTraceIdsIndependientes() {
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(TRACE_ID);

        // Primer request
        when(tracer.currentSpan()).thenReturn(span);
        MockServerHttpRequest req1 = MockServerHttpRequest.get("/api/libros").build();
        ServerWebExchange exchange1 = MockServerWebExchange.from(req1);
        filter.filter(exchange1, chain).block();

        // Segundo request con otro traceId
        String traceId2 = "deadbeefdeadbeefdeadbeefdeadbeef";
        when(traceContext.traceId()).thenReturn(traceId2);
        MockServerHttpRequest req2 = MockServerHttpRequest.get("/api/usuarios").build();
        ServerWebExchange exchange2 = MockServerWebExchange.from(req2);
        filter.filter(exchange2, chain).block();

        assertThat(exchange1.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo(TRACE_ID);
        assertThat(exchange2.getResponse().getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo(traceId2);
    }
}
