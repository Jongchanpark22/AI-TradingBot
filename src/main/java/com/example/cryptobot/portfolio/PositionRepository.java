package com.example.cryptobot.portfolio;

import com.example.cryptobot.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);

    Optional<Position> findByAccountIdAndSymbolAndStatus(
            Long accountId,
            String symbol,
            Position.PositionStatus status
    );

    Optional<Position> findByAccountAndSymbolAndStatus(
            Account account,
            String symbol,
            Position.PositionStatus status
    );

    List<Position> findByAccountId(Long accountId);

    List<Position> findByAccountIdAndStatus(
            Long accountId,
            Position.PositionStatus status
    );

    List<Position> findByStatus(Position.PositionStatus status);

    long countByAccountIdAndStatus(
            Long accountId,
            Position.PositionStatus status
    );

    long countByAccountAndStatus(
            Account account,
            Position.PositionStatus status
    );

    default List<Position> findAllOpenPositions() {
        return findByStatus(Position.PositionStatus.OPEN);
    }
}