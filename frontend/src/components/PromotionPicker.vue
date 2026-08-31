<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColorLower, PromotionPieceType } from '../types/api'

const props = defineProps<{
  color: ColorLower
  options: PromotionPieceType[]
}>()
const emit = defineEmits<{
  select: [PromotionPieceType]
}>()

const { t } = useI18n()
const LABELS = computed<Record<PromotionPieceType, string>>(() => ({
  QUEEN: t('promotion.queen'),
  ROOK: t('promotion.rook'),
  BISHOP: t('promotion.bishop'),
  KNIGHT: t('promotion.knight'),
}))
const GLYPHS: Record<ColorLower, Record<PromotionPieceType, string>> = {
  white: { QUEEN: '♕', ROOK: '♖', BISHOP: '♗', KNIGHT: '♘' },
  black: { QUEEN: '♛', ROOK: '♜', BISHOP: '♝', KNIGHT: '♞' },
}
</script>

<template>
  <div class="fixed inset-0 z-10 flex items-center justify-center bg-black/60">
    <div class="rounded-lg bg-stone-800 p-6 shadow-xl">
      <p class="mb-4 text-center text-stone-200">{{ t('promotion.title') }}</p>
      <div class="flex gap-3">
        <button
          v-for="option in options"
          :key="option"
          type="button"
          class="flex h-16 w-16 flex-col items-center justify-center rounded bg-stone-700 text-3xl hover:bg-stone-600"
          @click="emit('select', option)"
        >
          {{ GLYPHS[props.color][option] }}
          <span class="text-xs text-stone-300">{{ LABELS[option] }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
