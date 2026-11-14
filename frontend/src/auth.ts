import { computed, reactive } from 'vue'
import { ApiError, get, post } from './api'
import type { CurrentUser } from './types'

const state = reactive<{ user: CurrentUser | null; loaded: boolean }>({ user: null, loaded: false })
const fallbackPermissions: Record<CurrentUser['role'], string[]> = {
  ADMIN: ['USER_MANAGE','SYSTEM_ADMIN','CHANNEL_READ','ACCOUNT_READ','ACCOUNT_WRITE','TEMPLATE_READ','TEMPLATE_WRITE','TEMPLATE_EXECUTE','TRACE_READ','INBOX_ACCESS','MESSAGE_SEND','MQ_DEAD_LETTER_MANAGE','PAYMENT_READ','PAYMENT_PURCHASE','QUOTA_READ','QUOTA_MANAGE','BILLING_OUTBOX_MANAGE','REPORT_READ','REPORT_GENERATE','REPORT_MANAGE'],
  OPERATOR: ['CHANNEL_READ','ACCOUNT_READ','ACCOUNT_WRITE','TEMPLATE_READ','TEMPLATE_WRITE','TEMPLATE_EXECUTE','TRACE_READ','INBOX_ACCESS','PAYMENT_READ','QUOTA_READ','REPORT_READ'],
  AUDITOR: ['CHANNEL_READ','TEMPLATE_READ','TRACE_READ','INBOX_ACCESS','PAYMENT_READ','QUOTA_READ','REPORT_READ'],
  USER: ['INBOX_ACCESS'],
}

function normalizeUser(user: CurrentUser) {
  return { ...user, permissions: user.permissions?.length ? user.permissions : fallbackPermissions[user.role] }
}

export const currentUser = computed(() => state.user)
export const tenantLabel = computed(() => state.user?.tenantName || `租户 #${state.user?.tenantId || ''}`)
export function hasPermission(permission?: string) {
  return !permission || Boolean(state.user?.permissions?.includes(permission))
}

export async function bootstrapAuth() {
  if (state.loaded) return state.user
  try {
    state.user = normalizeUser(await get<CurrentUser>('/auth/me'))
  } catch {
    state.user = null
  } finally {
    state.loaded = true
  }
  return state.user
}

export async function login(username: string, password: string) {
  state.user = normalizeUser(await post<CurrentUser>('/auth/login', { username, password }))
  state.loaded = true
  return state.user
}

// 收到 401 后复核会话是否真的失效；仅当 /auth/me 返回 401 才判定过期，
// 避免个别接口的权限/网关问题误伤整个登录态。返回 true 表示会话仍然有效。
export async function refreshSession(): Promise<boolean> {
  try {
    state.user = normalizeUser(await get<CurrentUser>('/auth/me'))
    state.loaded = true
    return true
  } catch (error) {
    return !(error instanceof ApiError && error.status === 401)
  }
}

export async function logout() {
  try {
    await post<void>('/auth/logout')
  } finally {
    state.user = null
    state.loaded = true
  }
}

export function clearAuth() {
  state.user = null
  state.loaded = true
}
