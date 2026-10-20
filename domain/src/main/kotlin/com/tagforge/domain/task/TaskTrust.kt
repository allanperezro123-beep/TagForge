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

    /** The token that runs [plan] without showing the sheet, or null when the user must be asked. */
    fun autoConfirm(settings: AppSettings, plan: TaskPlan): ConfirmedTask? {
        if (plan.items.isEmpty()) return null
        return when (settings.taskExecution) {
            TaskExecutionPolicy.ASK_ALWAYS -> null
            // The user accepted the risk of running anything, plain HTTP included.
            TaskExecutionPolicy.NEVER_ASK -> plan.confirm(acknowledgedInsecure = true, source = ConfirmationSource.POLICY)
            TaskExecutionPolicy.OWN_TASKS -> {
                val clean = !plan.requiresSecondTap && plan.items.none { it.isBlocked || it.capability is Capability.NeedsGrant }
                if (clean && isTrusted(settings, plan)) plan.confirm(acknowledgedInsecure = false, source = ConfirmationSource.POLICY) else null
            }
        }
    }
}
