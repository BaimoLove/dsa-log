import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { openDb, storageState } from './db'
import './style.css'

async function bootstrap() {
  // 先开库再挂载：避免各 store 初始化时拿到未打开的连接
  await openDb()
  if (!storageState.available) {
    console.warn('[db] 本地存储不可用：', storageState.error)
  }

  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.mount('#app')
}

void bootstrap()
