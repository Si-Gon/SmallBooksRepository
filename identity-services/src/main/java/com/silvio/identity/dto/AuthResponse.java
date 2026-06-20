package com.silvio.identity.dto;

import org.springframework.hateoas.RepresentationModel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class AuthResponse extends RepresentationModel<AuthResponse> {
    private String accessToken;
    private String refreshToken;
    private String message;
    private String username;

}
