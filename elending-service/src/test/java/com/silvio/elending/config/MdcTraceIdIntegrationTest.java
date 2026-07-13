package com.silvio.elending.config;

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
        // Crea un span manualmente y verifica que el traceId aparece en MDC
        Tracer.SpanInScope scope = null;
        try {
            // Crea un nuevo span (inicia un nuevo trace)
            io.micrometer.tracing.Span span = tracer.nextSpan().start();

            // Pone el span en el contexto actual
            scope = tracer.withSpan(span);

            // El MDC debería tener el traceId ahora
            String traceId = MDC.get("traceId");
            assertNotNull(traceId, "traceId debe estar en MDC cuando hay span activo");
            assertFalse(traceId.isEmpty(), "traceId no debe ser vacío");
            assertFalse("NONE".equals(traceId), "traceId no debe ser NONE");

            // Verificar formato hexadecimal
            assertTrue(traceId.matches("[0-9a-f]{16,32}"),
                    "traceId debe ser hexadecimal de 16-32 chars: " + traceId);

            // El spanId también debería estar presente
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
        // Sin span activo, el MDC debe mostrar "NONE" o null
        String traceIdAntes = MDC.get("traceId");
        assertTrue(traceIdAntes == null || "NONE".equals(traceIdAntes),
                "Sin span activo, traceId debe ser null o NONE");
    }

    @Test
    void mdc_traceId_cambiaConCadaTrace() {
        // Crea dos traces distintos y verifica que los traceIds son diferentes
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
        // Verifica que al cerrar el scope, el MDC se limpia
        Tracer.SpanInScope scope = null;
        try {
            io.micrometer.tracing.Span span = tracer.nextSpan().start();
            scope = tracer.withSpan(span);
            assertNotNull(MDC.get("traceId"), "traceId debe estar presente dentro del scope");
        } finally {
            if (scope != null) scope.close();
        }

        // Después de cerrar el scope, el MDC debe limpiarse
        String traceIdDespues = MDC.get("traceId");
        assertTrue(traceIdDespues == null || "NONE".equals(traceIdDespues),
                "Al cerrar el scope, traceId debe desaparecer del MDC. Valor: " + traceIdDespues);
    }
}
