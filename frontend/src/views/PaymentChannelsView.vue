<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { get, put } from '../api'

interface Channel {
  provider: string; enabled: boolean; configured: boolean; source: string
  feeRate: number; remark: string; version: number | null
  fields: Record<string, string>; savedSecrets: string[]
}
interface Field { key: string; label: string; secret?: boolean; multiline?: boolean; hint?: string }
const names: Record<string, string> = {
  ALIPAY: '支付宝 · 电脑网站支付', STRIPE_INTENT: 'Stripe · PaymentIntent',
  STRIPE: 'Stripe · Checkout', MOCK: 'Mock · 本地模拟支付',
}
const fieldDefs: Record<string, Field[]> = {
  STRIPE_INTENT: [
    { key: 'publishableKey', label: 'Publishable Key', hint: 'pk_test_ 开头，用于浏览器支付组件' },
    { key: 'apiKey', label: 'Secret Key', secret: true, hint: 'sk_test_ 或 rk_test_，仅保存在服务端' },
    { key: 'webhookSecret', label: 'Webhook Signing Secret', secret: true, hint: 'whsec_ 开头，与 API Key 不同' },
  ],
  STRIPE: [
    { key: 'apiKey', label: 'Restricted Key', secret: true, hint: 'rk_test_ 开头的最小权限受限密钥' },
    { key: 'webhookSecret', label: 'Webhook Signing Secret', secret: true, hint: 'whsec_ 开头' },
  ],
  ALIPAY: [
    { key: 'appId', label: '沙箱 App ID', hint: '支付宝开放平台沙箱应用的16位 App ID' },
    { key: 'appPrivateKey', label: '应用私钥', secret: true, multiline: true, hint: 'RSA2 / 2048位以上，PKCS8格式；支持粘贴 PEM 或纯密钥内容' },
    { key: 'alipayPublicKey', label: '支付宝公钥', secret: true, multiline: true, hint: '从支付宝开放平台获取。此处不是你生成的应用公钥' },
    { key: 'gatewayUrl', label: '沙箱网关', hint: 'https://openapi-sandbox.dl.alipaydev.com/gateway.do' },
    { key: 'notifyUrl', label: '异步通知地址', hint: '公网可达的完整地址，以 /billing/payments/webhooks/alipay 结尾；走前端代理时加 /api' },
    { key: 'checkoutBaseUrl', label: '支付跳转基础地址', hint: '本地建议 http://localhost:3000/api，保持浏览器登录会话' },
  ],
  MOCK: [
    { key: 'signingSecret', label: '模拟支付签名密钥', secret: true, hint: '至少32字节，仅在 local / dev / test 环境可用' },
    { key: 'checkoutBaseUrl', label: '模拟收银台基础地址', hint: '本地建议 http://localhost:3000/api' },
  ],
}
const channels = ref<Channel[]>([])
const loading = ref(false); const saving = ref(false); const error = ref('')
const selected = ref<Channel | null>(null); const open = ref(false)
const edit = reactive({ enabled: false, feeRate: 0, remark: '', fields: {} as Record<string, string> })
const columns = [
  { title: '应用', dataIndex: 'app', width: 120 },
  { title: '支付渠道', dataIndex: 'provider' },
  { title: '环境', dataIndex: 'environment', width: 110 },
  { title: '配置状态', dataIndex: 'configured', width: 140 },
  { title: '开启状态', dataIndex: 'enabled', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100 },
]
const activeCount = computed(() => channels.value.filter(c => c.enabled).length)
const callbackPath = computed(() => '/billing/payments/webhooks/' + (selected.value?.provider || '').toLowerCase())
async function load() {
  loading.value = true; error.value = ''
  try { channels.value = await get<Channel[]>('/admin/payment-channels') }
  catch (e) { error.value = e instanceof Error ? e.message : '加载失败，请重试' }
  finally { loading.value = false }
}
function configure(channel: Channel) {
  selected.value = channel
  edit.enabled = channel.enabled; edit.feeRate = Number(channel.feeRate); edit.remark = channel.remark || ''
  edit.fields = { ...channel.fields }
  for (const f of fieldDefs[channel.provider] || []) if (f.secret) edit.fields[f.key] = ''
  if (channel.provider === 'ALIPAY') {
    edit.fields.gatewayUrl ||= 'https://openapi-sandbox.dl.alipaydev.com/gateway.do'
    if (channel.source === 'ENVIRONMENT') edit.fields.checkoutBaseUrl = window.location.origin + '/api'
  }
  if (channel.provider === 'MOCK' && channel.source === 'ENVIRONMENT') edit.fields.checkoutBaseUrl = window.location.origin + '/api'
  open.value = true
}
function close() {
  if (saving.value) return
  open.value = false; selected.value = null; edit.fields = {}
}
async function save() {
  if (!selected.value) return
  saving.value = true
  try {
    const updated = await put<Channel>('/admin/payment-channels/' + selected.value.provider, {
      enabled: edit.enabled, feeRate: edit.feeRate, remark: edit.remark,
      version: selected.value.version, fields: edit.fields,
    })
    channels.value = channels.value.map(c => c.provider === updated.provider ? updated : c)
    open.value = false; selected.value = null; edit.fields = {}
    message.success('配置已保存并生效；请通过沙箱交易验证密钥与回调')
  } catch (e) { message.error(e instanceof Error ? e.message : '保存失败') }
  finally { saving.value = false }
}
async function toggle(channel: Channel, enabled: boolean) {
  if (enabled && !channel.configured) { configure(channel); edit.enabled = true; return }
  saving.value = true
  try {
    const updated = await put<Channel>('/admin/payment-channels/' + channel.provider, {
      enabled, feeRate: channel.feeRate, remark: channel.remark, version: channel.version, fields: {},
    })
    channels.value = channels.value.map(c => c.provider === updated.provider ? updated : c)
    message.success(enabled ? '渠道已开启' : '已停止此渠道的新支付，已有订单回调仍会处理')
  } catch (e) { message.error(e instanceof Error ? e.message : '更新失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-heading">
      <div><span class="eyebrow blue">Payment settings</span><h1>支付渠道</h1><p>管理 Austin 平台收款配置，统一控制额度购买可使用的支付方式。</p></div>
      <a-button :loading="loading" @click="load">刷新</a-button>
    </div>
    <a-alert class="notice" type="info" show-icon message="当前为沙箱环境 · 配置仅平台管理员可见"
      description="保存后即时生效。密钥加密保存，编辑时留空表示保留原值。配置完成不代表已经通过支付平台验证。" />
    <div class="channel-summary"><strong>Austin</strong><span>平台收款应用</span><a-tag color="blue">SANDBOX</a-tag><span>{{ activeCount }} 个渠道已开启</span></div>
    <a-alert v-if="error" class="notice" type="error" show-icon :message="error" />
    <a-table :columns="columns" :data-source="channels" row-key="provider" :loading="loading" :pagination="false" :scroll="{ x: 780 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'app'">Austin</template>
        <template v-else-if="column.dataIndex === 'provider'"><strong>{{ names[record.provider] }}</strong><div class="muted">{{ record.provider }}</div></template>
        <template v-else-if="column.dataIndex === 'environment'"><a-tag color="blue">沙箱</a-tag></template>
        <template v-else-if="column.dataIndex === 'configured'"><a-tag :color="record.configured ? 'green' : 'default'">{{ record.configured ? '配置完整' : '待配置' }}</a-tag></template>
        <template v-else-if="column.dataIndex === 'enabled'"><a-switch :checked="record.enabled" :disabled="saving" @change="(value: boolean) => toggle(record, value)" /></template>
        <template v-else-if="column.dataIndex === 'action'"><a-button type="link" :disabled="saving" @click="configure(record)">编辑</a-button></template>
      </template>
    </a-table>
    <div class="unsupported"><span>微信 · Native 支付</span><a-tag>未接入</a-tag><span>当前后端尚无微信支付实现。</span></div>
    <a-modal :open="open" :title="'编辑 ' + (selected ? names[selected.provider] : '')" width="760px"
      :mask-closable="false" :confirm-loading="saving" :closable="!saving" :cancel-button-props="{ disabled: saving }"
      ok-text="保存配置" cancel-text="取消" @ok="save" @cancel="close" :destroy-on-close="true">
      <a-form layout="vertical" autocomplete="off" class="channel-form">
        <a-form-item label="渠道编号"><a-input :value="selected?.provider" disabled /></a-form-item>
        <a-form-item v-for="f in (selected ? fieldDefs[selected.provider] : [])" :key="f.key" :label="f.label" :help="f.hint">
          <a-input-password v-if="f.secret" v-model:value="edit.fields[f.key]" autocomplete="new-password"
            :placeholder="selected?.savedSecrets.includes(f.key) ? '已保存，留空保留原值' : '请输入密钥'" :maxlength="16384" />
          <a-input v-else v-model:value="edit.fields[f.key]" :maxlength="2048" />
        </a-form-item>
        <a-form-item label="手续费率" help="仅作渠道配置记录；不会更改套餐价格或替代支付平台实际手续费。">
          <a-input-number v-model:value="edit.feeRate" :min="0" :max="100" :precision="4" :step="0.01" /> %
        </a-form-item>
        <a-form-item label="渠道状态"><a-radio-group v-model:value="edit.enabled"><a-radio :value="true">启用</a-radio><a-radio :value="false">禁用</a-radio></a-radio-group></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="edit.remark" :maxlength="255" show-count :rows="2" /></a-form-item>
        <a-alert v-if="selected?.provider.startsWith('STRIPE')" type="info" show-icon>
          <template #message>在 Stripe Dashboard 配置 Webhook URL</template>
          <template #description>使用公网 HTTPS 地址或 Stripe CLI 转发到 <code>{{ callbackPath }}</code>（经过前端代理时加 /api）。本页只保存签名密钥，测试与正式密钥不可混用。
            <a href="https://docs.stripe.com/keys" target="_blank" rel="noopener noreferrer">查看官方文档</a>
          </template>
        </a-alert>
        <a-alert v-else-if="selected?.provider === 'ALIPAY'" type="info" show-icon>
          <template #message>支付宝电脑网站支付 · 公钥模式 / RSA2 / UTF-8</template>
          <template #description>将你生成的应用公钥上传至支付宝开放平台，应用私钥保存在本页；“支付宝公钥”用于校验异步通知。仅支持 CNY 套餐，支付结果以验签后的异步通知为准。
            <a href="https://opendocs.alipay.com/open/270/105898" target="_blank" rel="noopener noreferrer">查看官方文档</a>
          </template>
        </a-alert>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.notice { margin-bottom: 20px; }
.channel-summary { display: flex; align-items: center; gap: 16px; margin: 24px 0 16px; }
.channel-summary strong { font-size: 22px; }
.muted, .unsupported { color: #8792a6; font-size: 12px; }
.unsupported { display: flex; align-items: center; gap: 12px; padding: 20px 16px; background: #f8fafc; border: 1px solid #edf0f5; }
.channel-form { padding: 12px 0; }
.channel-form :deep(.ant-form-item) { margin-bottom: 26px; }
code { word-break: break-all; }
</style>
