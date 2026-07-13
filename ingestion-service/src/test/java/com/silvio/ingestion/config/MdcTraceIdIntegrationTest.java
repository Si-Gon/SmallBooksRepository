package com.silvio.ingestion.config;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MdcTraceIdIntegrationTest {

    @Autowired(required = false)
    private Tracer tracer;

    @Test
    void tracer_estaDisponible() {
        assertNotNull(tracer, "Tracer debe estar disponible");
    }

    @Test
    void mdc_contieneTraceId_cuandoHaySpanActivo() {
        Tracer.SpanInScope scope = null;
        try {
            io.micrometer.tracing.Span span = tracer.nextSpan().start();
            scope = tracer.withSpan(span);
            String traceId = MDC.get("traceId");
            assertNotNull(traceId);
            assertFalse("NONE".equals(traceId));
            assertTrue(traceId.matches("[0-9a-f]{16,32}"));
            String spanId = MDC.get("spanId");
            assertNotNull(spanId);
            assertTrue(spanId.matches("[0-9a-f]{16}"));
        } finally {
            if (scope != null) scope.close();
        }
    }

    @Test
    void mdc_noContieneTraceId_cuandoNoHaySpan() {
        String traceId = MDC.get("traceId");
        assertTrue(traceId == null || "NONE".equals(traceId));
    }

    @Test
    void mdc_traceId_cambiaConCadaTrace() {
        String traceId1;
        String traceId2;
        Tracer.SpanInScope scope1 = null;
        try {
            io.micrometer.tracing.Span span1 = tracer.nextSpan().start();
            scope1 = tracer.withSpan(span1);
            traceId1 = MDC.get("traceId");
        } finally {
            if (scope1 != null) scope1.close();
        }
        Tracer.SpanInScope scope2 = null;
        try {
            io.micrometer.tracing.Span span2 = tracer.nextSpan().start();
            scope2 = tracer.withSpan(span2);
            traceId2 = MDC.get("traceId");
        } finally {
            if (scope2 != null) scope2.close();
        }
        assertNotNull(traceId1);
        assertNotNull(traceId2);
        assertNotEquals(traceId1, traceId2);
    }

    @Test
    void mdc_seLimpia_alCerrarScope() {
        Tracer.SpanInScope scope = null;
        try {
            io.micrometer.tracing.Span span = tracer.nextSpan().start();
            scope = tracer.withSpan(span);
            assertNotNull(MDC.get("traceId"));
        } finally {
            if (scope != null) scope.close();
        }
        String traceId = MDC.get("traceId");
        assertTrue(traceId == null || "NONE".equals(traceId));
    }
}
