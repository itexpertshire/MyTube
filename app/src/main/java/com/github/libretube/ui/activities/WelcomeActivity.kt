package com.github.libretube.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.R
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.databinding.ActivityWelcomeBinding
import com.github.libretube.helpers.BackupHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.adapters.InstancesAdapter
import com.github.libretube.ui.base.BaseActivity
import com.github.libretube.ui.models.WelcomeModel
import com.github.libretube.ui.preferences.BackupRestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WelcomeActivity : BaseActivity() {
    private lateinit var binding: ActivityWelcomeBinding
    private var viewModel: WelcomeModel? = null

    private val restoreFilePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            CoroutineScope(Dispatchers.IO).launch {
                BackupHelper.restoreAdvancedBackup(this@WelcomeActivity, uri)

                // only skip the welcome activity if the restored backup contains an instance
                if (PreferenceHelper.getString(PreferenceKeys.FETCH_INSTANCE, "").isNotEmpty()) {
                    withContext(Dispatchers.Main) { startMainActivity() }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get()

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ALl the binding values are optional due to two different possible layouts (normal, landscape)
        viewModel!!.instances.observe(this) { instances ->
            binding.instancesRecycler?.layoutManager = LinearLayoutManager(this@WelcomeActivity)
            binding.instancesRecycler?.adapter = InstancesAdapter(instances, viewModel!!) { index ->
                viewModel!!.selectedInstanceIndex.value = index
                binding.okay?.alpha = 1f
            }
            binding.progress?.isGone = true
        }
        viewModel!!.fetchInstances(this)

        binding.okay?.alpha = if (viewModel!!.selectedInstanceIndex.value != null) 1f else 0.5f
        binding.okay?.setOnClickListener {
            if (viewModel!!.selectedInstanceIndex.value != null) {
                val selectedInstance =
                    viewModel!!.instances.value!![viewModel!!.selectedInstanceIndex.value!!]
                PreferenceHelper.putString(PreferenceKeys.FETCH_INSTANCE, selectedInstance.apiUrl)
                startMainActivity()
            } else {
                Toast.makeText(this, R.string.choose_instance, Toast.LENGTH_LONG).show()
            }
        }

        binding.restore?.setOnClickListener {
            restoreFilePicker.launch(BackupRestoreSettings.JSON)
        }
    }

    private fun startMainActivity() {
        val mainActivityIntent = Intent(this@WelcomeActivity, MainActivity::class.java)
        startActivity(mainActivityIntent)
        finish()
    }
}
