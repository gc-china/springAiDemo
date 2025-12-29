<template>
  <div class="monitor-container">
    <div class="header">
      <h2>系统健康监控看板</h2>
      <el-tag v-if="isConnected" effect="dark" type="success">实时连接中</el-tag>
      <el-tag v-else effect="dark" type="danger">连接断开</el-tag>
    </div>

    <!-- 核心指标卡片 -->
    <el-row :gutter="20" class="metric-row">
      <!-- 1. 死信队列 (最关键) -->
      <el-col :span="6">
        <el-card :class="{ 'alarm-card': metrics.dlqSize > 0 }" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🚨 死信队列 (DLQ)</span>
              <el-tooltip content="处理失败的消息积压量，应始终为 0" placement="top">
                <el-icon>
                  <Warning/>
                </el-icon>
              </el-tooltip>
            </div>
          </template>
          <div class="metric-value">{{ metrics.dlqSize }}</div>
          <div class="metric-desc">当前积压异常消息</div>
        </el-card>
      </el-col>

      <!-- 2. Stream Lag -->
      <el-col :span="6">
        <el-card :class="{ 'warning-card': metrics.streamLag > 1000 }" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🌊 消费积压 (Lag)</span>
            </div>
          </template>
          <div class="metric-value">{{ metrics.streamLag }}</div>
          <div class="metric-desc">待处理消息数量</div>
        </el-card>
      </el-col>

      <!-- 3. Redis P99 延迟 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>⚡ Redis 写入延迟 (P99)</span>
            </div>
          </template>
          <div class="metric-value">{{ metrics.redisP99Latency.toFixed(2) }} ms</div>
          <div class="metric-desc">99% 的请求响应时间</div>
        </el-card>
      </el-col>

      <!-- 4. 归档成功率 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📚 会话归档总数</span>
            </div>
          </template>
          <div class="metric-value">{{ metrics.archiveSuccessCount }}</div>
          <div class="metric-desc">
            失败数: <span class="text-danger">{{ metrics.archiveErrorCount }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 这里可以预留位置放 ECharts 历史趋势图 -->
  </div>
</template>

<script lang="ts" setup>
import {ref, onMounted, onUnmounted} from 'vue'
import {Warning} from '@element-plus/icons-vue'
import axios from 'axios'
import {ElMessage} from 'element-plus'

// 定义数据接口
interface MonitorVO {
  dlqSize: number
  streamLag: number
  archiveSuccessCount: number
  archiveErrorCount: number
  redisP99Latency: number
}

const isConnected = ref(false)
const timer = ref<any>(null)

// 响应式数据
const metrics = ref<MonitorVO>({
  dlqSize: 0,
  streamLag: 0,
  archiveSuccessCount: 0,
  archiveErrorCount: 0,
  redisP99Latency: 0
})

// 获取数据的方法
const fetchData = async () => {
  try {
    // 假设你的后端 API 地址前缀已配置
    const res = await axios.get('/api/monitor/dashboard')
    if (res.data) {
      metrics.value = res.data
      isConnected.value = true
    }
  } catch (error) {
    console.error('监控数据获取失败', error)
    isConnected.value = false
    // 首次失败提示，后续静默
    if (!timer.value) ElMessage.error('无法连接监控服务')
  }
}

onMounted(() => {
  fetchData()
  // 每 3 秒轮询一次
  timer.value = setInterval(fetchData, 3000)
})

onUnmounted(() => {
  if (timer.value) clearInterval(timer.value)
})
</script>

<style scoped>
.monitor-container {
  padding: 20px;
}

.header {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 20px;
}

.metric-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  margin: 10px 0;
}

.metric-desc {
  font-size: 13px;
  color: #909399;
}

.text-danger {
  color: #F56C6C;
  font-weight: bold;
}

/* 告警样式：死信队列 > 0 时卡片变红 */
.alarm-card {
  background-color: #fef0f0;
  border-color: #fde2e2;
}

.alarm-card .metric-value {
  color: #F56C6C;
}

/* 警告样式：Lag 过高 */
.warning-card {
  background-color: #fdf6ec;
  border-color: #faecd8;
}
</style>