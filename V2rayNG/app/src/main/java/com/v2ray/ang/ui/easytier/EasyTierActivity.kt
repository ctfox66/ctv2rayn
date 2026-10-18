package com.v2ray.ang.ui.easytier

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.launch

class EasyTierActivity : BaseComponentActivity() {

    private val viewModel: EasyTierViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.registerStatusReceiver(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshConfig()
                viewModel.requestStatus()
            }
        }
    }

    override fun onDestroy() {
        viewModel.unregisterStatusReceiver(this)
        super.onDestroy()
    }

    @Composable
    override fun ScreenContent() {
        EasyTierScreen(
            viewModel = viewModel,
            onBackClick = { finish() }
        )
    }
}
