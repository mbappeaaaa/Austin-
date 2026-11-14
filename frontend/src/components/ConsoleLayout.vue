<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BellOutlined, CreditCardOutlined, DashboardOutlined, DatabaseOutlined,
  FileTextOutlined, LogoutOutlined, MailOutlined, MenuFoldOutlined,
  MenuUnfoldOutlined, NotificationOutlined, SettingOutlined,
  ShoppingCartOutlined, ToolOutlined, UserOutlined,
} from '@ant-design/icons-vue'
import { currentUser, hasPermission, logout, tenantLabel } from '../auth'
import { get, post } from '../api'
import type { InboxMessage, PageData, QuotaAccount } from '../types'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const quota = ref<QuotaAccount | null>(null)
const unread = ref(0)
const drawerOpen = ref(false)
const notifications = ref<InboxMessage[]>([])
const selectedKeys = computed(() => [route.path])
const quotaRisk = computed(() => quota.value && quota.value.availableQuota <= quota.value.warningThreshold ? 1 : 0)

async function loadHeader() {
  const [quotaResult, unreadResult] = await Promise.allSettled([
    get<QuotaAccount>('/billing/quota'),
    get<number>(`/inbox/unread-count?receiver=${encodeURIComponent(currentUser.value?.username || '')}`),
  ])
  if (quotaResult.status === 'fulfilled') quota.value = quotaResult.value
  if (unreadResult.status === 'fulfilled') unread.value = unreadResult.value
}

async function openNotifications() {
  drawerOpen.value = true
  const data = await get<PageData<InboxMessage>>(`/inbox/messages?receiver=${encodeURIComponent(currentUser.value?.username || '')}&unreadOnly=false&page=1&perPage=8`)
  notifications.value = data.rows || []
}

async function markAllRead() {
  await post('/inbox/messages/read', { receiver: currentUser.value?.username, ids: [] })
  unread.value = 0
  notifications.value = notifications.value.map(item => ({ ...item, isRead: 1 }))
}

async function signOut() {
  await logout()
  router.replace({ name: 'login' })
}

onMounted(loadHeader)
</script>

<template>
  <a-layout class="console-shell">
    <a-layout-sider v-model:collapsed="collapsed" :trigger="null" collapsible class="console-sider">
      <div class="brand" :class="{ compact: collapsed }"><span class="brand-mark">A</span><span v-if="!collapsed">Austin Admin</span></div>
      <a-menu theme="dark" mode="inline" :selected-keys="selectedKeys" @click="({ key }: { key: string }) => router.push(key)">
        <a-menu-item v-if="hasPermission('QUOTA_READ')" key="/dashboard"><DashboardOutlined /><span>运营总览</span></a-menu-item>
        <a-sub-menu v-if="hasPermission('TEMPLATE_READ')" key="message-management"><template #icon><NotificationOutlined /></template><template #title>消息管理</template>
          <a-menu-item key="/templates"><FileTextOutlined />消息模板</a-menu-item>
          <a-menu-item v-if="hasPermission('TRACE_READ')" key="/messages/records">发送记录</a-menu-item>
          <a-menu-item v-if="hasPermission('ACCOUNT_READ')" key="/accounts">渠道账号</a-menu-item>
        </a-sub-menu>
        <a-sub-menu v-if="hasPermission('QUOTA_READ')" key="billing"><template #icon><DatabaseOutlined /></template><template #title>费用与额度</template>
          <a-menu-item key="/billing/quota">额度中心</a-menu-item>
          <a-menu-item v-if="hasPermission('PAYMENT_PURCHASE')" key="/billing/purchase"><ShoppingCartOutlined />购买额度</a-menu-item>
          <a-menu-item v-if="hasPermission('PAYMENT_READ')" key="/billing/payments"><CreditCardOutlined />支付订单</a-menu-item>
          <a-menu-item v-if="hasPermission('REPORT_READ')" key="/billing/reports">月度报告</a-menu-item>
        </a-sub-menu>
        <a-menu-item key="/inbox"><MailOutlined /><span>通知中心</span></a-menu-item>
        <a-sub-menu v-if="hasPermission('SYSTEM_ADMIN')" key="operations"><template #icon><ToolOutlined /></template><template #title>运维中心</template>
          <a-menu-item key="/settings/payment-channels"><CreditCardOutlined />支付渠道</a-menu-item>
          <a-menu-item v-if="hasPermission('MQ_DEAD_LETTER_MANAGE')" key="/ops/mq-dead">MQ 死信</a-menu-item>
          <a-menu-item v-if="hasPermission('BILLING_OUTBOX_MANAGE')" key="/ops/outbox-dead">Outbox 死信</a-menu-item>
          <a-menu-item v-if="hasPermission('REPORT_MANAGE')" key="/ops/reports-dead">失败报告</a-menu-item>
        </a-sub-menu>
        <a-sub-menu v-if="hasPermission('USER_MANAGE')" key="settings"><template #icon><SettingOutlined /></template><template #title>租户设置</template>
          <a-menu-item key="/settings/members"><UserOutlined />成员与角色</a-menu-item>
          <a-menu-item key="/settings/basic">基本信息</a-menu-item>
        </a-sub-menu>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="console-header">
        <div class="header-context">
          <a-button type="text" class="collapse-button" @click="collapsed = !collapsed"><MenuUnfoldOutlined v-if="collapsed" /><MenuFoldOutlined v-else /></a-button>
          <strong>Austin Admin Console</strong><span class="context-separator" />
          <span class="tenant-name">{{ tenantLabel }}</span><span class="sandbox-badge">SANDBOX</span>
        </div>
        <div class="header-actions">
          <router-link v-if="quota" to="/billing/quota" class="quota-link">额度风险 <b>{{ quotaRisk }}</b></router-link>
          <a-badge :count="unread" :overflow-count="99"><a-button type="text" shape="circle" aria-label="打开通知" @click="openNotifications"><BellOutlined /></a-button></a-badge>
          <a-dropdown>
            <button class="user-button"><span class="avatar">{{ (currentUser?.displayName || currentUser?.username || 'A').slice(0, 1) }}</span><span>{{ currentUser?.displayName || currentUser?.username }}</span></button>
            <template #overlay><a-menu><a-menu-item @click="signOut"><LogoutOutlined /> 退出登录</a-menu-item></a-menu></template>
          </a-dropdown>
        </div>
      </a-layout-header>
      <a-layout-content class="console-content"><RouterView /></a-layout-content>
    </a-layout>
  </a-layout>

  <a-drawer v-model:open="drawerOpen" title="通知中心" width="440">
    <template #extra><a-button type="link" @click="markAllRead">全部已读</a-button></template>
    <a-list :data-source="notifications" item-layout="horizontal">
      <template #renderItem="{ item }"><a-list-item><a-list-item-meta :title="item.title" :description="item.content" /><a-badge :status="item.isRead ? 'default' : 'processing'" /></a-list-item></template>
    </a-list>
    <a-empty v-if="!notifications.length" description="暂无通知" />
  </a-drawer>
</template>
