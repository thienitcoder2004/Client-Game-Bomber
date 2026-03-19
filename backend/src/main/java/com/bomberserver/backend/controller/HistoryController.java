package com.bomberserver.backend.controller;

import com.example.bomberserver.document.MatchHistoryDocument;
import com.example.bomberserver.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/me")
    public ResponseEntity<List<MatchHistoryDocument>> myHistory(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(historyService.getMyHistory(userId));
    }
}