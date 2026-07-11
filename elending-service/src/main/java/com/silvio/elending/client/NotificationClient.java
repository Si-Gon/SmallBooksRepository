// NOTA: Este cliente Feign ha sido reemplazado por mensajería asíncrona
// con RabbitMQ (NotificacionPublisher). Las notificaciones ahora se publican
// en el exchange "notificacion.exchange" para que Notification Service
// las consuma desde la cola "notificacion.queue".
// Ver: com.silvio.elending.messaging.NotificacionPublisher
