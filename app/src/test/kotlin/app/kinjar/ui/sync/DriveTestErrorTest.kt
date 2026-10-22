package app.kinjar.ui.sync

import app.kinjar.data.sync.drive.DriveException
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveTestErrorTest {
    @Test
    fun `account identity failure is distinguishable without exposing exception details`() {
        assertEquals("ACCOUNT_IDENTITY_MISSING", driveTestError(
            DriveException.AuthRequired("private details", "ACCOUNT_IDENTITY_MISSING"),
        ))
    }

    @Test
    fun `Google status survives result parsing and request failures`() {
        assertEquals("GOOGLE_RESULT_ERROR (Google status 10)", driveTestError(
            DriveException.AuthRequired("private details", "GOOGLE_RESULT_ERROR", 10),
        ))
        assertEquals("GOOGLE_AUTH_ERROR (Google status 7)", driveTestError(DriveException.AuthorizationFailed(7)))
        assertEquals("IllegalStateException", driveTestError(IllegalStateException("private details")))
    }
}
