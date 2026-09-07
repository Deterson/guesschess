<script setup lang="ts" generic="T extends string">
defineProps<{
  modelValue: T
  options: { value: T; label: string }[]
}>()
const emit = defineEmits<{ 'update:modelValue': [T] }>()
</script>

<template>
  <div role="radiogroup" class="inline-flex w-full rounded-lg bg-stone-950/40 p-1">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :aria-checked="modelValue === option.value"
      class="flex-1 cursor-pointer rounded-md px-3 py-1.5 text-sm font-semibold transition-colors"
      :class="
        modelValue === option.value
          ? 'bg-emerald-600 text-white'
          : 'text-stone-300 hover:bg-stone-700/60'
      "
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>
