package com.example.taskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import java.net.URI;

@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "AI-Powered Task & Productivity Management API",
        version = "1.0",
        description = "RESTful API services providing task analytics, PDF reporting, and AI subtask decomposition."
    )
)
public class TaskmanagerApplication {

    public static void main(String[] args) {
        configureDatabaseProperties();
        configureOAuthProperties();
        SpringApplication.run(TaskmanagerApplication.class, args);
    }

    private static void configureDatabaseProperties() {
        String dbUrl = System.getenv("DB_URL");
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUrl != null && !dbUrl.isBlank() && dbUsername != null && !dbUsername.isBlank() && dbPassword != null && !dbPassword.isBlank()) {
            System.setProperty("spring.datasource.url", dbUrl);
            System.setProperty("spring.datasource.username", dbUsername);
            System.setProperty("spring.datasource.password", dbPassword);
            System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
            return;
        }

        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }

        try {
            URI uri = new URI(databaseUrl);
            String scheme = uri.getScheme();
            if (!"postgres".equalsIgnoreCase(scheme) && !"postgresql".equalsIgnoreCase(scheme)) {
                return;
            }

            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] parts = userInfo.split(":", 2);
                if (parts.length >= 1 && (dbUsername == null || dbUsername.isBlank())) {
                    System.setProperty("spring.datasource.username", parts[0]);
                }
                if (parts.length == 2 && (dbPassword == null || dbPassword.isBlank())) {
                    System.setProperty("spring.datasource.password", parts[1]);
                }
            }

            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String database = uri.getPath();
            if (database != null && database.startsWith("/")) {
                database = database.substring(1);
            }

            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                    .append(host)
                    .append(":")
                    .append(port)
                    .append("/")
                    .append(database);

            String query = uri.getQuery();
            if (query != null && !query.isBlank()) {
                jdbcUrl.append("?").append(query);
                if (!query.contains("sslmode=")) {
                    jdbcUrl.append("&sslmode=require");
                }
            } else {
                jdbcUrl.append("?sslmode=require");
            }

            System.setProperty("spring.datasource.url", jdbcUrl.toString());
            System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        } catch (Exception e) {
            System.err.println("Failed to parse DATABASE_URL variable: " + e.getMessage());
        }
    }

    private static void configureOAuthProperties() {
        String googleClientId = System.getenv("GOOGLE_CLIENT_ID");
        String googleClientSecret = System.getenv("GOOGLE_CLIENT_SECRET");
        
        if (googleClientId == null || googleClientId.isBlank()
                || googleClientSecret == null || googleClientSecret.isBlank()) {
            return;
        }

        System.setProperty("spring.security.oauth2.client.registration.google.client-id", googleClientId);
        System.setProperty("spring.security.oauth2.client.registration.google.client-secret", googleClientSecret);
        System.setProperty("spring.security.oauth2.client.registration.google.scope", "openid,profile,email");

        String redirectUri = System.getenv("GOOGLE_REDIRECT_URI");
        if (redirectUri != null && !redirectUri.isBlank()) {
            System.setProperty("spring.security.oauth2.client.registration.google.redirect-uri", redirectUri);
        }
    }
}