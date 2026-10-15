package CodeSyS.Random_Number.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStartActionTest {

    @Test
    fun `startActionFor - new session shortcut`() {
        assertEquals(AppStartAction.NEW_SESSION, startActionFor(ACTION_NEW_SESSION))
    }

    @Test
    fun `startActionFor - last session shortcut`() {
        assertEquals(AppStartAction.LAST_SESSION, startActionFor(ACTION_LAST_SESSION))
    }

    @Test
    fun `startActionFor - unknown or missing action is NONE`() {
        assertEquals(AppStartAction.NONE, startActionFor(null))
        assertEquals(AppStartAction.NONE, startActionFor("android.intent.action.MAIN"))
    }
}
