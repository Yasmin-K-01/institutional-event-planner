package com.example.taskmanager.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for login request body.
 * Accepts both "username" and "email" to avoid 400 deserialization errors.
 */
public class LoginRequest {
    @JsonProperty("username")
    @JsonAlias({"email", "username"})
    private String username;

    @JsonProperty("password")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return username;
    }

    public void setEmail(String email) {
        this.username = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
