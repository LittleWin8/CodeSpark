<script setup>
defineProps({
  tags: {
    type: Array,
    required: true
  },
  active: {
    type: String,
    default: '全部'
  }
})

defineEmits(['select'])
</script>

<template>
  <div class="tag-filter">
    <button
      v-for="tag in tags"
      :key="tag"
      type="button"
      class="tag-btn"
      :class="{ active: tag === active }"
      @click="$emit('select', tag)"
    >
      {{ tag }}
    </button>
  </div>
</template>

<style scoped>
.tag-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.tag-btn {
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink-soft);
  border-radius: 999px;
  padding: 9px 22px;
  font-size: 0.82rem;
  letter-spacing: 0.06em;
  transition: all 0.22s ease;
}

.tag-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.tag-btn.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

@media (max-width: 640px) {
  .tag-filter {
    gap: 9px;
  }
  .tag-btn {
    padding: 7px 16px;
    font-size: 0.78rem;
  }
}
</style>
