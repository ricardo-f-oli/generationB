package com.generationb.foundation.internal;

import com.generationb.foundation.BrandContext;
import com.generationb.foundation.User;
import com.generationb.foundation.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Requirement #36: the audit trail, readable by an admin.
 *
 * <p>Rows carry the user's name rather than a bare UUID — an audit trail nobody can read is not
 * an audit trail. The before/after JSON is passed through as stored; the aspect already redacts
 * personal fields on the way in (Q-B21), so nothing extra leaks here.
 */
@Service
@RequiredArgsConstructor
public class AuditViewService {

    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("2999-12-31T23:59:59Z");

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public record AuditRow(
            UUID id, String entityType, UUID entityId, String action,
            UUID changedBy, String changedByName, Instant timestamp,
            String previousValue, String newValue) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public Page<AuditRow> search(String entityType, String action, UUID entityId,
                                 UUID changedBy, Instant from, Instant to, Pageable pageable) {
        BrandContext.requireBrandId();

        // Postgres cannot infer the type of a null timestamp parameter, so the bounds are always
        // supplied; open-ended just means "since the epoch" and "for the foreseeable future".
        Page<AuditLog> page = auditLogRepository.search(
                blankToNull(entityType), blankToNull(action), entityId, changedBy,
                from != null ? from : EARLIEST, to != null ? to : LATEST, pageable);

        List<UUID> userIds = page.getContent().stream()
                .map(AuditLog::getChangedBy)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, String> names = userIds.isEmpty() ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                user -> user.getName() != null ? user.getName() : user.getEmail(),
                                (a, b) -> a));

        return page.map(log -> new AuditRow(
                log.getId(), log.getEntityType(), log.getEntityId(), log.getAction(),
                log.getChangedBy(),
                log.getChangedBy() == null ? "System" : names.getOrDefault(log.getChangedBy(), "Unknown"),
                log.getTimestamp(), log.getPreviousValue(), log.getNewValue()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public List<String> entityTypes() {
        BrandContext.requireBrandId();
        return auditLogRepository.findEntityTypes();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
