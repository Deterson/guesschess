<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Color, ComputerLevel, GameVariant, TimeControlHttpRequest } from '../types/api'
import SegmentedControl from './SegmentedControl.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    /** Etape 15 : ajoute le choix du niveau. */
    vsComputer?: boolean
    /** Etape 16 : coche la case "variante Guessmate" par defaut selon le feature flag backend (voir HomeView). */
    defaultVariant?: GameVariant
  }>(),
  { vsComputer: false, defaultVariant: 'GUESSCHESS' },
)
const emit = defineEmits<{
  confirm: [
    { color: Color | 'RANDOM'; noGuessmate: boolean; timeControl: TimeControlHttpRequest | null; computerLevel: ComputerLevel | null },
  ]
  close: []
}>()

const { t } = useI18n()
const color = ref<Color | 'RANDOM'>('RANDOM')
/**
 * Reste undefined tant que l'utilisateur n'a pas touche la case, pour que la valeur par
 * defaut suive reactivement defaultVariant (arrive de facon asynchrone depuis
 * HomeView) plutot que de figer une valeur au montage du composant.
 */
const guessmateOverride = ref<boolean | undefined>(undefined)
const guessmate = computed({
  get: () => guessmateOverride.value ?? props.defaultVariant === 'GUESSCHESS',
  set: (value: boolean) => {
    guessmateOverride.value = value
  },
})
const mode = ref<'CORRESPONDENCE' | 'REALTIME'>('CORRESPONDENCE')
const baseMinutesText = ref('5')
const incrementSeconds = ref(0)
const computerLevel = ref<ComputerLevel>('MEDIUM')

/** Combinaisons classiques, proposees exhaustivement (voir CLAUDE.md, etape 12). */
const PRESETS: { baseMinutes: number; incrementSeconds: number }[] = [
  { baseMinutes: 1, incrementSeconds: 0 },
  { baseMinutes: 1, incrementSeconds: 2 },
  { baseMinutes: 5, incrementSeconds: 0 },
  { baseMinutes: 5, incrementSeconds: 2 },
  { baseMinutes: 15, incrementSeconds: 0 },
  { baseMinutes: 15, incrementSeconds: 10 },
  { baseMinutes: 30, incrementSeconds: 0 },
  { baseMinutes: 30, incrementSeconds: 20 },
]

/** Accepte "5", "2.5"/"2,5" et les fractions type "1/2"/"1/4" (parties bullet). */
function parseMinutes(text: string): number | null {
  const trimmed = text.trim()
  if (!trimmed) return null
  const fraction = trimmed.match(/^(\d+)\s*\/\s*(\d+)$/)
  if (fraction) {
    const denominator = Number(fraction[2])
    if (denominator <= 0) return null
    return Number(fraction[1]) / denominator
  }
  const value = Number(trimmed.replace(',', '.'))
  return Number.isFinite(value) && value > 0 ? value : null
}

const baseMinutesValue = computed(() => parseMinutes(baseMinutesText.value))
const canConfirm = computed(() => mode.value !== 'REALTIME' || baseMinutesValue.value != null)

const levelOptions = computed(() => [
  { value: 'EASY' as ComputerLevel, label: t('game.computerLevel.easy') },
  { value: 'MEDIUM' as ComputerLevel, label: t('game.computerLevel.medium') },
  { value: 'HARD' as ComputerLevel, label: t('game.computerLevel.hard') },
])
const colorOptions = computed(() => [
  { value: 'WHITE' as Color | 'RANDOM', label: t('common.white') },
  { value: 'BLACK' as Color | 'RANDOM', label: t('common.black') },
  { value: 'RANDOM' as Color | 'RANDOM', label: t('home.random') },
])
const modeOptions = computed(() => [
  { value: 'CORRESPONDENCE' as const, label: t('home.correspondenceOption') },
  { value: 'REALTIME' as const, label: t('home.realTimeOption') },
])

function applyPreset(preset: { baseMinutes: number; incrementSeconds: number }) {
  baseMinutesText.value = String(preset.baseMinutes)
  incrementSeconds.value = preset.incrementSeconds
  confirm()
}

