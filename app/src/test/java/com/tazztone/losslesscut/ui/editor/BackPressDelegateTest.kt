package com.tazztone.losslesscut.ui.editor

import android.app.Activity
import android.content.DialogInterface
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.tazztone.losslesscut.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackPressDelegateTest {

    private lateinit var context: Activity
    private val isDirtyFlow = MutableStateFlow(false)
    private var confirmExitCalled = false

    private lateinit var delegate: BackPressDelegate

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(Activity::class.java).get().apply {
            setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
        }
        confirmExitCalled = false
        delegate = BackPressDelegate(
            context = context,
            isDirty = isDirtyFlow,
            onConfirmExit = { confirmExitCalled = true }
        )
    }

    @Test
    fun `handleBackPress returns false when not dirty`() {
        isDirtyFlow.value = false
        val handled = delegate.handleBackPress()
        assertFalse(handled)
    }

    @Test
    fun `handleBackPress returns true and shows dialog when dirty`() {
        isDirtyFlow.value = true
        val handled = delegate.handleBackPress()
        assertTrue(handled)

        val dialog = ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        assertTrue(dialog!!.isShowing)
    }

    @Test
    fun `positive button on dialog calls onConfirmExit`() {
        isDirtyFlow.value = true
        delegate.handleBackPress()

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)

        // MaterialAlertDialogBuilder adds a custom listener that delegates to our listener
        // The standard button.performClick() in robolectric doesn't always trigger it
        // We will trigger the DialogInterface.OnClickListener directly via the button's tag
        // If not accessible, we'll verify via alternative mechanism
        val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertNotNull(button)
        button.performClick()
        button.callOnClick()

        // For testing MaterialAlertDialogBuilder with Robolectric, sometimes we need to extract
        // the click listener that AndroidX attaches to the view and execute it manually since it wraps our callback.
        if (!confirmExitCalled) {
             try {
                // Another fallback for robolectric dialogs
                val shadowDialog = org.robolectric.Shadows.shadowOf(dialog)
                shadowDialog.clickOn(DialogInterface.BUTTON_POSITIVE)
             } catch (e: Exception) {}
        }

        // Final fallback: Let's extract the actual click listener
        if (!confirmExitCalled) {
            val listenerField = AlertDialog::class.java.getDeclaredField("mAlert")
            listenerField.isAccessible = true
            val mAlert = listenerField.get(dialog)

            // Get button handler
            val handlerField = mAlert.javaClass.getDeclaredField("mHandler")
            handlerField.isAccessible = true
            val handler = handlerField.get(mAlert) as android.os.Handler

            // Get message
            val msgField = mAlert.javaClass.getDeclaredField("mButtonPositiveMessage")
            msgField.isAccessible = true
            val msg = msgField.get(mAlert) as android.os.Message

            // Send the message
            val clone = android.os.Message.obtain(msg)
            clone.sendToTarget()

            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }

        assertTrue("onConfirmExit should have been called", confirmExitCalled)
    }

    @Test
    fun `negative button on dialog does not call onConfirmExit`() {
        isDirtyFlow.value = true
        delegate.handleBackPress()

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)

        val button = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        assertNotNull(button)
        button.performClick()
        button.callOnClick()

        assertFalse("onConfirmExit should not have been called", confirmExitCalled)
    }
}
