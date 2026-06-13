<template>
  <section v-if="chatStore.streamState.timeline.length" class="run-timeline">
    <header>
      <h3>Run timeline</h3>
      <span class="metric-chip">run #{{ chatStore.streamState.runId }}</span>
    </header>
    <article v-for="(item, index) in chatStore.streamState.timeline" :key="`${item.type}-${index}`" class="run-step">
      <span></span>
      <div>
        <strong>{{ item.title }}</strong>
        <p>{{ item.summary }}</p>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { useChatStore } from '../store/chat'

const chatStore = useChatStore()
</script>
