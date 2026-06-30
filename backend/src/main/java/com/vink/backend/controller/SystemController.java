package com.vink.backend.controller;

import org.springframework.boot.SpringBootVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java port of the /health and /api index handlers in server/src/index.ts. */
@RestController
public class SystemController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
        out.put("timestamp", Instant.now().toString());
        return out;
    }

    @GetMapping("/api")
    public Map<String, Object> apiIndex() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", "VINK Multi Services Backend API (Java/Spring Boot)");
        out.put("version", "1.0.0");
        out.put("springBoot", SpringBootVersion.getVersion());
        out.put("convertedModules", List.of("vehicles"));
        out.put("pendingModules", List.of(
                "auth", "kpis", "subscribers", "network", "billing", "fraud", "provisioning",
                "support", "interconnects", "alerts", "rides", "healingDrivers", "passengers", "sos",
                "bankAccounts", "bankCards", "bankPayments", "bankTreasury", "bankCompliance", "bankUsers",
                "marketplace", "public", "globalBanking", "financialReports", "levySystem", "afc"
        ));
        out.put("note", "Converted endpoints are mounted under /api/vehicles/**. "
                + "Remaining modules are still served by the original Node/Express backend until ported.");
        return out;
    }
}
