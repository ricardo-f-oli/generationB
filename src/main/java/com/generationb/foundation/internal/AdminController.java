package com.generationb.foundation.internal;

import com.generationb.foundation.ApiResponse;
import com.generationb.foundation.internal.AuditViewService.AuditRow;
import com.generationb.foundation.internal.UserAdminService.CreateUserCommand;
import com.generationb.foundation.internal.UserAdminService.UpdateUserCommand;
import com.generationb.foundation.internal.UserAdminService.UserRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Requirements #35 and #36: user management and the audit trail.
 *
 * <p>Under {@code /api/settings/**}, which the security config already restricts to admins
 * (Q-F17); the service methods carry their own {@code @PreAuthorize} as well, so the rule holds
 * even if a route is moved.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserAdminService userAdminService;
    private final AuditViewService auditViewService;

    // ---------------------------------------------------------------- users

    @GetMapping("/users")
    public ApiResponse<List<UserRow>> listUsers(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "name"));

        Page<UserRow> result = userAdminService.list(pageable);
        return ApiResponse.of(result.getContent(), ApiResponse.Meta.of(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping("/users")
    public ApiResponse<UserRow> createUser(@RequestBody CreateUserCommand command) {
        return ApiResponse.of(userAdminService.create(command));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<UserRow> updateUser(@PathVariable UUID id,
                                           @RequestBody UpdateUserCommand command) {
        return ApiResponse.of(userAdminService.update(id, command));
    }

    @PostMapping("/users/{id}/unlock")
    public ApiResponse<UserRow> unlockUser(@PathVariable UUID id) {
        return ApiResponse.of(userAdminService.unlock(id));
    }

    @PostMapping("/users/{id}/send-reset")
    public ApiResponse<Void> sendReset(@PathVariable UUID id) {
        userAdminService.sendPasswordReset(id);
        return ApiResponse.success();
    }

    @GetMapping("/roles")
    public ApiResponse<List<String>> roles() {
        return ApiResponse.of(userAdminService.availableRoles());
    }

    // ---------------------------------------------------------------- audit

    @GetMapping("/audit")
    public ApiResponse<List<AuditRow>> audit(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID changedBy,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "timestamp"));

        Page<AuditRow> result = auditViewService.search(
                entityType, action, entityId, changedBy, from, to, pageable);

        return ApiResponse.of(result.getContent(), ApiResponse.Meta.of(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/audit/entity-types")
    public ApiResponse<List<String>> auditEntityTypes() {
        return ApiResponse.of(auditViewService.entityTypes());
    }
}
