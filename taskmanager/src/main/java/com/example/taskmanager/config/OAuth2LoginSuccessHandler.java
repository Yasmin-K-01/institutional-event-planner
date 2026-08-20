package com.example.taskmanager.config;

import com.example.taskmanager.model.User;
import com.example.taskmanager.service.AuthService;
import com.example.taskmanager.service.JwtUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final JwtUtils jwtUtils;

    public OAuth2LoginSuccessHandler(AuthService authService, JwtUtils jwtUtils) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
        User user = authService.upsertGoogleUser(oauth.getPrincipal());
        String target = UriComponentsBuilder.fromPath("/index.html")
                .fragment("oauth_token=" + jwtUtils.generateJwtToken(user.getUsername())
                        + "&username=" + user.getUsername()
                        + "&role=" + (user.getRole() == null ? "USER" : user.getRole()))
                .build().toUriString();
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
