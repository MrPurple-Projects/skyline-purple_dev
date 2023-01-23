/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright © 2020 Skyline Team and Contributors (https://github.com/skyline-emu/)
 */

package emu.skyline.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.R

/**
 * This preference is used to launch [GameLocationsActivity] using a preference
 */
class GameLocationsPreference @JvmOverloads constructor(context : Context, attrs : AttributeSet? = null, defStyleAttr : Int = R.attr.preferenceStyle) : Preference(context, attrs, defStyleAttr) {
    private val gameLocationsCallback = (context as ComponentActivity).registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notifyChanged()
    }

    init {
        // TO-DO
    }

    /**
     * This launches [GameLocationsActivity] on click to manage driver packages
     */
    override fun onClick() = gameLocationsCallback.launch(Intent(context, GameLocationsActivity::class.java))
}