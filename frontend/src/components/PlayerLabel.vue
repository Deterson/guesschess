<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColorLower, PlayerInfo } from '../types/api'

/**
 * Pseudo affiche au-dessus/en-dessous du plateau (etape 14) - color est la couleur
 * DU JOUEUR affiche (pas l'orientation du plateau), voir GameView.vue pour comment
 * les deux labels (haut/bas) en sont derives selon l'orientation. Le login est
 * l'identite immuable (jamais le display name, modifiable) - voir CLAUDE.md.
 */
const props = withDefaults(
  defineProps<{
    color: ColorLower
    info: PlayerInfo | null
    /** Pendule (etape 12) - absent/null pour une partie par correspondance. */
    clockMs?: number | null
    clockRunning?: boolean
    /** Halo rouge : ce joueur doit deviner et sa pendule tourne. */
    urgent?: boolean
  }>(),
  { clockMs: null, clockRunning: false, urgent: false },
)

const { t } = useI18n()

const isAnonymous = computed(() => props.info?.type === 'ANONYMOUS')

const formattedClock = computed(() => {
  if (props.clockMs == null) return null
  const totalSeconds = Math.max(0, Math.ceil(props.clockMs / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})
</script>

<template>
  <div class="flex min-h-6 items-center gap-2 px-1 text-sm">
    <span
      class="inline-block h-3 w-3 shrink-0 rounded-sm"
      :class="color === 'white' ? 'bg-stone-100' : 'border border-stone-500 bg-stone-900'"
    />
    <span v-if="!info" class="text-stone-600">…</span>
    <template v-else>
      <span
        class="inline-block h-2 w-2 shrink-0 rounded-full"
        :class="info.connected ? 'bg-emerald-500' : 'bg-stone-600'"
        :title="info.connected ? t('game.playerOnline') : t('game.playerOffline')"
      />
      <span v-if="isAnonymous" class="italic text-stone-500">{{ t('game.anonymousPlayer') }}</span>
      <router-link
        v-else
        :to="`/profile/${info.login}`"
        class="font-semibold hover:underline"
        :class="color === 'white' ? 'text-stone-100' : 'text-stone-400'"
      >
        @{{ info.login }}
      </router-link>
    </template>
    <span
      v-if="formattedClock"
      class="ml-auto rounded px-3 py-2 font-mono text-3xl font-semibold tabular-nums"
      :class="[clockRunning ? 'bg-violet-950 text-stone-100' : 'bg-stone-800 text-stone-400', urgent ? 'guess-halo-ring' : '']"
    >
      {{ formattedClock }}
    </span>
  </div>
</template>
