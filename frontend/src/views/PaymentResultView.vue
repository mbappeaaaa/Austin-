<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CheckOutlined, CloseOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { get } from '../api'
interface Order { orderNo: string; packageCode: string; packageName: string; provider: string; quotaAmount: number; status: string }
const route = useRoute(); const order = ref<Order | null>(null); const error = ref(''); let timer: number | undefined
const terminal = computed(() => ['SUCCEEDED','FAILED','EXPIRED'].includes(order.value?.status || ''))
async function poll() { const orderNo = String(route.query.orderNo || ''); if (!orderNo) { error.value = '缺少订单号'; return } try { order.value = await get(`/billing/payments/${encodeURIComponent(orderNo)}`); if (terminal.value && order.value) localStorage.removeItem(`austin-checkout:${order.value.packageCode}:${order.value.provider}`); else timer = window.setTimeout(poll, 3000) } catch (e) { error.value = e instanceof Error ? e.message : '查询订单失败' } }
onMounted(poll); onBeforeUnmount(() => window.clearTimeout(timer))
</script>
<template><div class="page-container"><section class="status-card"><template v-if="error"><div class="status-icon error"><CloseOutlined /></div><h2>无法确认支付</h2><p class="muted">{{ error }}</p></template><template v-else-if="!order || !terminal"><div class="status-icon"><LoadingOutlined spin /></div><h2>正在确认支付</h2><p class="muted">已返回 Austin Notify，正在查询服务端订单 {{ route.query.orderNo }} 的最终状态。</p><a-alert type="warning" show-icon message="SANDBOX · 不依赖支付渠道跳转结果，最终以服务端订单状态为准。" /></template><template v-else-if="order.status === 'SUCCEEDED'"><div class="status-icon success"><CheckOutlined /></div><h2>支付已确认</h2><p>额度 <b>{{ order.quotaAmount.toLocaleString() }}</b> 条已入账。</p><p class="muted">订单 {{ order.orderNo }} · {{ order.packageName }}</p></template><template v-else><div class="status-icon error"><CloseOutlined /></div><h2>支付未完成</h2><p class="muted">订单状态：{{ order.status }}。未确认的订单不会增加额度。</p></template><a-space class="top-gap-lg"><router-link to="/billing/payments"><a-button type="primary">查看支付订单</a-button></router-link><router-link to="/billing/quota"><a-button>返回额度中心</a-button></router-link></a-space></section></div></template>
