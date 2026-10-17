package rs.budget.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SharedReadsTest {
    @Test fun `simultaneous retries restart a failed read only once`() = runTest {
        var attempts = 0
        val shared = flow {
            if (attempts++ == 0) throw IOException("unavailable")
            delay(1)
            emit(42)
        }.shareReads(backgroundScope)
        assertTrue(runCatching { shared.first() }.exceptionOrNull() is IOException)
        assertEquals(List(8) { 42 }, List(8) { async { shared.first() } }.awaitAll())
        assertEquals(2, attempts)
    }

    @Test fun `shared read failure reaches consumer and next subscription recovers`() = runTest {
        var attempts = 0
        val shared = flow {
            if (attempts++ == 0) throw IOException("unavailable")
            emit(42)
        }.shareReads(backgroundScope)

        assertTrue(runCatching { shared.first() }.exceptionOrNull() is IOException)
        assertEquals(42, shared.first())
        assertEquals(2, attempts)
    }
}
