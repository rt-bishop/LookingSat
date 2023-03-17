/*
 * Look4Sat. Amateur radio satellite tracker and pass predictor.
 * Copyright (C) 2019-2022 Arty Bishop (bishop.arty@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.rtbishop.look4sat.presentation.passes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rtbishop.look4sat.domain.IDataRepository
import com.rtbishop.look4sat.domain.ILocationSource
import com.rtbishop.look4sat.domain.ISatelliteManager
import com.rtbishop.look4sat.domain.ISettingsSource
import com.rtbishop.look4sat.model.DataState
import com.rtbishop.look4sat.model.GeoPos
import com.rtbishop.look4sat.model.SatPass
import com.rtbishop.look4sat.utility.toTimerString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PassesViewModel @Inject constructor(
    private val satelliteManager: ISatelliteManager,
    private val repository: IDataRepository,
    private val settings: ISettingsSource,
    private val locationSource: ILocationSource
) : ViewModel() {

    private val _passes = MutableStateFlow<DataState<List<SatPass>>>(DataState.Loading)
    private val timerData = Triple("Next - Id:Null", "Name: Null", "00:00:00")
    private val _timerText = MutableStateFlow(timerData)
    private var passesProcessing: Job? = null
    val passes: StateFlow<DataState<List<SatPass>>> = _passes
    val timerText: StateFlow<Triple<String, String, String>?> = _timerText

    fun getHoursAhead() = settings.getHoursAhead()

    fun getMinElevation() = settings.getMinElevation()

    init {
        viewModelScope.launch {
            satelliteManager.calculatedPasses.collect { passes ->
                passesProcessing?.cancelAndJoin()
                passesProcessing = viewModelScope.launch {
                    while (isActive) {
                        val timeNow = System.currentTimeMillis()
                        val newPasses = satelliteManager.processPasses(passes, timeNow)

                        if (newPasses.isNotEmpty()) {
                            try {
                                val nextPass = newPasses.first { it.aosTime.minus(timeNow) > 0 }
                                val catNum = nextPass.catNum
                                val name = nextPass.name
                                val millisBeforeStart = nextPass.aosTime.minus(timeNow)
                                val timerString = millisBeforeStart.toTimerString()
                                _timerText.emit(Triple("Next - Id:$catNum", name, timerString))
                            } catch (e: NoSuchElementException) {
                                val lastPass = newPasses.last()
                                val catNum = lastPass.catNum
                                val name = lastPass.name
                                val millisBeforeEnd = lastPass.losTime.minus(timeNow)
                                val timerString = millisBeforeEnd.toTimerString()
                                _timerText.emit(Triple("Next - Id:$catNum", name, timerString))
                            }
                        } else _timerText.emit(timerData)

                        _passes.emit(DataState.Success(newPasses))
                        delay(1000)
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.updateState.collect { updateState ->
                if (updateState is DataState.Success) calculatePasses()
            }
        }
        calculatePasses()
    }

    fun shouldUseUTC() = settings.isUtcEnabled()

    fun calculatePasses(
        hoursAhead: Int = 1,
        minElevation: Double = settings.getMinElevation(),
        timeRef: Long = System.currentTimeMillis(),
        selection: List<Int>? = null
    ) {
        viewModelScope.launch {
            _passes.emit(DataState.Loading)
//            _timerText.postValue(timerDefaultText)
            passesProcessing?.cancelAndJoin()
            selection?.let { items -> settings.saveEntriesSelection(items) }
            settings.setHoursAhead(hoursAhead)
            settings.setMinElevation(minElevation)
            val stationPos = locationSource.stationPosition.value
            val geoPos = GeoPos(stationPos.latitude, stationPos.longitude)
            val selectedIds = settings.loadEntriesSelection()
            val satellites = repository.getEntriesWithIds(selectedIds)
            satelliteManager.calculatePasses(
                satellites, geoPos, timeRef, hoursAhead, minElevation
            )
        }
    }
}