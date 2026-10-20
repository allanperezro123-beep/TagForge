package com.tagforge.app.feature.tools.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.getOrNull
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.security.AuthLimitInfo
import com.tagforge.domain.security.AuthReport
import com.tagforge.domain.security.AuthenticateUseCase
import com.tagforge.domain.security.ConfigLockRequest
import com.tagforge.domain.security.ConfigLockUseCase
import com.tagforge.domain.security.ConfirmedPlan
import com.tagforge.domain.security.LockRequest
import com.tagforge.domain.security.LockTagUseCase
import com.tagforge.domain.security.Password
import com.tagforge.domain.security.PasswordInput
import com.tagforge.domain.security.PasswordRequest
import com.tagforge.domain.security.ReadAuthLimitUseCase
import com.tagforge.domain.security.ReadSecurityStateUseCase
import com.tagforge.domain.security.RemovePasswordUseCase
import com.tagforge.domain.security.SecretKind
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.security.SecurityPlan
import com.tagforge.domain.security.SecurityReport
import com.tagforge.domain.security.SecurityState
import com.tagforge.domain.security.SetPasswordUseCase

/** Turns what the user typed into domain requests and operations. Pure: no tag is touched here. */
internal object SecurityPlans {

    /** A problem found in [form] before any tag is touched, or null. */
    fun validate(action: SecurityAction, form: SecurityForm): FormProblem? {
        val currentTyped = form.currentPassword.isNotBlank()
        val currentValid = current(form) != null
        return when (action) {
            SecurityAction.SET_PASSWORD -> when {
                password(form) == null -> FormProblem.PASSWORD_INVALID
                pack(form) == null -> FormProblem.PACK_INVALID
                !form.storedAck -> FormProblem.STORED_NOT_ACKNOWLEDGED
                currentTyped && !currentValid -> FormProblem.CURRENT_PASSWORD_INVALID
                else -> null
            }
            SecurityAction.REMOVE_PASSWORD -> if (currentValid) null else FormProblem.CURRENT_PASSWORD_INVALID
            SecurityAction.UNLOCK -> when {
                !currentValid -> FormProblem.CURRENT_PASSWORD_INVALID
                form.expectedPack.isNotBlank() && expectedPack(form) == null -> FormProblem.PACK_INVALID
                else -> null
            }
            SecurityAction.CONFIG_LOCK -> when {
                !form.lockConfig && form.authLimit == 0 -> FormProblem.NOTHING_SELECTED
                currentTyped && !currentValid -> FormProblem.CURRENT_PASSWORD_INVALID
                else -> null
            }
            SecurityAction.LOCK -> if (currentTyped && !currentValid) FormProblem.CURRENT_PASSWORD_INVALID else null
        }
    }

    private fun password(form: SecurityForm): Password? = PasswordInput.password(form.mode, form.password).getOrNull()

    private fun pack(form: SecurityForm): Bytes? = PasswordInput.parse(form.mode, SecretKind.PACK, form.pack).getOrNull()

    private fun expectedPack(form: SecurityForm): Bytes? = PasswordInput.parse(form.mode, SecretKind.PACK, form.expectedPack).getOrNull()

    private fun current(form: SecurityForm): Password? = PasswordInput.password(form.mode, form.currentPassword).getOrNull()

    /** The read that comes first: it gives the exact current content the dry-run is built from. */
    fun readOperation(form: SecurityForm): TagOperation<SecurityState> = ReadSecurityStateUseCase(current(form))

    fun limitProbe(): TagOperation<AuthLimitInfo> = ReadAuthLimitUseCase()

    fun authenticate(form: SecurityForm): TagOperation<AuthReport> = AuthenticateUseCase(current(form)!!, expectedPack(form))

    fun plan(action: SecurityAction, form: SecurityForm, state: SecurityState, advanced: Boolean): Outcome<SecurityPlan, SecurityFailure> =
        when (action) {
            SecurityAction.SET_PASSWORD -> SetPasswordUseCase.plan(
                state,
                PasswordRequest(password(form)!!, pack(form)!!, form.auth0, form.protectReads, current(form)),
            )
            SecurityAction.REMOVE_PASSWORD -> RemovePasswordUseCase.plan(state, current(form)!!)
            SecurityAction.CONFIG_LOCK -> ConfigLockUseCase.plan(state, ConfigLockRequest(form.lockConfig, form.authLimit, current(form)))
            SecurityAction.LOCK -> LockTagUseCase.plan(state, LockRequest(form.lockScope, advanced, current(form)))
            SecurityAction.UNLOCK -> Outcome.Err(SecurityFailure.NothingToChange)
        }

    fun execution(action: SecurityAction, confirmed: ConfirmedPlan): TagOperation<SecurityReport> = when (action) {
        SecurityAction.SET_PASSWORD -> SetPasswordUseCase(confirmed)
        SecurityAction.REMOVE_PASSWORD -> RemovePasswordUseCase(confirmed)
        SecurityAction.CONFIG_LOCK -> ConfigLockUseCase(confirmed)
        SecurityAction.LOCK -> LockTagUseCase(confirmed)
        SecurityAction.UNLOCK -> error("Unlock has no plan to execute")
    }
}
