<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  gameId: string
}>()
const emit = defineEmits<{
  dismiss: []
}>()

const { t } = useI18n()
const copied = ref(false)
const link = `${window.location.origin}/game/${props.gameId}`

async function copy() {
  await navigator.clipboard.writeText(link)
  copied.value = true
}
</script>

<template>
  <div class="mb-4 space-y-2 rounded-lg bg-stone-800 p-4 text-left">
    <p class="text-sm font-semibold">{{ t('inviteBanner.created') }}</p>
    <div class="flex gap-2">
      <input readonly class="flex-1 truncate rounded bg-stone-900 px-2 py-1 text-xs" :value="link" />
      <button type="button" class="rounded bg-stone-700 px-3 py-1 text-sm hover:bg-stone-600" @click="copy">
        {{ copied ? t('inviteBanner.copied') : t('inviteBanner.copy') }}
      </button>
      <button type="button" class="rounded bg-stone-700 px-3 py-1 text-sm hover:bg-stone-600" @click="emit('dismiss')">
        ✕
      </button>
    </div>
  </div>
</template>
