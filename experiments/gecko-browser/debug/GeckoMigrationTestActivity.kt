package com.example.browser.gecko

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.ui.screens.OrbitBrowserStore
import org.mozilla.geckoview.GeckoView

/** Debug-only host using the real app coordinator, services, engine and extension. */
class GeckoMigrationTestActivity : ComponentActivity() {
    internal lateinit var services: GeckoBrowserServices
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = OrbitBrowserStore(applicationContext)
        store.saveTabs(emptyList(), 1)
        services = GeckoBrowserServices(this, activityResultRegistry, store) { id -> services.coordinator.closeTab(id) }
        services.bind(this, activityResultRegistry)
        val view = GeckoView(this)
        setContentView(view)
        services.coordinator.controller.attach(view)
    }
    override fun onDestroy() { if (::services.isInitialized) services.close(); super.onDestroy() }
}
