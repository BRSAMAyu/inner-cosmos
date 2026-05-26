package com.innercosmos.safety;

import org.springframework.stereotype.Component;

@Component
public class ResourceRedirectService {
    public String resourcePage() {
        return "/pages/safety-harbor.html";
    }
}
