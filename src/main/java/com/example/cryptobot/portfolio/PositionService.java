package com.example.cryptobot.portfolio;

import com.example.cryptobot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PositionService {

    private final PositionRepository positionRepository;

    public Position getOrCreatePosition(Long accountId, String symbol) {
        return positionRepository.findByAccountIdAndSymbol(accountId, symbol)
                .orElseGet(() -> createNewPosition(accountId, symbol));
    }

    private Position createNewPosition(Long accountId, String symbol) {
        Position position = Position.builder()
                .account(new com.example.cryptobot.account.Account())
                .symbol(symbol)
                .quantity(0.0)
                .avgBuyPrice(0.0)
                .currentPrice(0.0)
                .unrealizedProfit(0.0)
                .unrealizedProfitRate(0.0)
                .status(Position.PositionStatus.CLOSED)
                .build();
        position.getAccount().setId(accountId);
        return positionRepository.save(position);
    }

    public List<Position> getPositions(Long accountId) {
        return positionRepository.findByAccountId(accountId);
    }

    public List<Position> getOpenPositions(Long accountId) {
        return positionRepository.findByAccountIdAndStatus(accountId, Position.PositionStatus.OPEN);
    }

    public void updatePosition(Long positionId, Double quantity, Double avgPrice, Double currentPrice) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException("POSITION_NOT_FOUND", "포지션을 찾을 수 없습니다."));
        
        position.setQuantity(quantity);
        position.setAvgBuyPrice(avgPrice);
        position.setCurrentPrice(currentPrice);
        
        // 미실현 손익 계산
        Double unrealizedProfit = (currentPrice - avgPrice) * quantity;
        Double unrealizedProfitRate = avgPrice > 0 ? (unrealizedProfit / (avgPrice * quantity)) * 100 : 0;
        
        position.setUnrealizedProfit(unrealizedProfit);
        position.setUnrealizedProfitRate(unrealizedProfitRate);
        position.setStatus(quantity > 0 ? Position.PositionStatus.OPEN : Position.PositionStatus.CLOSED);
        
        positionRepository.save(position);
        log.info("Position updated: positionId={}, quantity={}, avgPrice={}", positionId, quantity, avgPrice);
    }

}

