package com.example.cryptobot.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 커스텀 알림 CRUD API.
 *
 * <p>⚠️ 면책: 이 알림은 정보 제공 목적이며 투자 권유가 아닙니다.</p>
 */
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final UserAlertService alertService;

    /**
     * 알림 전체 목록 조회.
     */
    @GetMapping
    public List<UserAlert> getAll() {
        return alertService.findAll();
    }

    /**
     * 알림 단건 조회.
     */
    @GetMapping("/{id}")
    public UserAlert getById(@PathVariable Long id) {
        return alertService.findById(id);
    }

    /**
     * 알림 생성.
     * body 예: {"symbol":"KRW-BTC","name":"BTC RSI 과매도","conditionJson":"{\"indicator\":\"RSI\",\"op\":\"<\",\"value\":30}","cooldownMinutes":60}
     */
    @PostMapping
    public ResponseEntity<UserAlert> create(@RequestBody UserAlert request) {
        return ResponseEntity.ok(alertService.create(request));
    }

    /**
     * 알림 수정.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserAlert> update(@PathVariable Long id, @RequestBody UserAlert request) {
        return ResponseEntity.ok(alertService.update(id, request));
    }

    /**
     * 알림 삭제.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
