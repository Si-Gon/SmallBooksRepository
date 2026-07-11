package com.silvio.elending.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

// Configuración de ShedLock para bloqueo distribuido del scheduler
// Proveedor JDBC: usa la tabla shedlock en la misma BD del servicio
// Cada instancia adquiere el lock antes de ejecutar cerrarPrestamosVencidos
@Configuration
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
