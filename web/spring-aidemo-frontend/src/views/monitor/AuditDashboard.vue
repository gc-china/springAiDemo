<template>
  <div class="audit-dashboard">
    <div class="dashboard-header">
      <h2>🔍 审计监控面板</h2>
      <div class="header-controls">
        <el-date-picker
            v-model="timeRange"
            end-placeholder="结束时间"
            range-separator="至"
            size="small"
            start-placeholder="开始时间"
            type="datetimerange"
            @change="refreshData"
        />
        <el-button :loading="loading" size="small" type="primary" @click="refreshData">
          <el-icon>
            <Refresh/>
          </el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-cards">
      <el-card class="stat-card">
        <div class="stat-content">
          <div class="stat-icon">📊</div>
          <div class="stat-info">
            <div class="stat-number">{{ statistics.totalExecutions || 0 }}</div>
            <div class="stat-label">总执行次数</div>
          </div>
        </div>
      </el-card>
      <el-card class="stat-card success">
        <div class="stat-content">
          <div class="stat-icon">✅</div>
          <div class="stat-info">
            <div class="stat-number">{{ statistics.successfulExecutions || 0 }}</div>
            <div class="stat-label">成功执行</div>
          </div>
        </div>
      </el-card>
      <el-card class="stat-card error">
        <div class="stat-content">
          <div class="stat-icon">❌</div>
          <div class="stat-info">
            <div class="stat-number">{{ statistics.failedExecutions || 0 }}</div>
            <div class="stat-label">失败执行</div>
          </div>
        </div>
      </el-card>
      <el-card class="stat-card warning">
        <div class="stat-content">
          <div class="stat-icon">⚠️</div>
          <div class="stat-info">
            <div class="stat-number">{{ statistics.ambiguousExecutions || 0 }}</div>
            <div class="stat-label">歧义执行</div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 图表区域 -->
    <div class="charts-section">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-card>
            <template #header>
              <span>📈 工具使用统计</span>
            </template>
            <div ref="toolUsageChart" style="height: 300px;"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <template #header>
              <span>🥧 执行状态分布</span>
            </template>
            <div ref="statusChart" style="height: 300px;"></div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 审计记录表格 -->
    <el-card class="audit-table-card">
      <template #header>
        <div class="card-header">
          <span>📋 审计记录</span>
          <div class="table-controls">
            <el-input
                v-model="searchQuery"
                placeholder="搜索工具名称或会话ID"
                size="small"
                style="width: 200px; margin-right: 10px;"
                @input="handleSearch"
            />
            <el-select v-model="statusFilter" placeholder="状态筛选" size="small" @change="handleFilter">
              <el-option label="全部" value=""/>
              <el-option label="成功" value="ok"/>
              <el-option label="失败" value="error"/>
              <el-option label="歧义" value="ambiguous"/>
            </el-select>
          </div>
        </div>
      </template>

      <el-table
          v-loading="tableLoading"
          :data="auditRecords"
          size="small"
          style="width: 100%"
          @row-click="showExecutionDetails"
      >
        <el-table-column label="执行ID" prop="executionId" show-overflow-tooltip width="120"/>
        <el-table-column label="工具名称" prop="toolName" width="150"/>
        <el-table-column label="方法名称" prop="methodName" width="120"/>
        <el-table-column label="状态" prop="status" width="80">
          <template #default="{ row }">
            <el-tag
                :type="getStatusTagType(row.status)"
                size="small"
            >
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行时间(ms)" prop="executionTimeMs" width="120"/>
        <el-table-column label="开始时间" prop="startTime" width="180">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column label="会话ID" prop="sessionId" show-overflow-tooltip width="120"/>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click.stop="showExecutionDetails(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="totalRecords"
            layout="total, sizes, prev, pager, next, jumper"
            size="small"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 执行详情对话框 -->
    <el-dialog
        v-model="detailDialogVisible"
        :before-close="closeDetailDialog"
        title="🔍 执行详情"
        width="80%"
    >
      <div v-if="selectedExecution" class="execution-details">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="执行ID">{{ selectedExecution.executionId }}</el-descriptions-item>
          <el-descriptions-item label="追踪ID">{{ selectedExecution.traceId || 'N/A' }}</el-descriptions-item>
          <el-descriptions-item label="会话ID">{{ selectedExecution.sessionId }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ selectedExecution.userId || 'N/A' }}</el-descriptions-item>
          <el-descriptions-item label="工具名称">{{ selectedExecution.toolName }}</el-descriptions-item>
          <el-descriptions-item label="方法名称">{{ selectedExecution.methodName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(selectedExecution.status)">
              {{ getStatusText(selectedExecution.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="执行时间">{{ selectedExecution.executionTimeMs || 0 }}ms</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeTab" style="margin-top: 20px;">
          <el-tab-pane label="原始参数" name="originalParams">
            <pre class="json-display">{{ JSON.stringify(selectedExecution.originalParams, null, 2) }}</pre>
          </el-tab-pane>
          <el-tab-pane v-if="selectedExecution.finalParams" label="最终参数" name="finalParams">
            <pre class="json-display">{{ JSON.stringify(selectedExecution.finalParams, null, 2) }}</pre>
          </el-tab-pane>
          <el-tab-pane v-if="selectedExecution.result" label="执行结果" name="result">
            <pre class="json-display">{{ JSON.stringify(selectedExecution.result, null, 2) }}</pre>
          </el-tab-pane>
          <el-tab-pane v-if="selectedExecution.parameterChain" label="参数转换链" name="parameterChain">
            <div class="parameter-chain">
              <div
                  v-for="(step, index) in selectedExecution.parameterChain.steps"
                  :key="index"
                  class="chain-step"
              >
                <div class="step-header">
                  <span class="step-number">步骤 {{ index + 1 }}</span>
                  <el-tag size="small">{{ step.transformationType }}</el-tag>
                  <span class="confidence">置信度: {{ (step.confidence * 100).toFixed(1) }}%</span>
                </div>
                <div class="step-content">
                  <div><strong>参数:</strong> {{ step.parameterName }}</div>
                  <div><strong>原值:</strong> {{ step.originalValue }}</div>
                  <div><strong>转换值:</strong> {{ step.transformedValue }}</div>
                  <div><strong>原因:</strong> {{ step.reason }}</div>
                </div>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane v-if="selectedExecution.errorMessage" label="错误信息" name="error">
            <el-alert
                :closable="false"
                :title="selectedExecution.errorMessage"
                show-icon
                type="error"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import {ref, onMounted, nextTick} from 'vue'
import {Refresh} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import axios from 'axios'

// 动态导入echarts以减少初始包大小
let echarts: any = null

// 响应式数据
const loading = ref(false)
const tableLoading = ref(false)
const timeRange = ref<[Date, Date]>([
  new Date(Date.now() - 24 * 60 * 60 * 1000), // 24小时前
  new Date()
])

const statistics = ref<any>({})
const auditRecords = ref<any[]>([])
const searchQuery = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalRecords = ref(0)

const detailDialogVisible = ref(false)
const selectedExecution = ref<any>(null)
const activeTab = ref('originalParams')

// 图表引用
const toolUsageChart = ref<HTMLElement>()
const statusChart = ref<HTMLElement>()

// 图表实例
let toolUsageChartInstance: any = null
let statusChartInstance: any = null

// API基础URL
const API_BASE = '/api/audit'

// 生命周期
onMounted(async () => {
  // 动态导入echarts
  try {
    const echartsModule = await import('echarts')
    echarts = echartsModule.default || echartsModule
  } catch (error) {
    console.warn('ECharts加载失败，图表功能将不可用:', error)
  }

  refreshData()
  nextTick(() => {
    if (echarts) {
      initCharts()
    }
  })
})

// 方法
const refreshData = async () => {
  loading.value = true
  try {
    await Promise.all([
      loadStatistics(),
      loadAuditRecords()
    ])
  } catch (error) {
    console.error('刷新数据失败:', error)
    ElMessage.error('刷新数据失败')
  } finally {
    loading.value = false
  }
}

const loadStatistics = async () => {
  try {
    const [startTime, endTime] = timeRange.value
    const response = await axios.get(`${API_BASE}/statistics?since=${startTime.toISOString()}`)
    statistics.value = response.data
    if (echarts) {
      updateCharts(response.data)
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
    // 使用模拟数据
    statistics.value = {
      totalExecutions: 0,
      successfulExecutions: 0,
      failedExecutions: 0,
      ambiguousExecutions: 0,
      toolUsage: {}
    }
  }
}

const loadAuditRecords = async () => {
  tableLoading.value = true
  try {
    const [startTime, endTime] = timeRange.value
    const params = new URLSearchParams({
      startTime: startTime.toISOString(),
      endTime: endTime.toISOString(),
      offset: ((currentPage.value - 1) * pageSize.value).toString(),
      limit: pageSize.value.toString()
    })

    if (statusFilter.value) {
      params.append('statuses', statusFilter.value)
    }

    const response = await axios.get(`${API_BASE}/trail?${params}`)
    auditRecords.value = response.data
    totalRecords.value = response.data.length // 实际应该从响应头或单独接口获取总数
  } catch (error) {
    console.error('加载审计记录失败:', error)
    auditRecords.value = []
    // 显示友好的错误信息
    ElMessage.warning('审计数据加载失败，可能是服务未启动或数据库未初始化')
  } finally {
    tableLoading.value = false
  }
}

const initCharts = () => {
  if (!echarts) return

  if (toolUsageChart.value) {
    toolUsageChartInstance = echarts.init(toolUsageChart.value)
  }
  if (statusChart.value) {
    statusChartInstance = echarts.init(statusChart.value)
  }
}

const updateCharts = (data: any) => {
  if (!echarts) return

  // 更新工具使用统计图表
  if (toolUsageChartInstance && data.toolUsage) {
    const toolNames = Object.keys(data.toolUsage)
    const toolCounts = Object.values(data.toolUsage) as number[]

    toolUsageChartInstance.setOption({
      title: {text: '工具使用统计', left: 'center'},
      tooltip: {trigger: 'axis'},
      xAxis: {
        type: 'category',
        data: toolNames,
        axisLabel: {rotate: 45}
      },
      yAxis: {type: 'value'},
      series: [{
        data: toolCounts,
        type: 'bar',
        itemStyle: {color: '#409EFF'}
      }]
    })
  }

  // 更新状态分布饼图
  if (statusChartInstance) {
    const statusData = [
      {value: data.successfulExecutions || 0, name: '成功'},
      {value: data.failedExecutions || 0, name: '失败'},
      {value: data.ambiguousExecutions || 0, name: '歧义'}
    ]

    statusChartInstance.setOption({
      title: {text: '执行状态分布', left: 'center'},
      tooltip: {trigger: 'item'},
      series: [{
        type: 'pie',
        radius: '50%',
        data: statusData,
        itemStyle: {
          color: (params: any) => {
            const colors = ['#67C23A', '#F56C6C', '#E6A23C']
            return colors[params.dataIndex]
          }
        }
      }]
    })
  }
}

const handleSearch = () => {
  // 实现搜索逻辑
  loadAuditRecords()
}

const handleFilter = () => {
  currentPage.value = 1
  loadAuditRecords()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  loadAuditRecords()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  loadAuditRecords()
}

const showExecutionDetails = (row: any) => {
  selectedExecution.value = row
  detailDialogVisible.value = true
}

const closeDetailDialog = () => {
  detailDialogVisible.value = false
  selectedExecution.value = null
}

const getStatusTagType = (status: string) => {
  switch (status) {
    case 'ok':
      return 'success'
    case 'error':
      return 'danger'
    case 'ambiguous':
      return 'warning'
    default:
      return 'info'
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'ok':
      return '成功'
    case 'error':
      return '失败'
    case 'ambiguous':
      return '歧义'
    default:
      return status
  }
}

const formatTime = (timeStr: string) => {
  if (!timeStr) return 'N/A'
  return new Date(timeStr).toLocaleString('zh-CN')
}
</script>

<style scoped>
.audit-dashboard {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.dashboard-header h2 {
  margin: 0;
  color: #303133;
  font-size: 1.5rem;
}

.header-controls {
  display: flex;
  gap: 10px;
  align-items: center;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-card.success {
  border-left: 4px solid #67C23A;
}

.stat-card.error {
  border-left: 4px solid #F56C6C;
}

.stat-card.warning {
  border-left: 4px solid #E6A23C;
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 10px;
}

.stat-icon {
  font-size: 2rem;
  margin-right: 15px;
}

.stat-info {
  flex: 1;
}

.stat-number {
  font-size: 1.8rem;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 5px;
}

.stat-label {
  color: #666;
  font-size: 0.9rem;
}

.charts-section {
  margin-bottom: 20px;
}

.audit-table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-controls {
  display: flex;
  gap: 10px;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: right;
}

.execution-details {
  max-height: 600px;
  overflow-y: auto;
}

.json-display {
  background: #f5f5f5;
  padding: 15px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
}

.parameter-chain {
  max-height: 400px;
  overflow-y: auto;
}

.chain-step {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 10px;
  padding: 15px;
  background: #fafafa;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-weight: bold;
}

.step-number {
  color: #409EFF;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.8rem;
}

.confidence {
  color: #67C23A;
  font-size: 12px;
  background: #f0f9ff;
  padding: 2px 6px;
  border-radius: 8px;
}

.step-content {
  font-size: 14px;
  line-height: 1.6;
}

.step-content > div {
  margin-bottom: 5px;
  padding: 2px 0;
}

.step-content strong {
  color: #606266;
  min-width: 60px;
  display: inline-block;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .dashboard-header {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }

  .header-controls {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .stats-cards {
    grid-template-columns: 1fr;
  }
}
</style>