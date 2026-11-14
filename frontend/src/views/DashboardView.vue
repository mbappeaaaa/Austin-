<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRightOutlined, CheckCircleFilled, ClockCircleOutlined,
  RiseOutlined, TeamOutlined, ThunderboltOutlined, WarningFilled,
} from '@ant-design/icons-vue'
import { get } from '../api'
import type { DashboardOverview, DashboardTrendPoint, QuotaLedgerEntry } from '../types'

const router = useRouter()
const overview = ref<DashboardOverview | null>(null)
const events = ref<QuotaLedgerEntry[]>([])
const loading = ref(true)
const errorMsg = ref('')
const range = ref('今日')
const channel = ref(0)
const trendMode = ref('消息量')

const RANGE_MAP: Record<string, string> = { '今日': 'today', '7 天': '7d', '30 天': '30d' }
const RANGE_LABEL: Record<string, string> = { '今日': '今日提交', '7 天': '近 7 天提交', '30 天': '近 30 天提交' }

async function loadOverview() {
  const params = new URLSearchParams({ range: RANGE_MAP[range.value] || 'today' })
  if (channel.value !== 0) params.set('channel', String(channel.value))
  overview.value = await get<DashboardOverview>(`/dashboard/overview?${params.toString()}`)
  errorMsg.value = ''
}

async function loadEvents() {
  try {
    const page = await get<{ count: number; rows: QuotaLedgerEntry[] }>('/billing/quota/ledger?page=1&perPage=3')
    events.value = page.rows || []
  } catch {
    events.value = []
  }
}

watch(range, () => { loadOverview().catch(() => {}) })
watch(channel, () => { loadOverview().catch(() => {}) })

onMounted(async () => {
  try {
    await Promise.all([loadOverview(), loadEvents()])
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : '运营总览数据加载失败'
  } finally {
    loading.value = false
  }
})

function formatNumber(value: number | null | undefined): string {
  return (value ?? 0).toLocaleString()
}

function formatWan(value: number | null | undefined): string {
  const n = value ?? 0
  return n >= 10000 ? `${(n / 10000).toFixed(1)} 万` : formatNumber(n)
}

