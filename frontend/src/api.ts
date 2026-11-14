const API_BASE = '/api'

interface AustinResponse<T> {
  status: string | number
  msg?: string
  message?: string
  data: T
}

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message)
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  })
  const payload = await response.json().catch(() => null) as AustinResponse<T> | null
  if (!response.ok || !payload || String(payload.status) !== '0') {
    if (response.status === 401) window.dispatchEvent(new CustomEvent('austin:unauthorized'))
    const rawMessage = payload?.msg || payload?.message
    const safeMessage = typeof rawMessage === 'string' && rawMessage.length <= 300
      && !/java\.|\bat\s+[\w.$]+\([^)]*\)/.test(rawMessage)
      ? rawMessage : '服务暂时不可用，请稍后重试或联系管理员'
    throw new ApiError(safeMessage, response.status)
  }
  return payload.data
}

export const get = <T>(path: string) => api<T>(path)
export const post = <T>(path: string, body?: unknown) => api<T>(path, {
  method: 'POST',
  body: body === undefined ? undefined : JSON.stringify(body),
})
export const put = <T>(path: string, body?: unknown) => api<T>(path, {
  method: 'PUT',
  body: body === undefined ? undefined : JSON.stringify(body),
})
export const del = <T>(path: string) => api<T>(path, { method: 'DELETE' })

export async function download(path: string, filename?: string) {
  const response = await fetch(`${API_BASE}${path}`, { credentials: 'include' })
  if (!response.ok) throw new ApiError('文件下载失败', response.status)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename || 'download'
  anchor.click()
  URL.revokeObjectURL(url)
}
