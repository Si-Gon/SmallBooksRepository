package com.silvio.elending.client;

import com.silvio.elending.dto.NotificacionRequestDTO;
import com.silvio.elending.dto.NotificacionResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping("/api/notifications")
    NotificacionResponseDTO crear(@RequestBody NotificacionRequestDTO request);
}