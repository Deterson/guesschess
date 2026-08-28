<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { ChatMessage } from '../types/api'

const props = defineProps<{
  messages: ChatMessage[]
  /** Peut écrire seulement si vrai joueur actif de cette partie (voir GameView.boardDisabled). */
  canSend: boolean
}>()

const emit = defineEmits<{
  send: [text: string]
}>()

const MAX_LENGTH = 500

const draft = ref('')
const listEl = ref<HTMLElement | null>(null)

const LABELS: Record<string, string> = { WHITE: 'Blancs', BLACK: 'Noirs' }

function send() {
  const text = draft.value.trim()
  if (!text || !props.canSend) return
  emit('send', text)
  draft.value = ''
}

watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  },
)
</script>

<template>
  <div class="flex flex-col gap-2 rounded-lg bg-stone-800 px-4 py-3 text-sm">
    <p class="font-semibold text-stone-300">Chat</p>

    <div ref="listEl" class="flex max-h-56 min-h-16 flex-col gap-1 overflow-y-auto text-stone-300">
      <p v-if="messages.length === 0" class="text-stone-500">Aucun message.</p>
      <p v-for="(message, index) in messages" :key="index">
        <span
          class="font-semibold"
          :class="message.color === 'WHITE' ? 'text-stone-100' : 'text-stone-400'"
        >{{ LABELS[message.color] }} :</span>
        {{ message.text }}
      </p>
    </div>

    <form v-if="canSend" class="flex gap-2" @submit.prevent="send">
      <input
        v-model="draft"
        type="text"
        :maxlength="MAX_LENGTH"
        placeholder="Écrire un message…"
        class="min-w-0 flex-1 rounded-md bg-stone-900 px-3 py-1.5 text-stone-100 placeholder:text-stone-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
      />
      <button
        type="submit"
        class="rounded-md bg-emerald-600 px-3 py-1.5 font-semibold hover:bg-emerald-500 disabled:opacity-50"
        :disabled="!draft.trim()"
      >
        Envoyer
      </button>
    </form>
  </div>
</template>
