package prac.fin.payment.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
	name = "merchant",
	indexes = {
		@Index(name = "idx_merchant_api_key_hash", columnList = "api_key_hash", unique = true),
		@Index(name = "idx_merchant_email", columnList = "email", unique = true)	
	}
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Merchant extends BaseEntity {
	
	@Id
	@GeneratedValue
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;
	
	@Column(name = "name", nullable = false, length = 200)
	private String name;
	
	@Column(name = "email", nullable = false, unique = true, length = 255)
	private String email;
	
	/**
     * SHA-256 hash of the merchant's API key.
     * The raw key is never stored.
     * On each request: hash(incoming key) == this value → authenticated.
     */
	@Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
	private String apiKeyHash;
	
	/**
     * Bank account details for end-of-day settlement payouts.
     * IFSC = Indian Financial System Code (identifies the bank branch).
     */
	@Column(name = "settlement_account_number", length = 50)
    private String settlementAccountNumber;

    @Column(name = "settlement_ifsc", length = 20)
    private String settlementIfsc;
    
    /**
     * URL where we POST payment event notifications (webhooks).
     * e.g. https://myshop.com/webhooks/payment
     */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;
    
    /**
     * Soft delete. We never hard delete a merchant.
     * Deactivated merchants can't make API calls but their
     * 
     * @Builder.Default prevents Lombok from ignoring the initialized value.
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
