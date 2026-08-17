package com.generationb.foundation.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.foundation.BrandContext;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Aspect
@Component
public class AuditAspect {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public AuditAspect(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.generationb.foundation.Audited) || @within(com.generationb.foundation.Audited)")
    @Transactional
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String serviceName = joinPoint.getTarget().getClass().getSimpleName();
        Class<?> entityClass = getEntityClass(serviceName, methodName);

        if (entityClass == null) {
            return joinPoint.proceed();
        }

        String action = getAction(methodName);
        if ("READ".equals(action)) {
            // Q-E6: exportBriefAsPdf() and getSharedBriefLink() used to write UPDATE rows.
            return joinPoint.proceed();
        }
        UUID brandId = BrandContext.getCurrentBrandId();
        if (brandId == null) {
            brandId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
        UUID userId = BrandContext.getCurrentUserId();

        Object[] args = joinPoint.getArgs();
        List<UUID> entityIds = new ArrayList<>();
        Map<UUID, String> previousValues = new HashMap<>();

        if ("UPDATE".equals(action) || "DELETE".equals(action)) {
            if (args.length > 0) {
                if (args[0] instanceof UUID) {
                    entityIds.add((UUID) args[0]);
                } else if (args[0] instanceof List) {
                    List<?> list = (List<?>) args[0];
                    for (Object item : list) {
                        if (item instanceof UUID) {
                            entityIds.add((UUID) item);
                        }
                    }
                }
            }

            for (UUID id : entityIds) {
                Object entity = entityManager.find(entityClass, id);
                if (entity != null) {
                    previousValues.put(id, serializeEntity(entity));
                }
            }
        }

        Object result = joinPoint.proceed();

        if ("CREATE".equals(action)) {
            UUID createdId = extractIdFromResult(result);
            if (createdId != null) {
                Object entity = entityManager.find(entityClass, createdId);
                if (entity != null) {
                    writeAuditLog(brandId, entityClass.getSimpleName(), createdId, "CREATE", userId, null, serializeEntity(entity));
                }
            }
        } else if ("UPDATE".equals(action)) {
            for (UUID id : entityIds) {
                Object entity = entityManager.find(entityClass, id);
                if (entity != null) {
                    writeAuditLog(brandId, entityClass.getSimpleName(), id, "UPDATE", userId, previousValues.get(id), serializeEntity(entity));
                }
            }
        } else if ("DELETE".equals(action)) {
            for (UUID id : entityIds) {
                Object entity = entityManager.find(entityClass, id);
                String newValue = (entity != null) ? serializeEntity(entity) : null;
                writeAuditLog(brandId, entityClass.getSimpleName(), id, "DELETE", userId, previousValues.get(id), newValue);
            }
        }

        return result;
    }

    private void writeAuditLog(UUID brandId, String entityType, UUID entityId, String action, UUID userId, String prevVal, String newVal) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setBrandId(brandId);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setChangedBy(userId);
        log.setTimestamp(Instant.now());
        log.setPreviousValue(prevVal);
        log.setNewValue(newVal);
        entityManager.persist(log);
    }

    /** Method-name prefixes that are reads and must not produce an audit entry (Q-E6). */
    private static final Set<String> READ_PREFIXES = Set.of(
            "get", "find", "list", "search", "export", "preview", "count", "is", "has");

    private String getAction(String methodName) {
        for (String prefix : READ_PREFIXES) {
            if (methodName.startsWith(prefix)) {
                return "READ";
            }
        }
        if (methodName.startsWith("create") || methodName.startsWith("save")
                || methodName.startsWith("add") || methodName.startsWith("import")
                || methodName.startsWith("register")) {
            return "CREATE";
        }
        if (methodName.startsWith("delete") || methodName.startsWith("archive")
                || methodName.startsWith("remove") || methodName.startsWith("anonymise")) {
            return "DELETE";
        }
        return "UPDATE";
    }

    private Class<?> getEntityClass(String serviceName, String methodName) {
        try {
            if (serviceName.contains("BriefService")) {
                if (methodName.contains("Clause")) {
                    return Class.forName("com.generationb.briefs.internal.ContractClause");
                }
                return Class.forName("com.generationb.briefs.internal.Brief");
            } else if (serviceName.contains("ContractClauseService")) {
                return Class.forName("com.generationb.briefs.internal.ContractClause");
            } else if (serviceName.contains("CampaignService")) {
                return Class.forName("com.generationb.campaigns.internal.Campaign");
            } else if (serviceName.contains("KanbanService")) {
                if (methodName.contains("Board")) {
                    return Class.forName("com.generationb.campaigns.internal.KanbanBoard");
                } else if (methodName.contains("Column")) {
                    return Class.forName("com.generationb.campaigns.internal.KanbanColumn");
                } else {
                    return Class.forName("com.generationb.campaigns.internal.CampaignCard");
                }
            } else if (serviceName.contains("OutreachTemplateService")) {
                return Class.forName("com.generationb.outreach.internal.OutreachTemplate");
            } else if (serviceName.contains("OutreachCampaignService")) {
                return Class.forName("com.generationb.outreach.internal.OutreachCampaign");
            } else if (serviceName.contains("GiftingService")) {
                // Requirement #44 and #53 both need "who signed this off?" to be answerable.
                if (methodName.contains("CompSlip")) {
                    return Class.forName("com.generationb.gifting.internal.GiftingRun");
                } else if (methodName.contains("BrandOrder")) {
                    return Class.forName("com.generationb.gifting.internal.BrandOrder");
                } else if (methodName.contains("Dispatch")) {
                    return Class.forName("com.generationb.gifting.internal.Dispatch");
                }
                return null;
            } else if (serviceName.contains("ReportService")) {
                return Class.forName("com.generationb.reporting.internal.Report");
            }
        } catch (ClassNotFoundException ignored) {}
        return null;
    }

    private UUID extractIdFromResult(Object result) {
        if (result == null) return null;
        try {
            java.lang.reflect.Method idMethod = result.getClass().getMethod("id");
            return (UUID) idMethod.invoke(result);
        } catch (Exception e) {
            try {
                java.lang.reflect.Method getIdMethod = result.getClass().getMethod("getId");
                return (UUID) getIdMethod.invoke(result);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String serializeEntity(Object entity) {
        try {
            Map<String, Object> map = entityToMap(entity);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Q-B21: field names that must never be snapshotted into audit_log. The audit trail records
     * that something changed and by whom; it is not a second copy of everyone's personal data.
     */
    private static final Set<String> REDACTED_FIELDS = Set.of(
            "password", "passwordhash", "tokenhash", "tokendigest", "secret",
            "email", "phone", "street", "postalcode", "notetext", "bodytext", "bodyhtml");

    private Map<String, Object> entityToMap(Object entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || 
                    java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                if (field.isAnnotationPresent(jakarta.persistence.OneToMany.class) ||
                    field.isAnnotationPresent(jakarta.persistence.ManyToMany.class) ||
                    field.isAnnotationPresent(jakarta.persistence.ManyToOne.class) ||
                    field.isAnnotationPresent(jakarta.persistence.OneToOne.class)) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object val = field.get(entity);
                    if (REDACTED_FIELDS.contains(field.getName().toLowerCase())) {
                        map.put(field.getName(), val == null ? null : "[redacted]");
                    } else {
                        map.put(field.getName(), val);
                    }
                } catch (IllegalAccessException e) {
                    // ignore
                }
            }
            clazz = clazz.getSuperclass();
        }
        return map;
    }
}
