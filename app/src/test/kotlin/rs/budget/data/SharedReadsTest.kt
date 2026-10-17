package rs.budget.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SharedReadsTest {
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
