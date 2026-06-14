package com.riscv.controller;

import com.riscv.model.SimulationResult;
import com.riscv.service.SimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimulatorController {

    private final SimulatorService service;

    public SimulatorController(SimulatorService service) {
        this.service = service;
    }

    @PostMapping("/simulate")
    public ResponseEntity<SimulationResult> simulate(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.run(body.getOrDefault("code", "")));
    }

    @PostMapping("/validate")
    public ResponseEntity<SimulatorService.ValidateResult> validate(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.validate(body.getOrDefault("code", "")));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "simulator", "RV32I"));
    }
}
