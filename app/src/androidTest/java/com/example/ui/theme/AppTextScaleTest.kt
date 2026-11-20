package com.example.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.example.data.AppTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AppTextScaleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun textUpdatesInTheOpenScreenAndNestedBrowserTheme() {
        val selected = mutableIntStateOf(AppTextSize.DEFAULT)
        val system = mutableFloatStateOf(1f)
        compose.setContent {
            val deviceDensity = Density(LocalDensity.current.density, system.floatValue)
            CompositionLocalProvider(LocalDensity provides deviceDensity) {
                val scale = AppTextSize.scale(selected.intValue)
                HiPlayerTheme(uiTextScale = scale) {
                    Column {
                        Text("Library", Modifier.testTag("root"), style = MaterialTheme.typography.bodyLarge)
                        HiPlayerTheme(uiTextScale = scale) {
                            Text("Library", Modifier.testTag("nested"), style = MaterialTheme.typography.bodyLarge)
                        }
                        AppDialog(onDismissRequest = {}) {
                            Text("Library", Modifier.testTag("dialog"), style = MaterialTheme.typography.bodyLarge)
                        }
                        Text("Player controls", Modifier.testTag("player"), fontSize = 16.sp)
                    }
                }
            }
        }

        fun width(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().size.width
        fun widths(): Pair<Int, Int> {
            val root = width("root")
            assertEquals(root, width("nested"))
            assertEquals(root, width("dialog"))
            return root to width("player")
        }

        val default = widths()
        compose.runOnIdle { selected.intValue = AppTextSize.MIN }
        val small = widths()
        assertTrue(small.first < default.first && small.second < default.second)
        compose.runOnIdle { selected.intValue = AppTextSize.MAX }
        val large = widths()
        assertTrue(large.first > default.first && large.second > default.second)
        compose.runOnIdle { system.floatValue = 1.5f }
        val accessible = widths()
        assertTrue(accessible.first > large.first && accessible.second > large.second)
    }
}
