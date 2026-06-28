package com.example.cryptobot.strategy.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Spring 컨텍스트에 등록된 모든 {@link Strategy} 빈을 수집하는 레지스트리.
 *
 * <p>{@code @Component}가 붙은 {@link Strategy} 구현체는 자동으로 이 목록에 포함된다.
 */
@Component
@RequiredArgsConstructor
public class StrategyRegistry {

    private final List<Strategy> strategies;

    /**
     * 전략 ID로 특정 전략을 조회한다.
     *
     * @param id 전략 고유 식별자
     * @return 해당 전략, 없으면 empty
     */
    public Optional<Strategy> findById(String id) {
        return strategies.stream()
                .filter(s -> s.id().equals(id))
                .findFirst();
    }

    /**
     * 특정 유형의 전략 목록을 반환한다.
     *
     * @param type 전략 행동 유형
     * @return 해당 유형의 전략 목록
     */
    public List<Strategy> findByType(StrategyType type) {
        return strategies.stream()
                .filter(s -> s.type() == type)
                .toList();
    }

    /** 등록된 모든 전략을 반환한다. */
    public List<Strategy> all() {
        return List.copyOf(strategies);
    }
}
