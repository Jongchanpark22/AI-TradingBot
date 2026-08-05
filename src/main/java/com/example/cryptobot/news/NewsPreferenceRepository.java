package com.example.cryptobot.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NewsPreferenceRepository extends JpaRepository<NewsPreference, Long> {

    Optional<NewsPreference> findByUserId(Long userId);
}
