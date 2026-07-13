package com.theragenx.icsr.service;

import com.theragenx.icsr.model.AnnotatedField;
import com.theragenx.icsr.model.Case;
import com.theragenx.icsr.model.MergeAnnotation;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Produces a merged {@link Case} from a stored version and a follow-up payload.
 *
 * <p>Merge rules (per DESIGN.md):
 * <ul>
 *   <li>Field in both, same value     → {@code status: "unchanged"}</li>
 *   <li>Field in both, different value → {@code status: "overridden"}, {@code previous_value} set</li>
 *   <li>Field in follow-up only        → {@code status: "new"}</li>
 *   <li>Field in stored only           → {@code status: "retained"} — stored value preserved</li>
 * </ul>
 *
 * <p>Top-level metadata (classification, extractedAt, sourceDocument) comes from the follow-up.
 * {@code missing_fields} is taken directly from the follow-up — it is an annotation for that
 * extraction run only, not cumulative state.
 */
@Service
public class MergeService {

    /**
     * Merges {@code followUp} onto {@code stored} and returns the annotated result.
     * Every field in the returned case carries a {@code merge} annotation.
     */
    public Case merge(Case stored, Case followUp) {

        // Collect all section keys from both — sections absent from one side are still included.
        Set<String> allSectionKeys = new LinkedHashSet<>();
        allSectionKeys.addAll(stored.sections().keySet());
        allSectionKeys.addAll(followUp.sections().keySet());

        Map<String, Map<String, AnnotatedField>> mergedSections = new LinkedHashMap<>();

        for (String sectionKey : allSectionKeys) {

            // Use empty map as a safe default when a section is absent from one side.
            Map<String, AnnotatedField> storedFields =
                    stored.sections().getOrDefault(sectionKey, Map.of());
            Map<String, AnnotatedField> followUpFields =
                    followUp.sections().getOrDefault(sectionKey, Map.of());

            // Collect all field keys within this section from both sides.
            Set<String> allFieldKeys = new LinkedHashSet<>();
            allFieldKeys.addAll(storedFields.keySet());
            allFieldKeys.addAll(followUpFields.keySet());

            Map<String, AnnotatedField> mergedFields = new LinkedHashMap<>();

            for (String fieldKey : allFieldKeys) {
                AnnotatedField storedField   = storedFields.get(fieldKey);
                AnnotatedField followUpField = followUpFields.get(fieldKey);

                AnnotatedField merged = mergeField(storedField, followUpField);
                mergedFields.put(fieldKey, merged);
            }

            mergedSections.put(sectionKey, Map.copyOf(mergedFields));
        }

        // Build the merged Case. Canonical caseId comes from stored; everything
        // else (metadata, classification, missingFields) comes from the follow-up.
        return new Case(
                stored.caseId(),
                stored.version() + 1,
                followUp.caseClassification(),
                followUp.extractedAt(),
                followUp.sourceDocument(),
                Map.copyOf(mergedSections),
                followUp.missingFields()   // AI's report for this extraction run only
        );
    }

    /**
     * Applies the four-way status logic to a single field.
     *
     * @param stored   the field from the stored version, or {@code null} if absent
     * @param followUp the field from the follow-up payload, or {@code null} if absent
     */
    private AnnotatedField mergeField(AnnotatedField stored, AnnotatedField followUp) {

        // Field exists only in the follow-up — the AI found something new.
        if (stored == null) {
            return new AnnotatedField(
                    followUp.value(),
                    followUp.confidence(),
                    followUp.source(),
                    MergeAnnotation.newField()
            );
        }

        // Field exists only in the stored case — the AI did not re-extract it.
        // Per DESIGN.md: preserve the stored value; mark as "retained" so the
        // reviewer knows this field was not revisited in the follow-up.
        if (followUp == null) {
            return new AnnotatedField(
                    stored.value(),
                    stored.confidence(),
                    stored.source(),
                    MergeAnnotation.retained()
            );
        }

        // Field exists in both. Compare values to decide unchanged vs overridden.
        if (Objects.equals(stored.value(), followUp.value())) {
            // Same value — use the follow-up's confidence and source (may have improved).
            return new AnnotatedField(
                    followUp.value(),
                    followUp.confidence(),
                    followUp.source(),
                    MergeAnnotation.unchanged()
            );
        }

        // Different value — the follow-up wins; capture what the stored value was.
        return new AnnotatedField(
                followUp.value(),
                followUp.confidence(),
                followUp.source(),
                MergeAnnotation.overridden(stored.value())
        );
    }
}