function formatTime(seconds: number | undefined): string {
  if (!seconds) return '--'
  const date = new Date(seconds * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const tenant = computed(() => overview.value?.tenant ?? null)
const counts = computed(() => overview.value?.counts)
const quotaCritical = computed(() => !!tenant.value && tenant.value.availableQuota <= 0)
const healthRate = computed(() => {
  const rate = overview.value?.metrics.successRate
  return rate == null ? '—' : `${rate}%`
})
const healthScore = computed(() => {
  const rate = overview.value?.metrics.successRate
  return rate == null ? '—' : Math.round(rate)
})

const metrics = computed(() => {
  const m = overview.value?.metrics
  if (!m) return []
  const rateNote = m.successRate == null ? '暂无发送' : `成功率 ${m.successRate}%`
  const failNote = m.unknown > 0
    ? `另有 ${formatNumber(m.unknown)} 状态未知待对账`
    : (m.submitted > 0 ? `占比 ${(m.failed * 100 / m.submitted).toFixed(1)}%` : '暂无发送')
  return [
    { label: RANGE_LABEL[range.value], value: formatNumber(m.submitted), note: rateNote, tone: 'rose', icon: '↗' },
    { label: '发送成功', value: formatNumber(m.succeeded), note: rateNote, tone: 'green', icon: '✓' },
    { label: '发送失败', value: formatNumber(m.failed), note: failNote, tone: 'red', icon: '!' },
    { label: '被拦截/屏蔽', value: formatNumber(m.filtered), note: '去重与夜间屏蔽合计', tone: 'amber', icon: '⌁' },
  ]
})

const trendPoints = computed(() => overview.value?.trend ?? [])
const maxSubmitted = computed(() => Math.max(1, ...trendPoints.value.map(p => p.submitted)))
const trendTotal = computed(() => formatWan(overview.value?.metrics.submitted))
const axisLabels = computed(() => {
  const max = maxSubmitted.value
  const fmt = (n: number) => (trendMode.value === '消息量' ? formatWan(n) : `${n}%`)
  return [fmt(Math.round(max)), fmt(Math.round(max * 2 / 3)), fmt(Math.round(max / 3)), fmt(0)]
})

function barHeight(point: DashboardTrendPoint): string {
  if (trendMode.value === '成功率') {
    const total = point.succeeded + point.failed
    return total > 0 ? `${Math.max(8, Math.round(point.succeeded * 100 / total))}%` : '2%'
  }
  return `${Math.max(2, Math.round(point.submitted * 100 / maxSubmitted.value))}%`
}

const risks = computed(() => {
  const o = overview.value
  if (!o) return []
  const list: Array<{ level: string; target: string; issue: string; impact: string; route: string }> = []
  if (o.tenant.availableQuota <= o.tenant.warningThreshold) {
    list.push({
      level: o.tenant.availableQuota <= 0 ? '严重' : '警告',
      target: `租户 #${o.tenant.tenantId}`,
      issue: `可用额度 ${o.tenant.availableQuota.toLocaleString()}，新的发送任务可能被阻断`,
      impact: `告警阈值 ${o.tenant.warningThreshold.toLocaleString()}`,
      route: '/billing/quota',
    })
  }
  for (const item of o.channels) {
    if (item.successRate != null && item.successRate < 95) {
      list.push({
        level: item.successRate < 80 ? '严重' : '警告',
        target: item.channelName,
        issue: `今日成功率 ${item.successRate}%`,
        impact: `今日发送 ${item.total} 条`,
        route: '/accounts',
      })
    }
  }
  return list
})

const criticalCount = computed(() => risks.value.filter(item => item.level === '严重').length)
const warningCount = computed(() => risks.value.filter(item => item.level === '警告').length)

const tenantRows = computed(() => {
  const t = tenant.value
  if (!t) return []
  const total = t.availableQuota + t.reservedQuota
  const used = total > 0 ? Math.round(t.reservedQuota * 100 / total) : 0
  const status = t.availableQuota <= 0 ? '严重' : (t.availableQuota <= t.warningThreshold ? '警告' : '正常')
  return [{
    name: `租户 #${t.tenantId}`,
    available: formatNumber(t.availableQuota),
    reserved: formatNumber(t.reservedQuota),
    used,
    threshold: formatNumber(t.warningThreshold),
    status,
  }]
})

const channelOptions = computed(() => [
  { label: '全部渠道', value: 0 },
  ...(overview.value?.channels.map(c => ({ label: c.channelName, value: c.channelCode })) ?? []),
])
const filteredChannels = computed(() => overview.value?.channels ?? [])

const eventItems = computed(() => events.value.map(entry => {
  const delta = Math.abs(entry.availableDelta) || Math.abs(entry.reservedDelta)
  const map: Record<string, { title: string; icon: string }> = {
    CREDIT: { title: '额度充值完成', icon: 'success' },
    RESERVE: { title: '发送配额预占', icon: 'rose' },
    SETTLE: { title: '发送结算完成', icon: 'success' },
    RELEASE: { title: '额度释放退回', icon: 'warning' },
    MARK_UNKNOWN: { title: '投递结果置为未知', icon: 'warning' },
  }
  const meta = map[entry.entryType] || { title: '额度流水', icon: 'rose' }
  return {
    icon: meta.icon,
    title: meta.title,
    desc: `${entry.businessType || '额度账户'} · ${entry.availableDelta >= 0 ? '+' : '-'}${formatNumber(delta)} 额度`,
    time: formatTime(entry.created),
  }
}))

async function retry() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadEvents()])
  } catch (error) {
    errorMsg.value = error instanceof Error ? error.message : '运营总览数据加载失败'
  } finally {
    loading.value = false
  }
}

