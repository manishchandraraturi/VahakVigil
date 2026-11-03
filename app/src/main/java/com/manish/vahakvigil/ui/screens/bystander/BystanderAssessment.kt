package com.manish.vahakvigil.ui.screens.bystander

import com.manish.vahakvigil.domain.model.Contact
import com.manish.vahakvigil.domain.model.MedicalProfile

/**
 * Assessment questions answered by a bystander arriving at the scene.
 *
 * Each question accepts true (YES), false (NO), or null (unanswered).
 */
data class BystanderAssessment(
    val isConscious: Boolean? = null,
    val isBreathing: Boolean? = null,
    val hasHeavyBleeding: Boolean? = null,
    val hasSeriousInjury: Boolean? = null
) {
    val isAnyAnswered: Boolean
        get() = isConscious != null || isBreathing != null || hasHeavyBleeding != null || hasSeriousInjury != null
}

/**
 * Structured guidance item displayed to the bystander based on the current assessment.
 */
data class BystanderGuidanceItem(
    val id: String,
    val priority: Int, // 1 = Critical life threat, 2 = Urgent trauma, 3 = Stable precaution
    val title: String,
    val bulletPoints: List<String>,
    val isCritical: Boolean = false
)

/**
 * Pure deterministic resolver for translating a [BystanderAssessment] into
 * ordered, high-priority, actionable medical guidance.
 */
object BystanderGuidanceResolver {

    fun resolve(assessment: BystanderAssessment): List<BystanderGuidanceItem> {
        val items = mutableListOf<BystanderGuidanceItem>()

        // 1. Respiratory / Airway check (Highest Priority)
        if (assessment.isBreathing == false) {
            items.add(
                BystanderGuidanceItem(
                    id = "breathing_critical",
                    priority = 1,
                    title = "NOT BREATHING — IMMEDIATE CPR NEEDED",
                    bulletPoints = listOf(
                        "Call national emergency services (112) immediately.",
                        "Place hands in center of chest and push hard and fast (100–120 compressions/min).",
                        "Ensure mouth and airway are not obstructed by vomit, tongue, or debris.",
                        "Do not stop chest compressions until emergency responders take over."
                    ),
                    isCritical = true
                )
            )
        }

        // 2. Consciousness check
        if (assessment.isConscious == false && assessment.isBreathing != false) {
            items.add(
                BystanderGuidanceItem(
                    id = "unconscious_care",
                    priority = 1,
                    title = "UNCONSCIOUS PERSON CARE",
                    bulletPoints = listOf(
                        "Call emergency services (112) immediately.",
                        "Continuously check and listen for regular breathing.",
                        "Do NOT move the rider's neck or body unless they are in direct hazard (fire or moving traffic).",
                        "Do NOT give fluids or food under any circumstance."
                    ),
                    isCritical = true
                )
            )
        }

        // 3. Bleeding check
        if (assessment.hasHeavyBleeding == true) {
            items.add(
                BystanderGuidanceItem(
                    id = "bleeding_control",
                    priority = 2,
                    title = "SEVERE BLEEDING CONTROL",
                    bulletPoints = listOf(
                        "Apply firm, continuous, direct pressure over the bleeding site with a clean cloth, jacket, or bandage.",
                        "Maintain steady hand pressure — do not remove soaked cloth; add more layers on top.",
                        "Keep pressure applied until medical personnel arrive."
                    ),
                    isCritical = false
                )
            )
        }

        // 4. Suspected spinal / serious injury
        if (assessment.hasSeriousInjury == true) {
            items.add(
                BystanderGuidanceItem(
                    id = "spinal_precaution",
                    priority = 2,
                    title = "SUSPECTED SPINAL / SEVERE INJURY",
                    bulletPoints = listOf(
                        "DO NOT move the person or try to make them stand or sit up.",
                        "DO NOT remove their helmet unless airway is obstructed and CPR is required (removing helmet risks spinal paralysis).",
                        "Hold their head still with both hands to prevent movement if safe to do so."
                    ),
                    isCritical = false
                )
            )
        }

        // 5. If all answered NO/favorable (Conscious, Breathing, No heavy bleeding, No serious injury)
        if (assessment.isConscious == true &&
            assessment.isBreathing == true &&
            assessment.hasHeavyBleeding == false &&
            assessment.hasSeriousInjury == false
        ) {
            items.add(
                BystanderGuidanceItem(
                    id = "stable_monitoring",
                    priority = 3,
                    title = "PERSON APPEARS STABLE",
                    bulletPoints = listOf(
                        "Keep the person resting, warm, and calm.",
                        "Watch for delayed shock, dizziness, internal injury, or confusion.",
                        "Contact their emergency contacts below to inform them of the incident."
                    ),
                    isCritical = false
                )
            )
        }

        // Core trauma rules always present for bystander safety
        items.add(
            BystanderGuidanceItem(
                id = "essential_first_aid",
                priority = 4,
                title = "CORE FIRST-AID RULES",
                bulletPoints = listOf(
                    "Do NOT unnecessarily move someone after a suspected crash.",
                    "Apply firm, steady pressure to any active bleeding.",
                    "Seek professional medical help (Dial 112)."
                ),
                isCritical = false
            )
        )

        return items.sortedBy { it.priority }
    }
}

/**
 * Navigation steps for the rapid-response action-first bystander flow.
 */
enum class BystanderTriageStep {
    EMERGENCY_PROMPT,   // Screen 1: Call 112 dominant + Start Quick Check
    CONSCIOUSNESS,      // Screen 2: Is the rider conscious? + Illustration + YES / NO
    BREATHING,          // Screen 3: Are they breathing normally? + Illustration + YES / NO
    HEAVY_BLEEDING,     // Screen 4: Is there heavy bleeding? + Illustration + YES / NO
    SERIOUS_INJURY,     // Screen 5: Possible serious injury? + Illustration + YES / NO
    ACTION_SUMMARY      // Results Screen: Immediate first-aid actions, Call 112, DO NOW / DO NOT, Medical Info, Contacts
}

/**
 * UI State for the Bystander screen.
 */
data class BystanderUiState(
    val currentStep: BystanderTriageStep = BystanderTriageStep.EMERGENCY_PROMPT,
    val assessment: BystanderAssessment = BystanderAssessment(),
    val guidance: List<BystanderGuidanceItem> = BystanderGuidanceResolver.resolve(BystanderAssessment()),
    val emergencyContacts: List<Contact> = emptyList(),
    val medicalProfile: MedicalProfile? = null,
    val isEmergencyAlertTriggered: Boolean = false,
    val alertStatusMessage: String? = null
)
