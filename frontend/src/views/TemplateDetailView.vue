<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get } from '../api'
import { hasPermission } from '../auth'
const route = useRoute(); const router = useRouter(); const data = ref<Record<string, any> | null>(null)
onMounted(async () => { data.value = await get(`/messageTemplate/query/${route.params.id}`) })
</script>
<template><div class="page-container narrow-page"><div class="page-heading"><div><span class="eyebrow blue">Template detail</span><h1>{{ data?.name || '模板详情' }}</h1><p>查看历史模板内容与渠道可用性。</p></div><a-button v-if="hasPermission('TEMPLATE_WRITE')" type="primary" @click="router.push(`/templates/${route.params.id}/edit`)">编辑</a-button></div><a-skeleton v-if="!data" active /><div v-else class="form-card"><a-descriptions bordered :column="2"><a-descriptions-item label="ID">{{ data.id }}</a-descriptions-item><a-descriptions-item label="模板名称">{{ data.name }}</a-descriptions-item><a-descriptions-item label="发送渠道">{{ data.sendChannel }}</a-descriptions-item><a-descriptions-item label="渠道账号">{{ data.sendAccount }}</a-descriptions-item><a-descriptions-item label="模板类型">{{ data.templateType === 10 ? '定时任务' : '实时接口' }}</a-descriptions-item><a-descriptions-item label="渠道状态"><a-tag :color="data.channelReady ? 'green' : 'red'">{{ data.channelAvailabilityMessage || (data.channelReady ? '可用' : '不可用') }}</a-tag></a-descriptions-item><a-descriptions-item label="消息内容" :span="2"><pre class="content-preview">{{ data.msgContent }}</pre></a-descriptions-item></a-descriptions></div></div></template>
