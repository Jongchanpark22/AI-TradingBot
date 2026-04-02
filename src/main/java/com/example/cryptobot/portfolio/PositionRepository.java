package com.example.cryptobot.portfolio;

import com.example.cryptobot.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByAccountId(Long accountId);
    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);
    List<Position> findByAccountIdAndStatus(Long accountId, Position.PositionStatus status);
    
    // 계정과 심볼과 상태로 포지션 조회
    Optional<Position> findByAccountAndSymbolAndStatus(Account account, String symbol, Position.PositionStatus status);
}
