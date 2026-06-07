<template>
  <RouterView />
  <ToastNotification ref="toastRef" />
  <CommandPalette />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterView } from 'vue-router'
import { ToastNotification, CommandPalette } from '../components'
import { setToastInstance } from '../utils/toast'

const toastRef = ref()

onMounted(() => {
  if (toastRef.value) {
    setToastInstance(toastRef.value)
  }
})
</script>