function scrollToRisks() {
  document.getElementById('risk-panel')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <div class="page-container admin-dashboard">
    <a-skeleton v-if="loading" active :paragraph="{ rows: 14 }" />
    <template v-else>
      <section v-if="errorMsg" class="admin-alert">
        <span class="alert-mark"><WarningFilled /></span>
        <div><strong>运营总览数据加载失败</strong><p>{{ errorMsg }}（请确认后端已启动并刷新重试）</p></div>
        <a-button type="primary" @click="retry">重试</a-button>
      </section>
      <header class="dashboard-heading">
        <div><span class="dashboard-kicker">AUSTIN · ADMIN CONSOLE</span><h1>消息运营总览</h1><p>优先发现平台风险，快速定位租户、任务与渠道问题。</p></div>
        <div class="dashboard-filters">
          <a-radio-group v-model:value="range" button-style="solid" size="small">
            <a-radio-button value="今日">今日</a-radio-button><a-radio-button value="7 天">7 天</a-radio-button><a-radio-button value="30 天">30 天</a-radio-button>
          </a-radio-group>
          <a-select v-model:value="channel" size="small" style="width: 150px" :options="channelOptions" />
        </div>
      </header>

      <section class="admin-hero">
        <div class="hero-content">
          <span class="health-pill"><i /> 平台运行中</span>
          <h2>全局发送健康度 <strong>{{ healthRate }}</strong></h2>
          <p>{{ risks.length > 0 ? `当前有 ${risks.length} 项风险需要关注，请及时处理。` : '主要链路运行稳定，当前没有待处理风险。' }}</p>
          <div class="hero-facts"><span><TeamOutlined /><b>#{{ tenant?.tenantId ?? '—' }}</b> 当前租户</span><span><ThunderboltOutlined /><b>{{ counts?.activeChannels ?? 0 }}</b> 活跃渠道</span><span><RiseOutlined /><b>{{ counts?.templates ?? 0 }}</b> 消息模板</span></div>
        </div>
        <div class="health-score"><span>健康分</span><strong>{{ healthScore }}</strong><small>/ 100</small></div>
      </section>

      <section v-if="quotaCritical" class="admin-alert">
        <span class="alert-mark"><WarningFilled /></span>
        <div><strong>租户 #{{ tenant?.tenantId }} 当前额度不足，新的发送任务将被阻断</strong><p>可用额度已归零，建议立即进入额度中心充值处理。</p></div>
        <a-button type="primary" danger @click="router.push('/billing/quota')">处理额度</a-button>
        <a-button @click="scrollToRisks">查看全部风险</a-button>
      </section>

      <section class="admin-metrics">
        <article v-for="item in metrics" :key="item.label" class="admin-metric" :class="item.tone">
          <div><span class="metric-icon">{{ item.icon }}</span><span>{{ item.label }}</span><ArrowRightOutlined /></div>
          <strong>{{ item.value }}</strong><small>{{ item.note }}</small>
        </article>
      </section>

      <section class="admin-primary-grid">
        <article class="admin-panel trend-card">
          <header class="panel-header"><div><span class="dashboard-kicker">MESSAGE FLOW</span><h3>消息发送趋势</h3></div><a-radio-group v-model:value="trendMode" size="small"><a-radio-button value="消息量">消息量</a-radio-button><a-radio-button value="成功率">成功率</a-radio-button></a-radio-group></header>
          <div class="trend-summary"><strong>{{ trendMode === '消息量' ? trendTotal : (overview?.metrics.successRate == null ? '—' : `${overview.metrics.successRate}%`) }}</strong><span>{{ trendMode === '消息量' ? '累计提交' : '综合成功率' }}</span><div><i class="rose-dot" />成功 <i class="pink-dot" />失败</div></div>
          <div class="trend-chart">
            <div class="trend-axis"><span>{{ trendMode === '消息量' ? axisLabels[0] : '100%' }}</span><span>{{ trendMode === '消息量' ? axisLabels[1] : '98%' }}</span><span>{{ trendMode === '消息量' ? axisLabels[2] : '96%' }}</span><span>0</span></div>
            <div class="trend-bars"><div v-for="(point, index) in trendPoints" :key="`${range}-${index}`" class="trend-slot"><div class="trend-bar" :style="{ height: barHeight(point) }"><i /><b /></div><small>{{ point.label }}</small></div></div>
          </div>
        </article>

        <article id="risk-panel" class="admin-panel risk-card">
          <header class="panel-header"><div><span class="dashboard-kicker">ACTION REQUIRED</span><h3>待处理风险</h3></div><a-button type="link" size="small" @click="router.push('/inbox')">全部告警 <ArrowRightOutlined /></a-button></header>
          <div class="admin-risk-list">
            <div v-if="risks.length === 0" class="admin-risk-item"><div><strong>暂无待处理风险</strong><p>额度充足，渠道成功率正常。</p></div></div>
            <div v-for="risk in risks" :key="risk.target" class="admin-risk-item">
              <span class="risk-level" :class="risk.level === '严重' ? 'critical' : 'warning'">{{ risk.level }}</span>
              <div><strong>{{ risk.target }}</strong><p>{{ risk.issue }}</p><small>{{ risk.impact }}</small></div>
              <a-button size="small" @click="router.push(risk.route)">处理</a-button>
            </div>
          </div>
          <footer class="risk-overview"><span><i class="danger-dot" />{{ criticalCount }} 项严重</span><span><i class="warning-dot" />{{ warningCount }} 项警告</span><span>0 项已确认</span></footer>
        </article>
      </section>

      <section class="admin-secondary-grid">
        <article class="admin-panel tenant-risk-card">
          <header class="panel-header"><div><span class="dashboard-kicker">TENANT QUOTA</span><h3>租户额度风险</h3><p>当前租户的实时额度账户</p></div><a-button type="link" size="small" @click="router.push('/billing/quota')">额度管理 <ArrowRightOutlined /></a-button></header>
          <div class="admin-table-wrap"><table><thead><tr><th>租户</th><th>可用额度</th><th>预占额度</th><th>预占进度</th><th>告警阈值</th><th>状态</th></tr></thead><tbody><tr v-for="row in tenantRows" :key="row.name"><td><strong>{{ row.name }}</strong></td><td>{{ row.available }}</td><td>{{ row.reserved }}</td><td><span class="quota-track"><i :style="{ width: `${row.used}%` }" /></span><small>{{ row.used }}%</small></td><td>{{ row.threshold }}</td><td><span class="tenant-status" :class="`is-${row.status}`">{{ row.status }}</span></td></tr></tbody></table></div>
        </article>

        <article class="admin-panel channel-health-card">
          <header class="panel-header"><div><span class="dashboard-kicker">CHANNEL HEALTH</span><h3>渠道健康</h3></div><a-button type="link" size="small" @click="router.push('/accounts')">监控详情 <ArrowRightOutlined /></a-button></header>
          <div class="channel-health-list"><button v-for="item in filteredChannels" :key="item.channelCode" @click="router.push('/accounts')"><span class="channel-mark">{{ item.channelName.slice(0, 1) }}</span><span class="channel-copy"><strong>{{ item.channelName }}</strong><small>{{ item.accountCount }} 个账号</small></span><span class="channel-rate"><i><b :style="{ width: `${item.successRate ?? 0}%` }" /></i><small>{{ item.successRate == null ? '—' : `${item.successRate}%` }}</small></span><span class="channel-latency"><ClockCircleOutlined /> 今日 {{ item.total }} 条</span><span class="channel-state" :class="item.state === '正常' ? 'normal' : 'degraded'">{{ item.state }}</span></button></div>
        </article>
      </section>

      <section class="admin-panel recent-panel"><header class="panel-header"><div><span class="dashboard-kicker">RECENT ACTIVITY</span><h3>最近额度流水</h3></div><a-button type="link" size="small" @click="router.push('/billing/quota')">查看全部流水 <ArrowRightOutlined /></a-button></header><div class="recent-events"><button v-if="eventItems.length === 0"><span class="event-icon rose">—</span><span><strong>暂无额度流水</strong><small>充值或发送产生流水后会展示在这里</small></span></button><button v-for="(item, index) in eventItems" :key="index"><span class="event-icon" :class="item.icon"><CheckCircleFilled v-if="item.icon === 'success'" /><WarningFilled v-else-if="item.icon === 'warning'" /><span v-else>↗</span></span><span><strong>{{ item.title }}</strong><small>{{ item.desc }}</small></span><time>{{ item.time }}</time></button></div></section>
    </template>
  </div>
</template>
