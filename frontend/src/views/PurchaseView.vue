<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { get, post } from '../api'
interface QuotaPackage { packageCode: string; displayName: string; currency: string; amountMinor: number; quotaAmount: number }
interface PaymentOrder { orderNo: string; checkoutUrl?: string; publishableKey?: string; clientSecret?: string; displayMode?: string; provider: string; packageCode: string }
const router = useRouter()
const packages = ref<QuotaPackage[]>([]); const loading = ref(false)
const providers = ref<string[]>([])
const loadError = ref('')
const payModalVisible = ref(false); const paying = ref(false); const mounting = ref(false)
const paymentElementHost = ref<HTMLElement | null>(null)
let stripeInstance: any = null; let elementsInstance: any = null; let currentOrderNo = ''
function keyFor(pkg: QuotaPackage, provider: string) { return `austin-checkout:${pkg.packageCode}:${provider}:${pkg.currency}:${pkg.amountMinor}` }
function idempotencyKey(pkg: QuotaPackage, provider: string) { const storageKey = keyFor(pkg, provider); const existing = localStorage.getItem(storageKey); if (existing) return existing; const key = `checkout-${Date.now()}-${crypto.randomUUID()}`; localStorage.setItem(storageKey, key); return key }
async function loadStripeJs(): Promise<any> {
  if ((window as any).Stripe) return (window as any).Stripe
  await new Promise<void>((resolve, reject) => { const s = document.createElement('script'); s.src = 'https://js.stripe.com/v3/'; s.onload = () => resolve(); s.onerror = () => reject(new Error('Stripe.js 加载失败，请检查网络')); document.head.appendChild(s) })
  return (window as any).Stripe
}
function checkoutMock(pkg: QuotaPackage) { Modal.confirm({ title: 'Mock 支付', content: `确认创建 ${pkg.displayName} 的 SANDBOX Checkout？`, async onOk() { loading.value = true; try { const order = await post<PaymentOrder>('/billing/payments/checkout', { packageCode: pkg.packageCode, provider: 'MOCK', idempotencyKey: idempotencyKey(pkg, 'MOCK') }); window.location.assign(order.checkoutUrl!) } catch (error) { message.error(error instanceof Error ? error.message : '创建 Checkout 失败') } finally { loading.value = false } } }) }
async function mountPaymentElement(clientSecret: string, publishableKey: string) {
  mounting.value = true
  try {
    const Stripe = await loadStripeJs()
    stripeInstance = Stripe(publishableKey)
    elementsInstance = stripeInstance.elements({ clientSecret, appearance: { theme: 'stripe', locale: 'zh' } })
    await nextTick()
    if (paymentElementHost.value) {
      paymentElementHost.value.innerHTML = ''
      elementsInstance.create('payment').mount(paymentElementHost.value)
    }
  } catch (error) { message.error(error instanceof Error ? error.message : '支付组件初始化失败'); payModalVisible.value = false } finally { mounting.value = false }
}
async function checkoutStripe(pkg: QuotaPackage) {
  loading.value = true
  try {
    const provider = 'STRIPE_INTENT'
    const order = await post<PaymentOrder>('/billing/payments/checkout', { packageCode: pkg.packageCode, provider, idempotencyKey: idempotencyKey(pkg, provider) })
    if (order.displayMode !== 'CLIENT_SECRET' || !order.clientSecret || !order.publishableKey) { message.error('支付渠道未返回内嵌支付凭证'); return }
    currentOrderNo = order.orderNo
    payModalVisible.value = true
    await mountPaymentElement(order.clientSecret, order.publishableKey)
  } catch (error) { message.error(error instanceof Error ? error.message : '创建支付失败') } finally { loading.value = false }
}
async function confirmPayment() {
  if (!stripeInstance || !elementsInstance) return
  paying.value = true
  try {
    const { error } = await stripeInstance.confirmPayment({ elements: elementsInstance, redirect: 'if_required', confirmParams: { return_url: `${window.location.origin}/#/billing/payments/result?orderNo=${encodeURIComponent(currentOrderNo)}` } })
    if (error) { message.error(error.message || '支付未完成，可更换支付方式重试'); return }
    message.success('支付已提交，正在确认订单状态')
    payModalVisible.value = false
    router.push({ path: '/billing/payments/result', query: { orderNo: currentOrderNo } })
  } catch (error) { message.error(error instanceof Error ? error.message : '支付确认失败') } finally { paying.value = false }
}
async function checkoutRedirect(pkg: QuotaPackage, provider: string) {
  loading.value = true
  try {
    const order = await post<PaymentOrder>('/billing/payments/checkout', { packageCode: pkg.packageCode, provider, idempotencyKey: idempotencyKey(pkg, provider) })
    if (!order.checkoutUrl) throw new Error('支付渠道未返回收银台地址')
    window.location.assign(order.checkoutUrl)
  } catch (error) { message.error(error instanceof Error ? error.message : '创建支付失败') }
  finally { loading.value = false }
}
function closePayModal() { payModalVisible.value = false; elementsInstance = null; stripeInstance = null }
onMounted(async () => {
  loading.value = true
  try {
    const [packageList, providerList] = await Promise.all([
      get<QuotaPackage[]>('/billing/packages'), get<string[]>('/billing/payments/providers'),
    ])
    packages.value = packageList
    providers.value = providerList
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '支付配置加载失败，请刷新重试'
  } finally { loading.value = false }
})
</script>
<template><div class="page-container"><div class="page-heading"><div><span class="eyebrow blue">Sandbox checkout</span><h1>购买额度</h1><p>选择固定额度套餐，支付完成后由服务端确认订单并入账。</p></div></div><a-alert type="warning" show-icon class="sandbox-alert"><template #message><b>SANDBOX</b> · 当前不会产生真实交易。页面返回不代表支付成功，以服务端订单状态为准。</template></a-alert><a-alert v-if="loadError" type="error" show-icon :message="loadError" /><a-alert v-else-if="!loading && !providers.some(p => ['MOCK', 'STRIPE_INTENT', 'STRIPE', 'ALIPAY'].includes(p))" type="info" show-icon message="当前没有可用的支付渠道，请联系管理员配置沙箱支付。" /><a-spin :spinning="loading"><div class="package-grid"><article v-for="pkg in packages" :key="pkg.packageCode" class="package-card"><span class="eyebrow blue">{{ pkg.packageCode }}</span><h3>{{ pkg.displayName }}</h3><div class="package-quota">{{ pkg.quotaAmount.toLocaleString() }} <small>条消息额度</small></div><div class="package-price">{{ pkg.currency }} {{ (pkg.amountMinor / 100).toFixed(2) }}</div><a-space direction="vertical" style="width:100%"><a-button v-if="providers.includes('MOCK')" type="primary" block @click="checkoutMock(pkg)">Mock 支付</a-button><a-button block :disabled="!providers.includes('STRIPE_INTENT')" @click="checkoutStripe(pkg)">{{ providers.includes('STRIPE_INTENT') ? 'Stripe Sandbox' : 'Stripe Sandbox（未启用）' }}</a-button><a-button v-if="providers.includes('STRIPE')" block @click="checkoutRedirect(pkg, 'STRIPE')">Stripe Checkout</a-button><a-button v-if="providers.includes('ALIPAY')" block :disabled="pkg.currency !== 'CNY'" @click="checkoutRedirect(pkg, 'ALIPAY')">{{ pkg.currency === 'CNY' ? '支付宝沙箱支付' : '支付宝（仅支持人民币套餐）' }}</a-button></a-space></article></div></a-spin><a-empty v-if="!packages.length && !loading && !loadError" description="当前没有可购买套餐" /><a-modal v-model:visible="payModalVisible" title="Stripe Sandbox 支付" :mask-closable="false" :confirm-loading="paying" ok-text="确认支付" cancel-text="取消" @ok="confirmPayment" @cancel="closePayModal"><a-spin :spinning="mounting" tip="加载支付组件..."><div ref="paymentElementHost" class="stripe-element-host" /><a-alert class="top-gap" type="info" show-icon message="SANDBOX 测试卡号 4242 4242 4242 4242，任意有效期与 CVC。" /></a-spin></a-modal></div></template>
<style scoped>
.stripe-element-host { min-height: 180px; padding: 8px; border: 1px solid #e5e7eb; border-radius: 8px; }
.top-gap { margin-top: 12px; }
</style>
