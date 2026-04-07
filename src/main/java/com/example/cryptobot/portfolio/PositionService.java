package com.example.cryptobot.portfolio;

import com.example.cryptobot.account.Account;
import com.example.cryptobot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        Account account = new Account();
        account.setId(accountId);

        Position position = Position.builder()
                .account(account)
                .symbol(symbol)
                .quantity(BigDecimal.ZERO)
                .avgBuyPrice(BigDecimal.ZERO)
                .currentPrice(BigDecimal.ZERO)
                .unrealizedProfit(BigDecimal.ZERO)
                .unrealizedProfitRate(BigDecimal.ZERO)
                .status(Position.PositionStatus.CLOSED)
                .build();

        return positionRepository.save(position);
    }

    @Transactional(readOnly = true)
    public List<Position> getPositions(Long accountId) {
        return positionRepository.findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<Position> getOpenPositions(Long accountId) {
        return positionRepository.findByAccountIdAndStatus(accountId, Position.PositionStatus.OPEN);
    }

    public void updatePosition(Long positionId, BigDecimal quantity, BigDecimal avgPrice, BigDecimal currentPrice) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException("POSITION_NOT_FOUND", "포지션을 찾을 수 없습니다."));

        BigDecimal safeQuantity = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal safeAvgPrice = avgPrice != null ? avgPrice : BigDecimal.ZERO;
        BigDecimal safeCurrentPrice = currentPrice != null ? currentPrice : BigDecimal.ZERO;

        position.setQuantity(safeQuantity);
        position.setAvgBuyPrice(safeAvgPrice);
        position.setCurrentPrice(safeCurrentPrice);

        if (safeQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            position.setQuantity(BigDecimal.ZERO);
            position.setUnrealizedProfit(BigDecimal.ZERO);
            position.setUnrealizedProfitRate(BigDecimal.ZERO);
            position.setStatus(Position.PositionStatus.CLOSED);

            positionRepository.save(position);
            log.info("Position closed: positionId={}", positionId);
            return;
        }

        BigDecimal unrealizedProfit = safeCurrentPrice.subtract(safeAvgPrice).multiply(safeQuantity);

        BigDecimal baseAmount = safeAvgPrice.multiply(safeQuantity);
        BigDecimal unrealizedProfitRate = BigDecimal.ZERO;

        if (baseAmount.compareTo(BigDecimal.ZERO) > 0) {
            unrealizedProfitRate = unrealizedProfit
                    .divide(baseAmount, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        position.setUnrealizedProfit(unrealizedProfit);
        position.setUnrealizedProfitRate(unrealizedProfitRate);
        position.setStatus(Position.PositionStatus.OPEN);

        positionRepository.save(position);
        log.info("Position updated: positionId={}, quantity={}, avgPrice={}",
                positionId, safeQuantity, safeAvgPrice);
    }
}