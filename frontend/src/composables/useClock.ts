import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import type { Color, GameStateMessage } from '../types/api'

const TICK_MS = 200

/**
 * Pendule affichee pour color (etape 12), derivee du dernier GameStateMessage reçu -
 * jamais de source de verite locale (voir CLAUDE.md : le serveur pilote le decompte,
 * jamais le client). Pendant que la pendule de color tourne, la valeur affichee
 * decompte en continu entre deux messages, extrapolee depuis serverTimeMs (suppose
 * les horloges serveur/client a peu pres synchronisees - la seule autorite reelle
 * reste le serveur, voir Game.forfeitOnTimeIfExpired cote backend). Sinon (pendule a
 * l'arret, ou absente en correspondance), reflete directement la derniere valeur
 * connue sans interpolation.
 */
export function useClock(state: Ref<GameStateMessage | null>, color: Color) {
  const remainingMs = ref<number | null>(null)

  function recompute() {
    const current = state.value
    if (!current || !current.timeControl) {
      remainingMs.value = null
      return
    }
    const remaining = color === 'WHITE' ? current.whiteMillisRemaining : current.blackMillisRemaining
    if (current.clockRunningFor !== color) {
      remainingMs.value = remaining
      return
    }
    const elapsed = Date.now() - current.serverTimeMs
    remainingMs.value = Math.max(0, remaining - elapsed)
  }

  watch(state, recompute, { immediate: true })
  const intervalId = setInterval(recompute, TICK_MS)
  onUnmounted(() => clearInterval(intervalId))

  const running = computed(() => state.value?.clockRunningFor === color)

  return { remainingMs, running }
}
