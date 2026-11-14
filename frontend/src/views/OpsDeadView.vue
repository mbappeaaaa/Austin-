<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { get, post } from '../api'
import type { PageData } from '../types'
const props = defineProps<{ kind: 'mq'|'outbox'|'reports' }>()
const rows = ref<Record<string,any>[]>([]); const loading = ref(false)
const config = computed(() => ({
  mq:{title:'MQ 死信',subtitle:'查看当前租户消费失败的消息；响应不包含原始 payload。',list:'/admin/mq/inbox/dead?page=1&perPage=20',replay:(id:any)=>`/admin/mq/inbox/${id}/replay`},
  outbox:{title:'Outbox 死信',subtitle:'管理账务域事务事件的终止失败记录。',list:'/admin/billing/outbox/dead?page=1&perPage=20',replay:(id:any)=>`/admin/billing/outbox/${id}/replay`},
  reports:{title:'失败报告',subtitle:'仅展示已进入 DEAD 状态的报告。',list:'/billing/reports?page=1&perPage=100',replay:(id:any)=>`/admin/reports/${id}/replay`},
})[props.kind])
async function load(){loading.value=true;try{const data=await get<PageData<Record<string,any>>>(config.value.list);rows.value=(data.rows||[]).filter(row=>props.kind!=='reports'||row.status==='DEAD')}finally{loading.value=false}}
function replay(row:Record<string,any>){Modal.confirm({title:'确认人工重放？',content:'请先确认根因已处理，重放操作会记录次数。',async onOk(){await post(config.value.replay(row.id));message.success('已重新入队');load()}})}
watch(()=>props.kind,load,{immediate:true})
</script>
<template><div class="page-container"><div class="page-heading"><div><span class="eyebrow blue">Operations</span><h1>{{ config.title }}</h1><p>{{ config.subtitle }}</p></div></div><div class="table-card"><a-table :data-source="rows" :loading="loading" row-key="id"><a-table-column title="ID" data-index="id" width="80" /><a-table-column v-if="kind==='mq'" title="消费组" data-index="consumerGroup" /><a-table-column v-if="kind==='mq'" title="消费标识" data-index="consumeKey" /><a-table-column v-if="kind==='outbox'" title="事件类型" data-index="eventType" /><a-table-column v-if="kind==='outbox'" title="事件标识" data-index="eventKey" /><a-table-column v-if="kind==='reports'" title="月份" data-index="periodStart" /><a-table-column title="尝试次数" data-index="attemptCount" /><a-table-column title="重放次数" data-index="replayCount" /><a-table-column title="最后错误" key="error"><template #default="{record}">{{ record.lastErrorMessage || record.lastError || '—' }}</template></a-table-column><a-table-column title="操作" key="actions"><template #default="{record}"><a-button type="link" danger @click="replay(record)">人工重放</a-button></template></a-table-column></a-table></div></div></template>
