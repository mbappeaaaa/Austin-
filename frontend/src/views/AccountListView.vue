<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { currentUser, hasPermission } from '../auth'
import { del, get } from '../api'
interface AccountRow { id: number; name: string; sendChannel: number; creator: string; availabilityStatus: string; availabilityMessage?: string; accountConfig?: string; created?: number }
const router = useRouter(); const rows = ref<AccountRow[]>([]); const loading = ref(false)
async function load() { loading.value = true; try { rows.value = await get<AccountRow[]>(`/account/list?creator=${encodeURIComponent(currentUser.value?.username || '')}`) } finally { loading.value = false } }
function remove(row: AccountRow) { Modal.confirm({ title: `删除渠道账号“${row.name}”？`, content: '删除前请确认没有仍在使用的模板。', okType: 'danger', async onOk() { await del(`/account/delete/${row.id}`); message.success('渠道账号已删除'); load() } }) }
onMounted(load)
</script>
<template><div class="page-container"><div class="page-heading"><div><span class="eyebrow blue">Channel accounts</span><h1>渠道账号</h1><p>统一管理消息渠道凭据与可用性。</p></div><a-button v-if="hasPermission('ACCOUNT_WRITE')" type="primary" size="large" @click="router.push('/accounts/new')"><PlusOutlined /> 新增账号</a-button></div><div class="table-card"><a-table :data-source="rows" :loading="loading" row-key="id"><a-table-column title="ID" data-index="id" width="80" /><a-table-column title="账号名称" data-index="name" /><a-table-column title="渠道" data-index="sendChannel"><template #default="{ text }">渠道 {{ text }}</template></a-table-column><a-table-column title="创建者" data-index="creator" /><a-table-column title="可用性" key="status"><template #default="{ record }"><a-tag :color="record.availabilityStatus === 'READY' ? 'green' : 'red'">{{ record.availabilityStatus }}</a-tag><span class="muted small">{{ record.availabilityMessage }}</span></template></a-table-column><a-table-column v-if="hasPermission('ACCOUNT_WRITE')" title="操作" key="actions" width="180"><template #default="{ record }"><a-button type="link" @click="router.push(`/accounts/${record.id}/edit`)"><EditOutlined />编辑</a-button><a-button type="link" danger @click="remove(record)"><DeleteOutlined />删除</a-button></template></a-table-column></a-table></div></div></template>
