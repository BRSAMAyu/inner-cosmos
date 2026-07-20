package com.innercosmos.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Clean deep-link fallback for the Aurora SPA. The final path segment must not
 * contain a dot, so real static assets remain owned by Spring's resource handler.
 */
@Controller
public class AuroraSpaController {
    @GetMapping({
            "/app/aurora/{a:[^\\.]+}",
            "/app/aurora/{a:[^\\.]+}/{b:[^\\.]+}",
            "/app/aurora/{a:[^\\.]+}/{b:[^\\.]+}/{c:[^\\.]+}",
            "/app/aurora/{a:[^\\.]+}/{b:[^\\.]+}/{c:[^\\.]+}/{d:[^\\.]+}"
    })
    public String forwardRoute() {
        return "forward:/app/aurora/index.html";
    }
}
