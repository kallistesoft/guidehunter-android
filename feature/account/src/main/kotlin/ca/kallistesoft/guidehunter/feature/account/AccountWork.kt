package ca.kallistesoft.guidehunter.feature.account

import ca.kallistesoft.guidehunter.core.data.BackendNotConfiguredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What went wrong with the account, said on the screen until dismissed. */
enum class AccountError { SIGN_IN_FAILED, NOT_CONFIGURED, DELETE_FAILED }

/**
 * An account action (sign-in, deletion) at a time: the buttons wait while one runs ([busy]), and a failure shows
 * until dismissed ([error]). A second tap while one runs does nothing.
 */
internal class AccountWork(private val scope: CoroutineScope) {
    private val _busy = MutableStateFlow(false)
    private val _error = MutableStateFlow<AccountError?>(null)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val error: StateFlow<AccountError?> = _error.asStateFlow()

    fun run(failure: AccountError, work: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        scope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackendNotConfiguredException) {
                _error.value = AccountError.NOT_CONFIGURED
            } catch (_: Exception) {
                _error.value = failure
            } finally {
                _busy.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
