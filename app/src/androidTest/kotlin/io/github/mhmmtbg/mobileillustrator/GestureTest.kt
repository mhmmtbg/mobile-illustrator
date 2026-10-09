package io.github.mhmmtbg.mobileillustrator

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** İki parmak hareketleri: adb ile gönderilemediği için burada, gerçek dokunma olaylarıyla denenir. */
@RunWith(AndroidJUnit4::class)
class GestureTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun vm(): EditorViewModel {
        var out: EditorViewModel? = null
        rule.activityRule.scenario.onActivity { out = ViewModelProvider(it)[EditorViewModel::class.java] }
        return out!!
    }

    private fun settle() {
        rule.waitForIdle()
        rule.waitUntil(15_000) { vm().state.busy == null }
        rule.waitForIdle()
    }

    @Test
    fun pinchZoomsAroundTheFingersAndNeverDraws() {
        settle()
        val vm = vm()
        // Çizim aracı seçiliyken bile iki parmak yalnızca görünümü değiştirmeli.
        rule.onNodeWithContentDescription("Dikdörtgen").performClick()
        rule.waitForIdle()
        val before = vm.viewport
        val nodesBefore = vm.state.history.present.layers.sumOf { it.children.size }
        rule.onNodeWithContentDescription("Tuval").performTouchInput {
            val c = center
            pinch(
                start0 = c + Offset(-40f, 0f), end0 = c + Offset(-240f, 0f),
                start1 = c + Offset(40f, 0f), end1 = c + Offset(240f, 0f),
                durationMillis = 400,
            )
        }
        rule.waitForIdle()
        val after = vm.viewport
        assertTrue("yakınlaştırma artmalı: ${before.scale} -> ${after.scale}", after.scale > before.scale * 2.5f)
        assertEquals("iki parmak hareketi nesne çizmemeli", nodesBefore, vm.state.history.present.layers.sumOf { it.children.size })
        assertFalse("iki parmak hareketi geçmişe kayıt düşmemeli", vm.state.history.canUndo)
        assertEquals(null, vm.state.preview)

        // Uzaklaştırma
        rule.onNodeWithContentDescription("Tuval").performTouchInput {
            val c = center
            pinch(
                start0 = c + Offset(-240f, 0f), end0 = c + Offset(-60f, 0f),
                start1 = c + Offset(240f, 0f), end1 = c + Offset(60f, 0f),
                durationMillis = 400,
            )
        }
        rule.waitForIdle()
        assertTrue("uzaklaştırma azaltmalı", vm.viewport.scale < after.scale * 0.5f)
    }

    @Test
    fun twoFingerDragPansWithoutZooming() {
        settle()
        val vm = vm()
        val before = vm.viewport
        rule.onNodeWithContentDescription("Tuval").performTouchInput {
            val c = center
            // İki parmak aynı yönde ve aynı aralıkla: yalnızca kaydırma
            pinch(
                start0 = c + Offset(-80f, 0f), end0 = c + Offset(-80f, 200f),
                start1 = c + Offset(80f, 0f), end1 = c + Offset(80f, 200f),
                durationMillis = 400,
            )
        }
        rule.waitForIdle()
        val after = vm.viewport
        assertEquals("ölçek değişmemeli", before.scale, after.scale, before.scale * 0.03f)
        assertTrue("görünüm aşağı kaymalı: ${before.offset.y} -> ${after.offset.y}", after.offset.y - before.offset.y > 150f)
        assertEquals(before.offset.x, after.offset.x, 12f)
    }

    @Test
    fun secondFingerCancelsAnInProgressShape() {
        settle()
        val vm = vm()
        rule.onNodeWithContentDescription("Dikdörtgen").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Tuval").performTouchInput {
            val c = center
            down(0, c + Offset(-150f, -150f))
            moveTo(0, c + Offset(50f, 50f))
            // Çizim sürerken ikinci parmak iner: yarım kalan şekil atılmalı.
            down(1, c + Offset(150f, 150f))
            moveTo(1, c + Offset(200f, 200f))
            up(1)
            up(0)
        }
        rule.waitForIdle()
        assertEquals(0, vm.state.history.present.layers.sumOf { it.children.size })
        assertEquals(null, vm.state.preview)
        assertFalse(vm.state.history.canUndo)
    }
}
