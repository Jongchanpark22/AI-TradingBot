package com.example.cryptobot.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 커스텀 알림 CRUD 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAlertService {

    private final UserAlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    /** 전체 알림 목록 조회 (내부·스케줄러용) */
    public List<UserAlert> findAll() {
        return alertRepository.findAll();
    }

    /** 특정 회원의 알림 목록 조회 */
    public List<UserAlert> findByUserId(Long userId) {
        return alertRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 알림 단건 조회 (내부·스케줄러용 — userId 검증 없음) */
    public UserAlert findById(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다: " + id));
    }

    /** 알림 단건 조회 — 소유권 검증 포함 */
    public UserAlert findByIdAndUserId(Long id, Long userId) {
        return alertRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다: " + id));
    }

    /** 알림 생성 */
    @Transactional
    public UserAlert create(UserAlert alert) {
        validateConditionJson(alert.getConditionJson());
        return alertRepository.save(alert);
    }

    /** 알림 수정 — 소유권 검증 포함 */
    @Transactional
    public UserAlert update(Long userId, Long id, UserAlert request) {
        UserAlert alert = findByIdAndUserId(id, userId);
        if (request.getSymbol() != null) alert.setSymbol(request.getSymbol());
        if (request.getName() != null) alert.setName(request.getName());
        if (request.getConditionJson() != null) {
            validateConditionJson(request.getConditionJson());
            alert.setConditionJson(request.getConditionJson());
        }
        if (request.getEnabled() != null) alert.setEnabled(request.getEnabled());
        if (request.getCooldownMinutes() != null) alert.setCooldownMinutes(request.getCooldownMinutes());
        return alertRepository.save(alert);
    }

    /** 알림 삭제 — 소유권 검증 포함 */
    @Transactional
    public void delete(Long userId, Long id) {
        UserAlert alert = findByIdAndUserId(id, userId);
        alertRepository.delete(alert);
    }

    /** conditionJson 파싱 유효성 검사 */
    private void validateConditionJson(String json) {
        try {
            AlertCondition cond = objectMapper.readValue(json, AlertCondition.class);
            if (cond.indicator() == null || cond.op() == null) {
                throw new IllegalArgumentException("indicator, op 필드가 필요합니다.");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("conditionJson 파싱 오류: " + e.getMessage());
        }
    }

    /** conditionJson → AlertCondition 파싱 */
    public AlertCondition parseCondition(String json) {
        try {
            return objectMapper.readValue(json, AlertCondition.class);
        } catch (JsonProcessingException e) {
            log.warn("조건 파싱 실패: {}", json, e);
            return null;
        }
    }
}
