package com.cardgame.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback — returns index.html for any unknown route so that
 * React Router can take over on the client side.
 *
 * <p>This only applies when the React app is bundled inside the JAR
 * (i.e. built with {@code -Pbuild-frontend}). In local development
 * the Vite dev server handles this automatically.</p>
 */
@Controller
public class SpaController {

    /**
     * Catch-all for non-API, non-WS routes.
     * Spring Boot serves /static/index.html via its default resource handler,
     * so we just forward to it.
     */
    @GetMapping(value = {
            "/",
            "/game/**",
            "/lobby/**",
            "/waiting/**"
    })
    public String spa() {
        return "forward:/index.html";
    }
}

