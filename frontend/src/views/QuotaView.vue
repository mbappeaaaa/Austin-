<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { get, put } from '../api'
import { hasPermission } from '../auth'
import type { PageData, QuotaAccount } from '../types'
interface LedgerRow { id: number; created: number; entryType: string; availableDelta: number; reservedDelta: number; availableAfter: number; businessType: string; businessId: string }
const quota = ref<QuotaAccount | null>(null); const rows = ref<LedgerRow[]>([]); const total = ref(0); const loading = ref(false); const ledgerUnavailable = ref(false); const query = reactive({ page: 1, perPage: 20, type: '' }); const threshold = ref(0)
const percent = computed(() => { if (!quota.value) return 0; const totalQuota = quota.value.availableQuota + quota.value.reservedQuota; return totalQuota ? Math.round(quota.value.availableQuota / totalQuota * 100) : 0 })
async function loadQuota() { const account = await get<QuotaAccount>('/billing/quota'); quota.value = account; threshold.value = account.warningThreshold }
async function loadLedger() { loading.value = true; ledgerUnavailable.value = false; try { const data = await get<PageData<LedgerRow>>(`/billing/quota/ledger?page=${query.page}&perPage=${query.perPage}&type=${query.type}`); rows.value = data.rows || []; total.value = data.count || 0 } catch { ledgerUnavailable.value = true; rows.value = [] } finally { loading.value = false } }
async function saveThreshold() { quota.value = await put('/billing/quota/warning-threshold', { warningThreshold: threshold.value }); message.success('告警阈值已更新') }
onMounted(() => { loadQuota(); loadLedger() })
</script>
<template>
    <div class="page-container">
        <div class="page-heading">
            <div><span class="eyebrow blue">Billing & quota</span>
                <h1>额度中心</h1>
                <p>额度是可发送的消息条数，与货币金额分开记录。</p>
            </div>
        </div><a-skeleton v-if="!quota" active /><template v-else><a-alert
                v-if="quota.availableQuota <= quota.warningThreshold"
                :type="quota.availableQuota <= 0 ? 'error' : 'warning'" show-icon class="quota-alert"
                :message="quota.availableQuota <= 0 ? '当前无可用额度，发送将被服务端阻断。' : `当前额度 ${quota.availableQuota}，已达到告警阈值 ${quota.warningThreshold}。`" />
            <div class="quota-center-grid">
                <section class="quota-hero quota-compact">
                    <div>
                        <div class="eyebrow">可用额度</div>
                        <div class="quota-number">{{ quota.availableQuota.toLocaleString() }}</div>
                        <div class="hero-meta">预占 {{ quota.reservedQuota.toLocaleString() }} · 告警阈值 {{
                            quota.warningThreshold.toLocaleString() }}</div>
                    </div><a-progress type="circle" :percent="percent" :stroke-color="'#fff'"
                        :trail-color="'rgba(255,255,255,.2)'" />
                </section>
                <section class="surface-card threshold-card"><span class="eyebrow blue">Warning threshold</span>
                    <h3>额度告警阈值</h3>
                    <p class="muted">余额低于阈值时发送橙色站内信告警。</p><a-input-number v-model:value="threshold" :min="0"
                        :precision="0" :disabled="!hasPermission('QUOTA_MANAGE')" addon-after="条"
                        style="width:100%" /><a-button v-if="hasPermission('QUOTA_MANAGE')" type="primary" block
                        class="top-gap" @click="saveThreshold">保存阈值</a-button>
                </section>
            </div>
        </template>
        <section class="table-card top-gap-lg">
            <div class="table-toolbar">
                <h3>额度流水</h3><a-select v-model:value="query.type" allow-clear placeholder="全部类型" style="width:180px"
                    :options="[{ label: '购买入账', value: 'PURCHASE' }, { label: '预占', value: 'RESERVE' }, { label: '结算', value: 'SETTLE' }, { label: '释放', value: 'RELEASE' }]"
                    @change="query.page = 1; loadLedger()" />
            </div><a-alert v-if="ledgerUnavailable" type="info" show-icon message="额度流水接口尚未提供：GET /billing/quota/ledger"
                class="table-alert" /><a-table :data-source="rows" :loading="loading" row-key="id"
                :pagination="{ current: query.page, pageSize: query.perPage, total }"><a-table-column title="时间"
                    key="created"><template #default="{ record }">{{ new Date(record.created * 1000).toLocaleString()
                    }}</template></a-table-column><a-table-column title="类型"
                    data-index="entryType" /><a-table-column title="可用变动" key="delta"><template #default="{ record }"><b
                            :class="record.availableDelta >= 0 ? 'positive' : 'negative'">{{ record.availableDelta > 0 ?
                                '+' : '' }}{{ record.availableDelta }}</b></template></a-table-column><a-table-column
                    title="预占变动" data-index="reservedDelta" /><a-table-column title="变更后余额"
                    data-index="availableAfter" /><a-table-column title="业务类型"
                    data-index="businessType" /><a-table-column title="业务标识" data-index="businessId" /></a-table>
        </section>
    </div>
</template>
