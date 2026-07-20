package prac.fin.payment.domain.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Shared audit fields inherited by every entity.
 *
 * @MappedSuperclass means JPA won't create a table for this class itself.
 * Its fields are added to the table of whichever entity extends it.
 *
 * @EntityListeners(AuditingEntityListener.class) tells JPA to automatically
 * set createdAt on INSERT and updatedAt on every UPDATE.
 * For this to work, the main application class needs @EnableJpaAuditing.
 *
 * Why Instant?
 * Instant is always UTC, no timezone ambiguity.
 * LocalDateTime has no timezone info, so if servers run in different
 * regions or timezone config changes, stored timestamps become meaningless.
 * Instant is the standard for any production system that crosses timezones.
 *
 * PostgreSQL stores this as TIMESTAMPTZ (timestamp with time zone).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	
	@LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
