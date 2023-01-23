package emu.skyline

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import emu.skyline.loader.AppEntry
import emu.skyline.loader.RomFormat
import emu.skyline.utils.fromFile
import emu.skyline.utils.toFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashMap
import kotlin.collections.List

sealed class MainState {
    object Loading : MainState()
    class Loaded(val items : HashMap<RomFormat, ArrayList<AppEntry>>) : MainState()
    class Error(val ex : Exception) : MainState()
}

@HiltViewModel
class MainViewModel @Inject constructor(@ApplicationContext context : Context, private val romProvider : RomProvider) : AndroidViewModel(context as Application) {
    companion object {
        private val TAG = MainViewModel::class.java.simpleName
    }

    private var state
        get() = _stateData.value
        set(value) = _stateData.postValue(value)
    private val _stateData = MutableLiveData<MainState>()
    val stateData : LiveData<MainState> = _stateData

    /**
     * This refreshes the contents of the adapter by either trying to load cached adapter data or searches for them to recreate a list
     *
     * @param loadFromFile If this is false then trying to load cached adapter data is skipped entirely
     */
    fun loadRoms(context : Context, loadFromFile : Boolean, searchLocations : List<String>, systemLanguage : Int) {
        if (state == MainState.Loading) return
        state = MainState.Loading

        val romsFile = File(getApplication<SkylineApplication>().filesDir.canonicalPath + "/roms.bin")

        viewModelScope.launch(Dispatchers.IO) {
            if (loadFromFile && romsFile.exists()) {
                try {
                    state = MainState.Loaded(fromFile(romsFile))
                    return@launch
                } catch (e : Exception) {
                    Log.w(TAG, "Ran into exception while loading: ${e.message}")
                }
            }

            state = if (searchLocations.isEmpty()) {
                @Suppress("ReplaceWithEnumMap")
                MainState.Loaded(HashMap())
            } else {
                try {
                    var romElements = mutableMapOf<RomFormat, ArrayList<AppEntry>>()
                    for (location in searchLocations) {
                        KeyReader.importFromLocation(context, Uri.parse(location))
                        val roms = romProvider.loadRoms(Uri.parse(location), systemLanguage)
                        romElements = mergeDirectories(romElements, roms)
                    }
                    HashMap(romElements).toFile(romsFile)
                    MainState.Loaded(HashMap(romElements))
                } catch (e : Exception) {
                    Log.w(TAG, "Ran into exception while saving: ${e.message}")
                    MainState.Error(e)
                }
            }
        }
    }

    fun mergeDirectories(
        first: kotlin.collections.MutableMap<RomFormat, ArrayList<AppEntry>>,
        second: kotlin.collections.HashMap<RomFormat, ArrayList<AppEntry>>,
    ) : MutableMap<RomFormat, ArrayList<AppEntry>> {
        for ((key, value) in second) {
            Log.i("value","$key = $value")
            if(!first.containsKey(key)){
                first[key] = ArrayList<AppEntry>()
            }
            for(app in value){
                first[key]?.add(app)
            }
        }
        return first
    }
}
