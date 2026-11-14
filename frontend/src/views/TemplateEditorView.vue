<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { get, post } from '../api'
import { currentUser } from '../auth'

interface Option { label?: string; name?: string; value?: string | number; code?: number; disabled?: boolean; availabilityMessage?: string }
const route = useRoute(); const router = useRouter(); const saving = ref(false); const channels = ref<Option[]>([]); const accounts = ref<Option[]>([])
const editing = computed(() => Boolean(route.params.id))
const form = reactive<Record<string, unknown>>({ name: '', sendChannel: undefined, sendAccount: undefined, templateType: 20, msgType: 10, msgContent: '', cronTaskId: 0, expectPushTime: '' })
async function loadAccounts(channel: unknown) {
  if (!channel) { accounts.value = []; return }
  accounts.value = await get<Option[]>(`/account/queryByChannelType?channelType=${channel}&creator=${encodeURIComponent(currentUser.value?.username || '')}`)
}
watch(() => form.sendChannel, value => loadAccounts(value))
async function save() {
  saving.value = true
  try {
    const payload = { ...form, sendChannel: Number(form.sendChannel), sendAccount: Number(form.sendAccount || 0), templateType: Number(form.templateType), msgType: Number(form.msgType), creator: currentUser.value?.username, updator: currentUser.value?.username }
    const saved = await post<{ id: number }>('/messageTemplate/save', payload)
    message.success(editing.value ? '模板已更新' : '模板已创建')
    router.replace(`/templates/${saved.id || route.params.id}`)
  } finally { saving.value = false }
}
onMounted(async () => {
  channels.value = await get<Option[]>(`/channel/list?creator=${encodeURIComponent(currentUser.value?.username || '')}&includeDisabled=${editing.value}`)
  if (editing.value) Object.assign(form, await get<Record<string, unknown>>(`/messageTemplate/query/${route.params.id}`))
})
</script>

<template><div class="page-container narrow-page"><div class="page-heading"><div><span class="eyebrow blue">Template editor</span><h1>{{ editing ? '编辑消息模板' : '新增消息模板' }}</h1><p>渠道与账号可用性会在服务端再次校验。</p></div></div><a-form class="form-card" :model="form" layout="vertical" @finish="save"><div class="form-grid"><a-form-item label="模板名称" name="name" :rules="[{ required: true, message: '请输入模板名称' }]"><a-input v-model:value="form.name" /></a-form-item><a-form-item label="发送渠道" name="sendChannel" :rules="[{ required: true, message: '请选择渠道' }]"><a-select v-model:value="form.sendChannel" :options="channels.map(item => ({ label: item.name, value: item.code }))" /></a-form-item><a-form-item label="渠道账号" name="sendAccount"><a-select v-model:value="form.sendAccount" allow-clear :options="accounts.map(item => ({ label: item.label, value: item.value, disabled: item.disabled }))" /></a-form-item><a-form-item label="模板类型"><a-radio-group v-model:value="form.templateType"><a-radio :value="20">实时接口</a-radio><a-radio :value="10">定时任务</a-radio></a-radio-group></a-form-item><a-form-item label="消息类型"><a-select v-model:value="form.msgType" :options="[{ label: '通知类', value: 10 }, { label: '营销类', value: 20 }, { label: '验证码', value: 30 }]" /></a-form-item></div><a-form-item label="消息内容" name="msgContent" :rules="[{ required: true, message: '请输入消息内容' }]"><a-textarea v-model:value="form.msgContent" :rows="10" placeholder="可填写文本或渠道所需 JSON" /></a-form-item><a-alert type="info" show-icon message="发送额度最终以服务端预占结果为准。" class="form-alert" /><div class="form-actions"><a-button @click="router.back()">取消</a-button><a-button type="primary" html-type="submit" :loading="saving">保存模板</a-button></div></a-form></div></template>
