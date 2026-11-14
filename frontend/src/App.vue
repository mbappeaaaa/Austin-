<script setup lang="ts">
import { RouterView, useRouter } from 'vue-router'
import { clearAuth, refreshSession } from './auth'

const router = useRouter()
const theme = {
  token: {
    colorPrimary: '#b94f6f',
    colorInfo: '#b94f6f',
    colorLink: '#a33f60',
    colorText: '#3e2932',
    borderRadius: 12,
    fontFamily: 'Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif',
  },
}
let checkingSession = false
window.addEventListener('austin:unauthorized', async () => {
  if (checkingSession) return
  checkingSession = true
  try {
    if (await refreshSession()) return
  } finally {
    checkingSession = false
  }
  clearAuth()
  router.replace({ name: 'login' })
})
</script>

<template><a-config-provider :theme="theme"><RouterView /></a-config-provider></template>
