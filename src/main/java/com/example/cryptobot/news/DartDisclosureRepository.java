package com.example.cryptobot.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DartDisclosureRepository extends JpaRepository<DartDisclosure, Long> {

    boolean existsByRceptNo(String rceptNo);

    List<DartDisclosure> findByLinkedSymbolOrderByDisclosureDateDesc(String linkedSymbol);

    List<DartDisclosure> findByDisclosureDateGreaterThanEqualOrderByDisclosureDateDesc(LocalDate from);
}
