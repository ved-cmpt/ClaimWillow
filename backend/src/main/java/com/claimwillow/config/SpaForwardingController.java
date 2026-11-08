package com.claimwillow.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the single-page app's index.html for client-side routes (same-origin deployment). */
@Controller
public class SpaForwardingController {

    @GetMapping({"/", "/login", "/claims", "/claims/**", "/policies", "/staff", "/staff/**", "/reports",
            "/admin", "/admin/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
