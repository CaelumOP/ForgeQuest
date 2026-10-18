package com.forgequest.app.data

import kotlin.coroutines.cancellation.CancellationException

/** [runCatching] for suspend code: a cancelled caller stays cancelled instead of carrying on with a failure. */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
