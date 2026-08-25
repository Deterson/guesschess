<script setup>
defineProps({
  moveHistory: { type: Array, required: true },
})

function colorOf(entry) {
  return entry.piece.startsWith('w') ? 'Blancs' : 'Noirs'
}
</script>

<template>
  <div class="rounded-lg bg-stone-800 px-4 py-3 text-sm">
    <p class="mb-2 font-semibold text-stone-300">Historique</p>
    <p v-if="moveHistory.length === 0" class="text-stone-500">Aucun coup joué pour l'instant.</p>
    <ol class="space-y-0.5 text-stone-400">
      <li v-for="(entry, index) in moveHistory" :key="index">
        {{ index + 1 }}. {{ colorOf(entry) }} {{ entry.from }}-{{ entry.to }}
        <span v-if="entry.captured">(prise)</span>
        <span v-if="entry.promotion">(promotion en {{ entry.promotion }})</span>
      </li>
    </ol>
  </div>
</template>
