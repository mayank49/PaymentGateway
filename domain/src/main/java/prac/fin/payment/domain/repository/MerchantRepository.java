package prac.fin.payment.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import prac.fin.payment.domain.entity.Merchant;

/**
 * Queries for the Merchant table.
 */
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
	
	/**
     * Used by the authentication filter on every API request.
     * Caller hashes the incoming API key (SHA-256) and looks it up here.
     * Returns empty if the key doesn't exist or the merchant is inactive.
     */
	Optional<Merchant> findByApiKeyHashAndActiveTrue(String apiKeyHash);
	
	/**
     * Used during merchant registration to prevent duplicate accounts.
     * Checked before creating a new merchant.
     */
    boolean existsByEmail(String email);
}
