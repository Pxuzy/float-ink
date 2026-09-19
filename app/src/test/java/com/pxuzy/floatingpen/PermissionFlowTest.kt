package com.pxuzy.floatingpen

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionFlowTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        application.getSharedPreferences(OverlayService.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `onboarding notification refusal ends the flow without requesting again`() {
        assertOnboardingNotificationResultEndsFlow(intArrayOf(PackageManager.PERMISSION_DENIED))
    }

    @Test
    fun `cancelled notification request ends the flow without requesting again`() {
        assertOnboardingNotificationResultEndsFlow(intArrayOf())
    }

    private fun assertOnboardingNotificationResultEndsFlow(results: IntArray) {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        activity.callPrivate("maybeShowPermissionGuide")
        ShadowAlertDialog.getLatestAlertDialog()
            .getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val request = shadowOf(activity).lastRequestedPermission
        assertNotNull(request)
        assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions)

        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, results)
        controller.pause().resume()

        assertSame("拒绝或取消后不应再次请求通知权限", request, shadowOf(activity).lastRequestedPermission)
        assertNull("首次引导不应自动启动悬浮服务", shadowOf(activity).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test
    fun `home start still starts foreground service after notification refusal`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        activity.callPrivate("onActionClick")
        val request = shadowOf(activity).lastRequestedPermission
        assertNotNull(request)
        assertNull(shadowOf(activity).nextStartedService)

        activity.onRequestPermissionsResult(
            request.requestCode,
            request.requestedPermissions,
            intArrayOf(PackageManager.PERMISSION_DENIED),
        )

        assertSame(request, shadowOf(activity).lastRequestedPermission)
        val intent = shadowOf(activity).nextStartedService
        assertNotNull(intent)
        assertEquals(OverlayService.ACTION_SHOW_BUBBLE, intent.action)
        assertEquals(OverlayService::class.java.name, intent.component?.className)
        controller.pause().resume()
        assertNull("返回首页不应重复启动服务", shadowOf(activity).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test
    fun `cancelling overlay permission explanation does not reopen it on resume`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        ShadowSettings.setCanDrawOverlays(false)

        activity.callPrivate("onActionClick")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("需要悬浮窗权限", shadowOf(dialog).title.toString())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        controller.pause().resume()

        assertFalse(dialog.isShowing)
        assertSame("取消授权后恢复页面不应重新弹窗", dialog, ShadowAlertDialog.getLatestAlertDialog())
        assertNull(shadowOf(activity).lastRequestedPermission)
        assertNull(shadowOf(activity).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test
    fun `fully authorized home action starts service without requesting permissions`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        activity.callPrivate("onActionClick")

        assertNull(shadowOf(activity).lastRequestedPermission)
        val intent = shadowOf(activity).nextStartedService
        assertNotNull(intent)
        assertEquals(OverlayService.ACTION_SHOW_BUBBLE, intent.action)
        assertEquals(OverlayService::class.java.name, intent.component?.className)
        controller.pause().stop().destroy()
    }

    private fun MainActivity.callPrivate(name: String) {
        javaClass.getDeclaredMethod(name).run {
            isAccessible = true
            invoke(this@callPrivate)
        }
    }
}
