package com.example.cryptobot.portfolio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/positions")
@RequiredArgsConstructor
@Slf4j
public class PositionController {

    private final PositionService positionService;

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Position>> getPositions(@PathVariable Long accountId) {
        List<Position> positions = positionService.getPositions(accountId);
        return ResponseEntity.ok(positions);
    }

    @GetMapping("/account/{accountId}/open")
    public ResponseEntity<List<Position>> getOpenPositions(@PathVariable Long accountId) {
        List<Position> positions = positionService.getOpenPositions(accountId);
        return ResponseEntity.ok(positions);
    }

}

