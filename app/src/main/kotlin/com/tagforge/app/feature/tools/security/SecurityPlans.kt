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
import com.tagforge.domain.security.DiversifiedPassword
import com.tagforge.domain.security.LockRequest
import com.tagforge.domain.security.LockTagUseCase
import com.tagforge.domain.security.Password
import com.tagforge.domain.security.PasswordInput
import com.tagforge.domain.security.PasswordSource
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
import kotlin.random.Random

/** Turns what the user typed into domain requests and operations. Pure: no tag is touched here. */
internal object SecurityPlans {

    /** A problem found in [form] before any tag is touched, or null. */
    fun validate(action: SecurityAction, form: SecurityForm): FormProblem? {
        val currentTyped = form.currentPassword.isNotBlank()
        val currentValid = current(form) != null
        return when (action) {
            SecurityAction.SET_PASSWORD -> when {
                form.derive && DiversifiedPassword.check(form.passphrase) != null -> FormProblem.PASSPHRASE_INVALID
                !form.derive && password(form) == null -> FormProblem.PASSWORD_INVALID
                !form.derive && form.pack.isNotBlank() && pack(form) == null -> FormProblem.PACK_INVALID
                !form.storedAck -> FormProblem.STORED_NOT_ACKNOWLEDGED
                currentTyped && !currentValid -> FormProblem.CURRENT_PASSWORD_INVALID
                else -> null
            }
            SecurityAction.REMOVE_PASSWORD -> if (currentValid) null else FormProblem.CURRENT_PASSWORD_INVALID
            SecurityAction.UNLOCK -> when {
                form.derive -> if (DiversifiedPassword.check(form.passphrase) != null) FormProblem.PASSPHRASE_INVALID else null
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

    fun password(form: SecurityForm): Password? = PasswordInput.password(form.mode, form.password).getOrNull()

    fun pack(form: SecurityForm): Bytes? = PasswordInput.parse(form.mode, SecretKind.PACK, form.pack).getOrNull()

    fun expectedPack(form: SecurityForm): Bytes? = PasswordInput.parse(form.mode, SecretKind.PACK, form.expectedPack).getOrNull()

    fun current(form: SecurityForm): Password? = PasswordInput.password(form.mode, form.currentPassword).getOrNull()

    /** The read that comes first: it gives the exact current content the dry-run is built from. */
    fun readOperation(form: SecurityForm): TagOperation<SecurityState> = ReadSecurityStateUseCase(current(form))

    fun limitProbe(): TagOperation<AuthLimitInfo> = ReadAuthLimitUseCase()

    /** Typed: the same password for every tag. Derived: calculated at tap time for the UID that answers (WU4). */
    fun authenticate(form: SecurityForm): TagOperation<AuthReport> {
        val source = if (form.derive) PasswordSource.Diversified(form.passphrase) else PasswordSource.Typed(current(form)!!)
        return AuthenticateUseCase(source, expectedPack(form))
    }

    /** The password and PACK a Set password will write. */
    class NewCredential(val password: Password, val pack: Bytes)

    /**
     * Resolved once the tag is known: derived for its [uid], or the typed password with the typed PACK, or - when the PACK is
     * blank - two random bytes drawn from [random]. Null when the form cannot give a valid credential (checked before the tap).
     */
    fun newCredential(form: SecurityForm, uid: Bytes, random: Random): NewCredential? {
        if (form.derive) {
            val (password, pack) = DiversifiedPassword.deriveChecked(form.passphrase, uid).getOrNull() ?: return null
            return NewCredential(password, pack)
        }
        val password = password(form) ?: return null
        val pack = PasswordInput.packOrRandom(form.mode, form.pack, random).getOrNull() ?: return null
        return NewCredential(password, pack)
    }

    fun plan(
        action: SecurityAction,
        form: SecurityForm,
        state: SecurityState,
        advanced: Boolean,
        credential: NewCredential? = null,
    ): Outcome<SecurityPlan, SecurityFailure> =
        when (action) {
            SecurityAction.SET_PASSWORD -> SetPasswordUseCase.plan(
                state,
                PasswordRequest(credential!!.password, credential.pack, form.auth0, form.protectReads, current(form)),
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
