<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { CopyOutlined, EditOutlined, EyeOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { currentUser, hasPermission } from '../auth'
import { get, post } from '../api'
import type { PageData, QuotaAccount } from '../types'

interface TemplateRow { id: number; name: string; sendChannel: number; templateType: number; msgType: number; msgStatus: number; channelReady: boolean; channelAvailabilityMessage?: string }
const router = useRouter()
const rows = ref<TemplateRow[]>([])
const loading = ref(false)
const total = ref(0)
const query = reactive({ page: 1, perPage: 20, keywords: '' })
const testOpen = ref(false); const testTemplate = ref<TemplateRow | null>(null); const receiver = ref(currentUser.value?.username || ''); const quota = ref<QuotaAccount | null>(null); const testing = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await get<PageData<TemplateRow>>(`/messageTemplate/list?page=${query.page}&perPage=${query.perPage}&keywords=${encodeURIComponent(query.keywords)}&creator=${encodeURIComponent(currentUser.value?.username || '')}`)
    rows.value = data.rows || []; total.value = data.count || 0
  } finally { loading.value = false }
}
async function copy(id: number) { await post(`/messageTemplate/copy/${id}`); message.success('模板已复制'); load() }
async function openTest(row: TemplateRow) { testTemplate.value = row; quota.value = await get<QuotaAccount>('/billing/quota'); testOpen.value = true }
async function testSend() { if (!testTemplate.value || !receiver.value.trim()) return; testing.value = true; try { await post('/messageTemplate/test', { id: testTemplate.value.id, receiver: receiver.value.trim(), msgContent: '{}' }); message.success('测试消息已提交'); testOpen.value = false } finally { testing.value = false } }
async function toggle(row: TemplateRow) {
  const starting = row.msgStatus !== 30
  Modal.confirm({ title: starting ? '启动定时任务' : '停止定时任务', content: starting ? '发送额度最终以服务端预占结果为准。' : '确认停止该任务？', async onOk() { await post(`/messageTemplate/${starting ? 'start' : 'stop'}/${row.id}`); message.success(starting ? '已启动' : '已停止'); load() } })
}
onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-heading"><div><span class="eyebrow blue">Message templates</span><h1>消息模板</h1><p>统一管理各渠道消息内容与发送任务。</p></div><a-button v-if="hasPermission('TEMPLATE_WRITE')" type="primary" size="large" @click="router.push('/templates/new')"><PlusOutlined /> 新增模板</a-button></div>
    <div class="table-card"><div class="table-toolbar"><a-input-search v-model:value="query.keywords" placeholder="按模板名称搜索" allow-clear style="width:320px" @search="query.page = 1; load()" /></div>
      <a-table :data-source="rows" :loading="loading" row-key="id" :pagination="{ current: query.page, pageSize: query.perPage, total, showSizeChanger: true }" @change="(p: any) => { query.page = p.current; query.perPage = p.pageSize; load() }">
        <a-table-column title="ID" data-index="id" width="80" /><a-table-column title="模板名称" data-index="name" />
        <a-table-column title="渠道" data-index="sendChannel"><template #default="{ text }">渠道 {{ text }}</template></a-table-column>
        <a-table-column title="类型" data-index="templateType"><template #default="{ text }">{{ text === 10 ? '定时任务' : '实时接口' }}</template></a-table-column>
        <a-table-column title="渠道状态" key="ready"><template #default="{ record }"><a-tag :color="record.channelReady ? 'green' : 'red'">{{ record.channelReady ? '可发送' : '不可用' }}</a-tag></template></a-table-column>
        <a-table-column title="操作" key="actions" width="360"><template #default="{ record }"><a-space><a-button v-if="hasPermission('TEMPLATE_EXECUTE')" type="link" :disabled="!record.channelReady" @click="openTest(record)">测试</a-button><a-button type="link" @click="router.push(`/templates/${record.id}`)"><EyeOutlined />查看</a-button><a-button v-if="hasPermission('TEMPLATE_WRITE')" type="link" @click="router.push(`/templates/${record.id}/edit`)"><EditOutlined />编辑</a-button><a-button v-if="hasPermission('TEMPLATE_WRITE')" type="link" :disabled="!record.channelReady" @click="copy(record.id)"><CopyOutlined />复制</a-button><a-button v-if="record.templateType === 10 && hasPermission('TEMPLATE_EXECUTE')" type="link" :danger="record.msgStatus === 30" :disabled="!record.channelReady && record.msgStatus !== 30" @click="toggle(record)">{{ record.msgStatus === 30 ? '停止' : '启动' }}</a-button></a-space></template></a-table-column>
      </a-table>
    </div>
    <a-modal v-model:open="testOpen" title="测试一条消息" :confirm-loading="testing" :ok-button-props="{ disabled: !quota || quota.availableQuota < 1 }" @ok="testSend"><div v-if="quota" class="send-estimate"><div><span>接收人数</span><b>1</b></div><div><span>预计消耗额度</span><b>1</b></div><div><span>当前可用额度</span><b>{{ quota.availableQuota.toLocaleString() }}</b></div><div><span>发送后预计剩余</span><b>{{ Math.max(0, quota.availableQuota - 1).toLocaleString() }}</b></div></div><a-alert v-if="quota && quota.availableQuota < 1" type="error" show-icon message="当前额度不足，本次发送需要 1 条额度。" class="form-alert" /><a-form-item label="接收者" class="top-gap"><a-input v-model:value="receiver" /></a-form-item><p class="muted">前端估算仅用于体验，最终以服务端额度预占结果为准。</p></a-modal>
  </div>
</template>
