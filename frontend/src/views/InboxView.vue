<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { currentUser } from '../auth'
import { del, get, post } from '../api'
import type { InboxMessage, PageData } from '../types'
const router=useRouter();const rows=ref<InboxMessage[]>([]);const loading=ref(false);const unreadOnly=ref(false);const unread=ref(0)
function receiver(){return encodeURIComponent(currentUser.value?.username||'')}
function businessLevel(row:InboxMessage){const text=`${row.title} ${row.content}`;if(/支付成功|报告.*完成/.test(text))return{color:'green',label:'成功'};if(/额度.*阈值|额度告警/.test(text))return{color:'gold',label:'告警'};if(/额度不足|失败|终止/.test(text))return{color:'red',label:'异常'};return{color:'blue',label:'通知'}}
function rowClassName(row:InboxMessage){return row.isRead?'read-row':'unread-row'}
async function load(){loading.value=true;try{const data=await get<PageData<InboxMessage>&{unreadCount?:number}>(`/inbox/messages?receiver=${receiver()}&unreadOnly=${unreadOnly.value}&page=1&perPage=20`);rows.value=data.rows||[];unread.value=data.unreadCount||0}finally{loading.value=false}}
async function markAll(){await post('/inbox/messages/read',{receiver:currentUser.value?.username,ids:[]});message.success('全部通知已读');load()}
async function open(row:InboxMessage){if(!row.isRead)await post(`/inbox/messages/${row.id}/read?receiver=${receiver()}`);if(row.url){const target=row.url.replace(/^#/, '');if(target.startsWith('/'))router.push(target);else window.location.assign(row.url)}else load()}
async function remove(row:InboxMessage){await del(`/inbox/messages/${row.id}?receiver=${receiver()}`);message.success('通知已删除');load()}
onMounted(load)
</script>
<template><div class="page-container"><div class="page-heading"><div><span class="eyebrow blue">Notifications</span><h1>通知中心</h1><p>支付、额度、报告和任务状态更新。</p></div><a-badge :count="unread"><a-button type="primary" @click="markAll">全部已读</a-button></a-badge></div><div class="table-card"><div class="table-toolbar"><a-switch v-model:checked="unreadOnly" checked-children="只看未读" un-checked-children="全部通知" @change="load" /></div><a-table :data-source="rows" :loading="loading" row-key="id" :row-class-name="rowClassName" :pagination="{pageSize:20}"><a-table-column title="类型" key="type" width="90"><template #default="{record}"><a-tag :color="businessLevel(record).color">{{ businessLevel(record).label }}</a-tag></template></a-table-column><a-table-column title="状态" key="status" width="90"><template #default="{record}"><a-badge :status="record.isRead?'default':'processing'" :text="record.isRead?'已读':'未读'" /></template></a-table-column><a-table-column title="标题" data-index="title" /><a-table-column title="正文" data-index="content" /><a-table-column title="收到时间" key="created"><template #default="{record}">{{ new Date(record.created*1000).toLocaleString() }}</template></a-table-column><a-table-column title="操作" key="actions"><template #default="{record}"><a-button type="link" @click="open(record)">{{ record.url?'打开':'标记已读' }}</a-button><a-button type="link" danger @click="remove(record)">删除</a-button></template></a-table-column></a-table></div></div></template>
