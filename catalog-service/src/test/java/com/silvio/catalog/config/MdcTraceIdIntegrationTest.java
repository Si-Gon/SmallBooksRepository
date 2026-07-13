package com.silvio.catalog.config;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

// Tests de integración que verifican que el TraceId se propaga
// correctamente al MDC (Mapped Diagnostic Context) para que aparezca
// en los logs con el formato configurado: [traceId spanId].
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
            assertNotNull(traceId, "traceId debe estar en MDC cuando hay span activo");
            assertFalse(traceId.isEmpty(), "traceId no debe ser vacío");
            assertFalse("NONE".equals(traceId), "traceId no debe ser NONE");
            assertTrue(traceId.matches("[0-9a-f]{16,32}"),
                    "traceId debe ser hexadecimal de 16-32 chars: " + traceId);

            String spanId = MDC.get("spanId");
            assertNotNull(spanId);
            assertTrue(spanId.matches("[0-9a-f]{16}"),
                    "spanId debe ser hexadecimal de 16 chars: " + spanId);
        } finally {
            if (scope != null) {
                scope.close();
            }
        }
    }

    @Test
    void mdc_noContieneTraceId_cuandoNoHaySpan() {
        String traceIdAntes = MDC.get("traceId");
        assertTrue(traceIdAntes == null || "NONE".equals(traceIdAntes),
                "Sin span activo, traceId debe ser null o NONE");
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
        assertNotEquals(traceId1, traceId2,
                "Cada trace debe tener un traceId único");
    }

    @Test
    void mdc_seLimpia_alCerrarScope() {
        Tracer.SpanInScope scope = null;
        try {
            io.micrometer.tracing.Span span = tracer.nextSpan().start();
            scope = tracer.withSpan(span);
            assertNotNull(MDC.get("traceId"), "traceId debe estar presente dentro del scope");
        } finally {
            if (scope != null) scope.close();
        }

        String traceIdDespues = MDC.get("traceId");
        assertTrue(traceIdDespues == null || "NONE".equals(traceIdDespues),
                "Al cerrar el scope, traceId debe desaparecer del MDC. Valor: " + traceIdDespues);
    }
}
