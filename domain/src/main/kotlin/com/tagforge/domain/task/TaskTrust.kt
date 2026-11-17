package com.tagforge.domain.task

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.settings.TaskExecutionPolicy
import java.security.MessageDigest

/**
 * SHA-256 (lower-case hex) of a task's compact encoding ([TaskCodec]). Name and every action parameter are part of it, so
 * a task that was edited by one byte is a different task.
 */
object TaskFingerprint {
    fun of(name: String?, actions: List<TaskAction>): String? =
        when (val encoded = TaskCodec.encode(TaskPayload(name, actions))) {
            is Outcome.Err -> null
            is Outcome.Ok -> MessageDigest.getInstance("SHA-256").digest(encoded.value.toByteArray()).joinToString("") { "%02x".format(it) }
        }

    fun of(plan: TaskPlan): String? = of(plan.name, plan.items.map { it.action })
}

/**
 * The only way a task skips the confirmation sheet. Tag content is untrusted (anyone can leave a sticker), so skipping is
 * opt-in through [TaskExecutionPolicy] and the token it yields is still made by [TaskPlan.confirm], marked
 * [ConfirmationSource.POLICY]. This never touches tag security operations: those have their own typed confirmation.
 */
object TaskTrust {

    /** More than this many remembered tasks drops the oldest: the set must not grow without bound. */
    const val MAX_TRUSTED = 500

    /** Remembers a task created on this phone (written to a tag or saved). */
    fun trust(settings: AppSettings, name: String?, actions: List<TaskAction>): AppSettings {
        val fingerprint = TaskFingerprint.of(name, actions) ?: return settings
        val kept = (settings.trustedTaskFingerprints - fingerprint) + fingerprint
        return settings.copy(trustedTaskFingerprints = kept.toList().takeLast(MAX_TRUSTED).toSet())
    }

    fun untrust(settings: AppSettings, fingerprint: String): AppSettings =
        settings.copy(trustedTaskFingerprints = settings.trustedTaskFingerprints - fingerprint)

    fun isTrusted(settings: AppSettings, plan: TaskPlan): Boolean =
        TaskFingerprint.of(plan)?.let { it in settings.trustedTaskFingerprints } == true

    /**
     * The token that runs [plan] without showing the sheet, or null when the user must be asked.
     *
     * - `ASK_ALWAYS`: never skips the sheet.
     * - `OWN_TASKS`: skips it only for a trusted plan (written or saved on this phone) with no blocked action and no missing
     *   grant. A plan with plain HTTP is never skipped; a plan whose only warnings are [ActionWarning.LOCAL_NETWORK] is, because
     *   the user wrote it here (a home-automation webhook on their own network, say).
     * - `NEVER_ASK`: skips it for a plan without warnings, as the user asked. A plan WITH any warning is skipped only when it is
     *   trusted: tag content and intents from other apps are untrusted, and a request into the user's own network or over plain
     *   HTTP is exactly what a planted sticker would carry, so for those the sheet, the warning and the second tap still apply.
     */
    fun autoConfirm(settings: AppSettings, plan: TaskPlan): ConfirmedTask? {
        if (plan.items.isEmpty()) return null
        return when (settings.taskExecution) {
            TaskExecutionPolicy.ASK_ALWAYS -> null
            TaskExecutionPolicy.NEVER_ASK ->
                if (!plan.requiresSecondTap || isTrusted(settings, plan)) policyConfirm(plan) else null
            TaskExecutionPolicy.OWN_TASKS -> {
                val runnable = plan.items.none { it.isBlocked || it.capability is Capability.NeedsGrant || it.warning == ActionWarning.INSECURE_HTTP }
                if (runnable && isTrusted(settings, plan)) policyConfirm(plan) else null
            }
        }
    }

    /** The policy stands in for the second tap: the warnings of an auto-run plan are accepted by the user's own choice. */
    private fun policyConfirm(plan: TaskPlan): ConfirmedTask? =
        plan.confirm(acknowledgedInsecure = true, source = ConfirmationSource.POLICY)
}
