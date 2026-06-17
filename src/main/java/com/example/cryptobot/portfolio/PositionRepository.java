package com.example.cryptobot.portfolio;

import com.example.cryptobot.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    long countByAccountAndStatus(Account account, Position.PositionStatus status);

    List<Position> findByStatus(Position.PositionStatus status);

    // OPEN + PARTIAL 동시 조회 (모니터링/리스크 체크용)
    @Query("SELECT p FROM Position p WHERE p.status IN ('OPEN', 'PARTIAL')")
    List<Position> findAllActive();

    @Query("SELECT p FROM Position p WHERE p.account = :account AND p.symbol = :symbol AND p.status IN ('OPEN', 'PARTIAL')")
    Optional<Position> findActiveByAccountAndSymbol(@Param("account") Account account, @Param("symbol") String symbol);

    @Query("SELECT COUNT(p) FROM Position p WHERE p.account = :account AND p.status IN ('OPEN', 'PARTIAL')")
    long countActiveByAccount(@Param("account") Account account);
}