function confirm() {
  if (!canConfirm.value) return
  const timeControl = mode.value === 'REALTIME' ? { baseMinutes: baseMinutesValue.value!, incrementSeconds: incrementSeconds.value } : null
  emit('confirm', { color: color.value, noGuessmate: !guessmate.value, timeControl, computerLevel: props.vsComputer ? computerLevel.value : null })
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-10 flex items-center justify-center bg-black/60">
    <div class="w-full max-w-sm space-y-4 rounded-lg bg-stone-800 p-6 text-left shadow-xl">
      <p class="text-center font-semibold text-stone-200">
        {{ vsComputer ? t('home.createComputerModalTitle') : t('home.createModalTitle') }}
      </p>

      <fieldset v-if="vsComputer" class="w-full rounded-lg bg-stone-900 px-4 py-3">
        <legend class="px-1 text-sm font-semibold">{{ t('home.levelLegend') }}</legend>
        <SegmentedControl v-model="computerLevel" :options="levelOptions" />
      </fieldset>

      <fieldset class="w-full rounded-lg bg-stone-900 px-4 py-3">
        <legend class="px-1 text-sm font-semibold">{{ t('home.colorLegend') }}</legend>
        <SegmentedControl v-model="color" :options="colorOptions" />
      </fieldset>

      <fieldset class="w-full rounded-lg bg-stone-900 px-4 py-3">
        <legend class="px-1 text-sm font-semibold">{{ t('home.timeControlLegend') }}</legend>
        <SegmentedControl v-model="mode" :options="modeOptions" />

        <div v-if="mode === 'REALTIME'" class="mt-3 space-y-3">
          <div class="grid grid-cols-2 gap-2">
            <button
              v-for="preset in PRESETS"
              :key="`${preset.baseMinutes}+${preset.incrementSeconds}`"
              type="button"
              class="rounded py-3 text-sm font-semibold bg-stone-700 hover:bg-stone-600"
              :class="{ 'bg-emerald-600 hover:bg-emerald-500': baseMinutesValue === preset.baseMinutes && incrementSeconds === preset.incrementSeconds }"
              @click="applyPreset(preset)"
            >
              {{ preset.baseMinutes }}+{{ preset.incrementSeconds }}
            </button>
          </div>

          <div class="flex gap-3 text-sm text-stone-300">
            <label class="flex flex-1 flex-col gap-1">
              {{ t('home.baseMinutesLabel') }}
              <input
                type="text"
                inputmode="decimal"
                placeholder="5, 1/2, 2.5…"
                v-model="baseMinutesText"
                class="rounded bg-stone-800 px-2 py-1 text-stone-100"
                :class="{ 'ring-2 ring-red-500': baseMinutesValue == null }"
              />
            </label>
            <label class="flex flex-1 flex-col gap-1">
              {{ t('home.incrementLabel') }}
              <input type="number" min="0" max="60" v-model.number="incrementSeconds" class="rounded bg-stone-800 px-2 py-1 text-stone-100" />
            </label>
          </div>
        </div>
      </fieldset>

      <label class="flex items-center gap-3 rounded-lg bg-stone-900 px-4 py-3 text-sm">
        <input type="checkbox" v-model="guessmate" class="h-4 w-4 accent-emerald-600" />
        <i18n-t keypath="home.guessmateTitle" tag="span" class="text-left font-semibold">
          <template #link>
            <router-link
              to="/how-to-play#guessmate"
              class="text-violet-400 underline hover:text-violet-300"
              @click="emit('close')"
            >
              {{ t('gameStatusBar.guessmateLinkText') }}
            </router-link>
          </template>
        </i18n-t>
      </label>

      <div class="flex gap-2">
        <button
          type="button"
          class="flex-1 rounded-lg bg-emerald-600 px-4 py-2 font-semibold hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="!canConfirm"
          @click="confirm"
        >
          {{ t('home.createButton') }}
        </button>
        <button
          type="button"
          class="flex-1 rounded-lg bg-stone-700 px-4 py-2 text-stone-300 hover:bg-stone-600"
          @click="emit('close')"
        >
          {{ t('common.cancel') }}
        </button>
      </div>
    </div>
  </div>
</template>
