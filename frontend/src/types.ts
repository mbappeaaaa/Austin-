export type Role = 'ADMIN' | 'OPERATOR' | 'AUDITOR' | 'USER'

export interface CurrentUser {
  id: number
  tenantId: number
  tenantName?: string
  username: string
  displayName?: string
  role: Role
  permissions: string[]
}

export interface QuotaAccount {
  tenantId: number
  availableQuota: number
  reservedQuota: number
  warningThreshold: number
}

export interface PageData<T> {
  rows: T[]
  count: number
}

export interface InboxMessage {
  id: number
  title: string
  content: string
  url?: string
  isRead: number
  created: number
}

export interface DashboardTenantQuota {
  tenantId: number
  availableQuota: number
  reservedQuota: number
  warningThreshold: number
}

export interface DashboardCounts {
  templates: number
  channelAccounts: number
  activeChannels: number
}

export interface DashboardMetrics {
  submitted: number
  succeeded: number
  failed: number
  filtered: number
  unknown: number
  successRate: number | null
}

export interface DashboardTrendPoint {
  label: string
  submitted: number
  succeeded: number
  failed: number
}

export interface DashboardChannelHealth {
  channelCode: number
  channelName: string
  accountCount: number
  total: number
  successRate: number | null
  state: string
}

export interface DashboardOverview {
  tenant: DashboardTenantQuota
  counts: DashboardCounts
  metrics: DashboardMetrics
  trend: DashboardTrendPoint[]
  channels: DashboardChannelHealth[]
}

export interface QuotaLedgerEntry {
  id: number
  created: number
  entryType: 'CREDIT' | 'RESERVE' | 'SETTLE' | 'RELEASE' | 'MARK_UNKNOWN' | string
  availableDelta: number
  reservedDelta: number
  availableAfter: number
  businessType?: string
  businessId?: string
}
