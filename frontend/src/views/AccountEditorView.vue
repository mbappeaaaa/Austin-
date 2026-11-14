<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { currentUser } from '../auth'
import { get, post } from '../api'
interface Channel { code: number; name: string }
interface AccountRow { id: number; name: string; sendChannel: number; accountConfig: string }
const route = useRoute(); const router = useRouter(); const editing = computed(() => Boolean(route.params.id)); const channels = ref<Channel[]>([]); const saving = ref(false)
const form = reactive<{ id?: number; name: string; sendChannel?: number; accountConfig: string }>({ name: '', accountConfig: '{\n  \n}' })
async function save() { saving.value = true; try { JSON.parse(form.accountConfig); await post('/account/save', { ...form, creator: currentUser.value?.username }); message.success(editing.value ? '账号已更新' : '账号已创建'); router.replace('/accounts') } catch (error) { if (error instanceof SyntaxError) message.error('账号配置必须是有效 JSON'); else throw error } finally { saving.value = false } }
onMounted(async () => { channels.value = await get<Channel[]>(`/channel/list?creator=${encodeURIComponent(currentUser.value?.username || '')}&includeDisabled=${editing.value}`); if (editing.value) { const rows = await get<AccountRow[]>(`/account/list?creator=${encodeURIComponent(currentUser.value?.username || '')}`); const row = rows.find(item => String(item.id) === String(route.params.id)); if (row) Object.assign(form, row) } })
</script>
<template><div class="page-container narrow-page"><div class="page-heading"><div><span class="eyebrow blue">Account editor</span><h1>{{ editing ? '编辑渠道账号' : '新增渠道账号' }}</h1><p>敏感配置由服务端加密或脱敏保存。</p></div></div><a-form class="form-card" :model="form" layout="vertical" @finish="save"><div class="form-grid"><a-form-item label="账号名称" name="name" :rules="[{ required: true, message: '请输入账号名称' }]"><a-input v-model:value="form.name" /></a-form-item><a-form-item label="发送渠道" name="sendChannel" :rules="[{ required: true, message: '请选择渠道' }]"><a-select v-model:value="form.sendChannel" :options="channels.map(item => ({ label: item.name, value: item.code }))" /></a-form-item></div><a-form-item label="账号配置 JSON" name="accountConfig" :rules="[{ required: true, message: '请输入账号配置' }]"><a-textarea v-model:value="form.accountConfig" :rows="14" class="code-input" /></a-form-item><a-alert type="warning" show-icon message="编辑时保留 ****** 表示不修改已脱敏的密钥。" class="form-alert" /><div class="form-actions"><a-button @click="router.back()">取消</a-button><a-button type="primary" html-type="submit" :loading="saving">保存账号</a-button></div></a-form></div></template>
