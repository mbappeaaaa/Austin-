<script setup lang="ts">
import { ref } from 'vue'
import { post } from '../api'
interface TraceRow { businessId?: string; title?: string; sendType?: string; creator?: string; detail?: string }
const keywords = ref(''); const rows = ref<TraceRow[]>([]); const loading = ref(false)
async function search() { if (!keywords.value.trim()) return; loading.value = true; try { const result = await post<{ items?: TraceRow[]; rows?: TraceRow[] }>('/trace/user', { receiver: keywords.value.trim() }); rows.value = result.rows || result.items || [] } finally { loading.value = false } }
</script>
<template><div class="page-container"><div class="page-heading"><div><span class="eyebrow blue">Delivery trace</span><h1>发送记录</h1><p>按接收者、messageId、模板 ID 或 businessId 查询近 72 小时链路。</p></div></div><div class="table-card"><div class="table-toolbar"><a-input-search v-model:value="keywords" placeholder="输入接收者或查询 ID" enter-button="查询" style="width:480px" :loading="loading" @search="search" /></div><a-table :data-source="rows" :loading="loading" row-key="businessId"><a-table-column title="业务 ID" data-index="businessId" /><a-table-column title="模板名称" data-index="title" /><a-table-column title="发送类型" data-index="sendType" /><a-table-column title="创建者" data-index="creator" /><a-table-column title="发送细节" data-index="detail" /></a-table></div></div></template>
