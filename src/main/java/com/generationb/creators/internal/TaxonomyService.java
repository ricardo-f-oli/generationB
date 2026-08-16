package com.generationb.creators.internal;

import com.generationb.creators.StyleTagResponse;
import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Requirements #16 and #17: the configurable tag library and the admin-defined custom attribute
 * schema. Both had entities and tables but no service, no endpoint and no way to use them.
 */
@Service
@RequiredArgsConstructor
public class TaxonomyService {

    private final ContentStyleTagRepository tagRepository;
    private final CreatorStyleTagLinkRepository tagLinkRepository;
    private final CustomAttributeDefinitionRepository definitionRepository;
    private final CreatorCustomAttributeRepository valueRepository;
    private final CreatorRepository creatorRepository;

    // ------------------------------------------------------------ tags (#17)

    @Transactional(readOnly = true)
    public List<StyleTagResponse> listTags() {
        UUID brandId = BrandContext.requireBrandId();
        return tagRepository.findAllForBrand(brandId).stream()
                .map(t -> new StyleTagResponse(t.getId(), t.getName(), t.getCategory(),
                        tagRepository.countCreators(t.getId())))
                .toList();
    }

    @Transactional
    public StyleTagResponse createTag(String name, String category) {
        UUID brandId = BrandContext.requireBrandId();
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Tag name is required");
        }
        tagRepository.findByBrandAndName(brandId, name.trim()).ifPresent(existing -> {
            throw ApiException.conflict("A tag called '" + name.trim() + "' already exists");
        });

        ContentStyleTag tag = new ContentStyleTag();
        tag.setBrandId(brandId);
        tag.setName(name.trim());
        tag.setCategory(category != null ? category : ContentStyleTag.AESTHETIC);
        ContentStyleTag saved = tagRepository.save(tag);
        return new StyleTagResponse(saved.getId(), saved.getName(), saved.getCategory(), 0);
    }

    @Transactional
    public StyleTagResponse renameTag(UUID tagId, String name) {
        UUID brandId = BrandContext.requireBrandId();
        ContentStyleTag tag = tagRepository.findScopedById(tagId, brandId)
                .orElseThrow(() -> ApiException.notFound("Tag"));
        if (name != null && !name.isBlank()) {
            tag.setName(name.trim());
        }
        ContentStyleTag saved = tagRepository.save(tag);
        return new StyleTagResponse(saved.getId(), saved.getName(), saved.getCategory(),
                tagRepository.countCreators(saved.getId()));
    }

    @Transactional
    public void deleteTag(UUID tagId) {
        UUID brandId = BrandContext.requireBrandId();
        ContentStyleTag tag = tagRepository.findScopedById(tagId, brandId)
                .orElseThrow(() -> ApiException.notFound("Tag"));
        tagRepository.delete(tag);
    }

    @Transactional
    public void assignTag(UUID creatorId, UUID tagId) {
        UUID brandId = BrandContext.requireBrandId();
        creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator"));
        tagRepository.findScopedById(tagId, brandId)
                .orElseThrow(() -> ApiException.notFound("Tag"));
        tagLinkRepository.save(new CreatorStyleTagLink(creatorId, tagId));
    }

    @Transactional
    public void unassignTag(UUID creatorId, UUID tagId) {
        tagLinkRepository.deleteLink(creatorId, tagId);
    }

    // ---------------------------------------------- custom attributes (#16)

    public record AttributeDefinitionResponse(
            UUID id, String key, String label, String type, List<String> options,
            boolean required, int displayOrder) {
    }

    @Transactional(readOnly = true)
    public List<AttributeDefinitionResponse> listDefinitions() {
        UUID brandId = BrandContext.requireBrandId();
        return definitionRepository.findActiveForBrand(brandId).stream()
                .map(d -> new AttributeDefinitionResponse(d.getId(), d.getAttributeKey(), d.getLabel(),
                        d.getAttributeType(), d.getOptions(), d.isRequired(), d.getDisplayOrder()))
                .toList();
    }

    @Transactional
    public AttributeDefinitionResponse createDefinition(String key, String label, String type,
                                                        List<String> options, boolean required) {
        UUID brandId = BrandContext.requireBrandId();
        if (key == null || key.isBlank() || label == null || label.isBlank()) {
            throw ApiException.badRequest("Attribute key and label are required");
        }
        String normalisedKey = key.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        definitionRepository.findByBrandIdAndAttributeKey(brandId, normalisedKey).ifPresent(existing -> {
            throw ApiException.conflict("An attribute with that key already exists");
        });

        CustomAttributeDefinition definition = new CustomAttributeDefinition();
        definition.setBrandId(brandId);
        definition.setAttributeKey(normalisedKey);
        definition.setLabel(label.trim());
        definition.setAttributeType(type != null ? type.toUpperCase() : "STRING");
        definition.setOptions(options);
        definition.setRequired(required);
        definition.setDisplayOrder((int) definitionRepository.findActiveForBrand(brandId).size());

        CustomAttributeDefinition saved = definitionRepository.save(definition);
        return new AttributeDefinitionResponse(saved.getId(), saved.getAttributeKey(), saved.getLabel(),
                saved.getAttributeType(), saved.getOptions(), saved.isRequired(), saved.getDisplayOrder());
    }

    @Transactional
    public void deleteDefinition(UUID definitionId) {
        UUID brandId = BrandContext.requireBrandId();
        CustomAttributeDefinition definition = definitionRepository.findScopedById(definitionId, brandId)
                .orElseThrow(() -> ApiException.notFound("Attribute definition"));
        definition.setActive(false);
        definitionRepository.save(definition);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAttributeValues(UUID creatorId) {
        UUID brandId = BrandContext.requireBrandId();
        return valueRepository.findForCreator(creatorId, brandId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        CreatorCustomAttribute::getAttributeKey,
                        a -> a.getAttributeValue() == null ? "" : a.getAttributeValue(),
                        (a, b) -> a));
    }

    @Transactional
    public void setAttributeValue(UUID creatorId, UUID definitionId, String value) {
        UUID brandId = BrandContext.requireBrandId();
        CustomAttributeDefinition definition = definitionRepository.findScopedById(definitionId, brandId)
                .orElseThrow(() -> ApiException.notFound("Attribute definition"));
        creatorRepository.findActiveById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator"));

        CreatorCustomAttribute attribute = valueRepository
                .findByCreatorIdAndDefinitionId(creatorId, definitionId)
                .orElseGet(() -> {
                    CreatorCustomAttribute created = new CreatorCustomAttribute();
                    created.setCreatorId(creatorId);
                    created.setBrandId(brandId);
                    created.setDefinitionId(definitionId);
                    created.setAttributeKey(definition.getAttributeKey());
                    created.setAttributeType(definition.getAttributeType());
                    return created;
                });
        attribute.setAttributeValue(value);
        valueRepository.save(attribute);
    }
}
