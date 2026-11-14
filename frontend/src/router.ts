import { createRouter, createWebHashHistory } from 'vue-router'
import { bootstrapAuth, hasPermission } from './auth'
import ConsoleLayout from './components/ConsoleLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    permission?: string
    title?: string
  }
}

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: ConsoleLayout,
      redirect: '/dashboard',
      children: [
        { path: 'settings/payment-channels', name: 'payment-channels', component: () => import('./views/PaymentChannelsView.vue'), meta: { permission: 'SYSTEM_ADMIN', title: '支付渠道' } },
        { path: 'dashboard', name: 'dashboard', component: () => import('./views/DashboardView.vue'), meta: { permission: 'QUOTA_READ', title: '工作台' } },
        { path: 'templates', name: 'templates', component: () => import('./views/TemplateListView.vue'), meta: { permission: 'TEMPLATE_READ', title: '消息模板' } },
        { path: 'templates/new', name: 'template-new', component: () => import('./views/TemplateEditorView.vue'), meta: { permission: 'TEMPLATE_WRITE', title: '新建消息模板' } },
        { path: 'templates/:id', name: 'template-detail', component: () => import('./views/TemplateDetailView.vue'), meta: { permission: 'TEMPLATE_READ', title: '模板详情' } },
        { path: 'templates/:id/edit', name: 'template-edit', component: () => import('./views/TemplateEditorView.vue'), meta: { permission: 'TEMPLATE_WRITE', title: '编辑消息模板' } },
        { path: 'messages/records', name: 'send-records', component: () => import('./views/SendRecordsView.vue'), meta: { permission: 'TRACE_READ', title: '发送记录' } },
        { path: 'accounts', name: 'accounts', component: () => import('./views/AccountListView.vue'), meta: { permission: 'ACCOUNT_READ', title: '渠道账号' } },
        { path: 'accounts/new', name: 'account-new', component: () => import('./views/AccountEditorView.vue'), meta: { permission: 'ACCOUNT_WRITE', title: '新建渠道账号' } },
        { path: 'accounts/:id/edit', name: 'account-edit', component: () => import('./views/AccountEditorView.vue'), meta: { permission: 'ACCOUNT_WRITE', title: '编辑渠道账号' } },
        { path: 'billing/quota', name: 'quota', component: () => import('./views/QuotaView.vue'), meta: { permission: 'QUOTA_READ', title: '额度中心' } },
        { path: 'billing/purchase', name: 'purchase', component: () => import('./views/PurchaseView.vue'), meta: { permission: 'PAYMENT_PURCHASE', title: '购买额度' } },
        { path: 'billing/payments', name: 'payments', component: () => import('./views/PaymentOrdersView.vue'), meta: { permission: 'PAYMENT_READ', title: '支付订单' } },
        { path: 'billing/payments/result', name: 'payment-result', component: () => import('./views/PaymentResultView.vue'), meta: { permission: 'PAYMENT_READ', title: '支付确认' } },
        { path: 'billing/reports', name: 'reports', component: () => import('./views/ReportsView.vue'), meta: { permission: 'REPORT_READ', title: '月度报告' } },
        { path: 'inbox', name: 'inbox', component: () => import('./views/InboxView.vue'), meta: { permission: 'INBOX_ACCESS', title: '通知中心' } },
        { path: 'ops/mq-dead', name: 'mq-dead', component: () => import('./views/OpsDeadView.vue'), props: { kind: 'mq' }, meta: { permission: 'MQ_DEAD_LETTER_MANAGE', title: 'MQ 死信' } },
        { path: 'ops/outbox-dead', name: 'outbox-dead', component: () => import('./views/OpsDeadView.vue'), props: { kind: 'outbox' }, meta: { permission: 'BILLING_OUTBOX_MANAGE', title: 'Outbox 死信' } },
        { path: 'ops/reports-dead', name: 'reports-dead', component: () => import('./views/OpsDeadView.vue'), props: { kind: 'reports' }, meta: { permission: 'REPORT_MANAGE', title: '失败报告' } },
        { path: 'settings/members', name: 'members', component: () => import('./views/MembersView.vue'), meta: { permission: 'USER_MANAGE', title: '成员与角色' } },
        { path: 'settings/basic', name: 'tenant-settings', component: () => import('./views/TenantView.vue'), meta: { permission: 'USER_MANAGE', title: '基本信息' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async to => {
  document.title = `${to.meta.title || '控制台'} · Austin Notify`
  const user = await bootstrapAuth()
  if (to.name === 'login') return user ? { name: user.permissions.includes('QUOTA_READ') ? 'dashboard' : 'inbox' } : true
  if (!user) return { name: 'login', query: { redirect: to.fullPath } }
  if (!hasPermission(to.meta.permission)) return { name: user.permissions.includes('QUOTA_READ') ? 'dashboard' : 'inbox' }
  return true
})

export default router
