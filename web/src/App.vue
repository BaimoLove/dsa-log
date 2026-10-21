<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppShell from '@/components/layout/AppShell.vue'
import ToastHost from '@/components/common/ToastHost.vue'
</script>

<template>
  <AppShell>
    <RouterView v-slot="{ Component }">
      <component :is="Component" />
    </RouterView>
  </AppShell>
  <ToastHost />
</template>
