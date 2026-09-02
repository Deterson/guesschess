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
const props = defineProps<{
  color: ColorLower
  info: PlayerInfo | null
}>()

const { t } = useI18n()

const isAnonymous = computed(() => props.info?.type === 'ANONYMOUS')
</script>

<template>
  <div class="flex h-6 items-center gap-2 px-1 text-sm">
    <span
      class="inline-block h-3 w-3 shrink-0 rounded-sm"
      :class="color === 'white' ? 'bg-stone-100' : 'border border-stone-500 bg-stone-900'"
    />
    <span v-if="!info" class="text-stone-600">…</span>
    <span v-else-if="isAnonymous" class="italic text-stone-500">{{ t('game.anonymousPlayer') }}</span>
    <router-link
      v-else
      :to="`/profile/${info.login}`"
      class="font-semibold hover:underline"
      :class="color === 'white' ? 'text-stone-100' : 'text-stone-400'"
    >
      @{{ info.login }}
    </router-link>
  </div>
</template>
