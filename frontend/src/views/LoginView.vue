<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { login } from '../auth'

const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    const user = await login(form.username.trim(), form.password)
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : (user.permissions.includes('QUOTA_READ') ? '/dashboard' : '/inbox'))
  } catch (error) {
    message.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-shell">
      <div class="login-brand"><div class="login-brand-name"><span class="brand-mark">A</span> Austin Notify</div><div><span class="sandbox-badge light">SANDBOX</span><h1>让每一条消息<br>准确抵达。</h1><p>从模板、发送到额度、支付和报告，在一个 Vue 控制台中完成消息运营闭环。</p></div></div>
      <div class="login-form-panel"><a-form :model="form" layout="vertical" @finish="submit"><h2>欢迎回来</h2><p class="muted">请使用管理员分配的账号登录</p><a-form-item name="username" label="用户名" :rules="[{ required: true, message: '请输入用户名' }]"><a-input v-model:value="form.username" size="large" autocomplete="username"><template #prefix><UserOutlined /></template></a-input></a-form-item><a-form-item name="password" label="密码" :rules="[{ required: true, message: '请输入密码' }]"><a-input-password v-model:value="form.password" size="large" autocomplete="current-password"><template #prefix><LockOutlined /></template></a-input-password></a-form-item><a-button type="primary" html-type="submit" size="large" block :loading="loading">登录</a-button><p class="security-note">登录状态保存于 HttpOnly Session</p></a-form></div>
    </section>
  </main>
</template>
