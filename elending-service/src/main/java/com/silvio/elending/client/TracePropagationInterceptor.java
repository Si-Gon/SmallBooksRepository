package com.silvio.elending.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TracePropagationInterceptor implements RequestInterceptor {

    private final Tracer tracer;

    @Override
    public void apply(RequestTemplate template) {
        var span = tracer.currentSpan();
        if (span != null) {
            var ctx = span.context();
            template.header("X-B3-TraceId", ctx.traceId());
            template.header("X-B3-SpanId", ctx.spanId());
            log.debug("Propagando trace context: traceId={}, spanId={}", ctx.traceId(), ctx.spanId());
        }
    }
}
