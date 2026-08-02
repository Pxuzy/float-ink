package com.pxuzy.floatingpen

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FloatInkIconViewTest {
    @Test
    fun `fibonacci icon exposes its measured tool surface`() {
        val icon = FloatInkIconView(ApplicationProvider.getApplicationContext(), "fibonacci")
        icon.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(48, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(48, android.view.View.MeasureSpec.EXACTLY),
        )
        icon.layout(0, 0, 48, 48)
        assertEquals(48, icon.measuredWidth)
        assertEquals(48, icon.measuredHeight)
    }
}
