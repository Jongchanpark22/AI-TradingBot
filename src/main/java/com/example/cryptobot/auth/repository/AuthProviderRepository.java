package com.example.cryptobot.auth.repository;

import com.example.cryptobot.auth.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthProviderRepository extends JpaRepository<AuthProvider, Long> {

    Optional<AuthProvider> findByProviderAndProviderUserId(
            AuthProvider.Provider provider, String providerUserId);
}
