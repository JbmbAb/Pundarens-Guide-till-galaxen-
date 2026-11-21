package se.pundaren.guide.conductor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.pundaren.guide.truth.api.TruthEventStream
import se.pundaren.guide.truth.api.TruthStateReader

/**
 * Kopplar [TailConductor] till sanningens flöden. Alla anrop till dirigenten görs i [scope]s
 * dispatcher, som ska vara enkeltrådad (huvudtråden i appen).
 *
 * Tre källor väcker dirigenten: en ny händelse, ett ändrat sanningstillstånd och en jämn puls
 * (så att en väntande svans prövas igen och en utgången stryks även när inget annat händer).
 */
object ConductorLoop {
    fun start(
        scope: CoroutineScope,
        conductor: TailConductor,
        events: TruthEventStream,
        state: TruthStateReader,
        monotonicMillis: () -> Long,
        pulseMillis: Long = 250,
    ): Job = scope.launch {
        launch { events.events.collect { conductor.onEvent(it, monotonicMillis()) } }
        launch { merge(state.navigation, state.safety, state.position).collect { conductor.reevaluate(monotonicMillis()) } }
        launch {
            while (isActive) {
                delay(pulseMillis)
                conductor.reevaluate(monotonicMillis())
            }
        }
    }
}
