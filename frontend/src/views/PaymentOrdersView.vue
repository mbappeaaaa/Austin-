<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError, get } from '../api'
import type { PageData } from '../types'

interface PaymentOrder {
  orderNo: string
  packageName: string
  provider: string
  quotaAmount: number
  currency: string
  amountMinor: number
  status: string
  created: number
}

interface TablePagination {
  current?: number
  pageSize?: number
}

const rows = ref<PaymentOrder[]>([])
const total = ref(0)
const errorMessage = ref('')
const loading = ref(false)
const query = reactive({
  page: 1,
  perPage: 20,
  status: undefined as string | undefined,
})

const statusOptions = [
  'CREATED',
  'PENDING',
  'SUCCEEDED',
  'FAILED',
  'CANCELED',
  'EXPIRED',
].map(value => ({ label: value, value }))

async function load() {
  loading.value = true
  errorMessage.value = ''

  const params = new URLSearchParams({
    page: String(query.page),
    perPage: String(query.perPage),
  })

  if (query.status) {
    params.set('status', query.status)
  }

  try {
    const data = await get<PageData<PaymentOrder>>(
      `/billing/payments?${params.toString()}`,
    )
    rows.value = data.rows || []
    total.value = data.count || 0
  } catch (error) {
    rows.value = []
    total.value = 0
    errorMessage.value =
      error instanceof ApiError ? error.message : '支付订单加载失败'
  } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  query.page = 1
  void load()
}

function handleTableChange(pagination: TablePagination) {
  const nextPerPage = pagination.pageSize ?? query.perPage
  const perPageChanged = nextPerPage !== query.perPage

  query.perPage = nextPerPage
  query.page = perPageChanged ? 1 : (pagination.current ?? query.page)
  void load()
}

const colors: Record<string, string> = {
  SUCCEEDED: 'green',
  FAILED: 'red',
  CANCELED: 'default',
  EXPIRED: 'default',
  PENDING: 'gold',
  PROCESSING: 'blue',
  CREATED: 'default',
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-heading">
      <div>
        <span class="eyebrow blue">Sandbox payments</span>
        <h1>支付订单</h1>
        <p>查看本租户的沙箱支付订单与服务端确认状态。</p>
      </div>
    </div>
    <a-alert
      type="warning"
      show-icon
      message="SANDBOX · 本页金额均为沙箱交易数据。"
      class="sandbox-alert"
    />
    <div class="table-card">
      <div class="table-toolbar">
        <a-select
          v-model:value="query.status"
          allow-clear
          placeholder="全部状态"
          style="width: 180px"
          :options="statusOptions"
          @change="handleStatusChange"
        />
      </div>
      <a-alert
        v-if="errorMessage"
        type="error"
        show-icon
        :message="errorMessage"
        class="table-alert"
      />
      <a-table
        :data-source="rows"
        :loading="loading"
        row-key="orderNo"
        :pagination="{
          current: query.page,
          pageSize: query.perPage,
          total,
          showSizeChanger: true,
        }"
        @change="handleTableChange"
      >
        <a-table-column title="订单号" data-index="orderNo" />
        <a-table-column title="套餐" data-index="packageName" />
        <a-table-column title="方式" data-index="provider" />
        <a-table-column title="额度" data-index="quotaAmount" />
        <a-table-column title="金额" key="amount">
          <template #default="{ record }">
            {{ record.currency }} {{ (record.amountMinor / 100).toFixed(2) }}
          </template>
        </a-table-column>
        <a-table-column title="状态" key="status">
          <template #default="{ record }">
            <a-tag :color="colors[record.status]">{{ record.status }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="创建时间" key="created">
          <template #default="{ record }">
            {{ new Date(record.created * 1000).toLocaleString() }}
          </template>
        </a-table-column>
        <a-table-column title="操作" key="actions">
          <template #default="{ record }">
            <router-link :to="`/billing/payments/result?orderNo=${record.orderNo}`">
              查看状态
            </router-link>
          </template>
        </a-table-column>
      </a-table>
    </div>
  </div>
</template>
