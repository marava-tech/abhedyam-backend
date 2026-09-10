package com.abhedyam.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Abhedyam Backend API")
                .version("1.0.0")
                .description("Backend API for Abhedyam Business Management Application. " +
                    "All endpoints are prefixed with /api/v1. " +
                    "Use JWT token from /auth/google/login (owners), /auth/otp/verify (web customers), or /auth/phone/login (Connect app).")
                .contact(new Contact()
                    .name("Abhedyam Team")
                    .email("support@abhedyam.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .servers(List.of(
                new Server().url("http://localhost:" + serverPort).description("Local Development Server")
            ))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("Bearer Authentication",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token from Google login, OTP verify, or phone login"))
                .addSecuritySchemes("AdminKey",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Admin-Key")
                        .description("Admin key in format: {prefix}{date}{month} (e.g., REDACTED_ADMIN_KEY1901)")));
    }
}
