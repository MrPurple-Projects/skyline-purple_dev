/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright © 2022 Skyline Team and Contributors (https://github.com/skyline-emu/)
 */

package emu.skyline.preference

import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import dagger.hilt.android.AndroidEntryPoint
import emu.skyline.R
import emu.skyline.adapter.GenericListItem
import emu.skyline.adapter.GameLocationViewItem
import emu.skyline.adapter.GenericAdapter
import emu.skyline.adapter.SpacingItemDecoration
import emu.skyline.databinding.GameLocationsActivityBinding
import emu.skyline.di.getSettings
import emu.skyline.utils.*
import javax.inject.Inject

/**
 * This activity is used to manage the selected game locations
 */
@AndroidEntryPoint
class GameLocationsActivity : AppCompatActivity() {
    private val binding by lazy { GameLocationsActivityBinding.inflate(layoutInflater) }

    private val adapter = GenericAdapter()

    @Inject
    lateinit var preferenceSettings : PreferenceSettings

    /**
     * The callback called after a user picked a driver to install.
     */
    private val addCallback = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {

            populateAdapter()
        }
    }

    /**
     * Updates the [adapter] with the current list of installed drivers.
     */
    private fun populateAdapter() {
        val items : MutableList<GenericListItem<out ViewBinding>> = ArrayList()

        var gameLocations = GameDataHandler().getGamesLocations(this)

        // Insert the system driver entry at the top of the list.
        for(location in gameLocations) {
            items.add(GameLocationViewItem(location).apply {
                onDelete = { path ->
                    GameDataHandler().deleteGameLocation(this@GameLocationsActivity, path)
                    preferenceSettings.refreshRequired = true
                    populateAdapter()
                }
            })
        }

        adapter.setItems(items)
    }

    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)

        val documentPicker = (this as ComponentActivity).registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let { uri ->
                Log.i("se recibe Uri", uri.toString())
                GameDataHandler().setGamesLocation(this, uri.toString())
                this.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                this.getSettings().refreshRequired = true
                populateAdapter()
            }
        }

        binding.addLocationButton.setOnClickListener { documentPicker.launch(null) }

        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsHelper.applyToActivity(binding.root, binding.romLocationsList)
        WindowInsetsHelper.addMargin(binding.addLocationButton, bottom = true)

        setSupportActionBar(binding.titlebar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.search_location_config)

        val layoutManager = LinearLayoutManager(this)
        binding.romLocationsList.layoutManager = layoutManager
        binding.romLocationsList.adapter = adapter

        var layoutDone = false // Tracks if the layout is complete to avoid retrieving invalid attributes
        binding.coordinatorLayout.viewTreeObserver.addOnTouchModeChangeListener { isTouchMode ->
            val layoutUpdate = {
                val params = binding.romLocationsList.layoutParams as CoordinatorLayout.LayoutParams
                if (!isTouchMode) {
                    binding.titlebar.appBarLayout.setExpanded(true)
                    params.height = binding.coordinatorLayout.height - binding.titlebar.toolbar.height
                } else {
                    params.height = CoordinatorLayout.LayoutParams.MATCH_PARENT
                }

                binding.romLocationsList.layoutParams = params
                binding.romLocationsList.requestLayout()
            }

            if (!layoutDone) {
                binding.coordinatorLayout.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        // We need to wait till the layout is done to get the correct height of the toolbar
                        binding.coordinatorLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        layoutUpdate()
                        layoutDone = true
                    }
                })
            } else {
                layoutUpdate()
            }
        }

        binding.romLocationsList.addItemDecoration(SpacingItemDecoration(resources.getDimensionPixelSize(R.dimen.grid_padding)))

        binding.romLocationsList.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                type = "application/zip"
            }
            addCallback.launch(intent)
        }

        populateAdapter()
    }
}