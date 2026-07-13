package com.microservice.gateway.microservice_gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// FILTRO GLOBAL: expone el TraceId en la respuesta HTTP
//
// Cada request que ingresa al Gateway recibe un traceId generado por
// Micrometer Tracing (Brave). Este filtro lo copia al header de
// respuesta "X-Trace-Id" para que el cliente sepa con qué traceId
// buscar en los logs si necesita depurar una falla.
//
// El orden LOWEST_PRECEDENCE asegura que se ejecute al final, cuando
// la respuesta ya fue generada por el microservicio destino.
@Component
public class TraceIdResponseFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;

    public TraceIdResponseFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                ServerHttpResponse response = exchange.getResponse();
                if (!response.isCommitted()) {
                    response.getHeaders().add("X-Trace-Id",
                            currentSpan.context().traceId());
                }
            }
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
