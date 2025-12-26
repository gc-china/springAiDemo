<template>
  <div class="ingestion-container">
    <div class="header">
      <h2>知识库文档摄入</h2>
      <p class="subtitle">上传文档以构建 RAG 向量知识库，支持 PDF, Word, Excel, Markdown 等格式。</p>
    </div>

    <!-- 上传区域 -->
    <div class="upload-section">
      <el-upload
          :disabled="isUploading"
          :http-request="handleUpload"
          :show-file-list="false"
          action="#"
          class="upload-dragger"
          drag
      >
        <el-icon class="el-icon--upload">
          <upload-filled/>
        </el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持单个文件上传，最大 50MB
          </div>
        </template>
      </el-upload>
    </div>

    <!-- 任务列表 / 当前任务状态 -->
    <div v-if="currentTask" class="task-status-card">
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span class="file-name">📄 {{ currentTask.fileName }}</span>
            <el-tag :type="getStatusType(currentTask.status)">{{ currentTask.status }}</el-tag>
          </div>
        </template>

        <div class="progress-section">
          <div class="progress-info">
            <span>处理进度</span>
            <span>{{ currentTask.progress }}%</span>
          </div>
          <el-progress
              :percentage="currentTask.progress"
              :status="getProgressStatus(currentTask.status)"
              :stroke-width="10"
              striped
              striped-flow
          />
          <div class="status-message">
            <i v-if="currentTask.status === 'PROCESSING'" class="ri-loader-4-line"></i>
            {{ currentTask.message }}
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {ref, onUnmounted} from 'vue'
import {UploadFilled} from '@element-plus/icons-vue'
import {ElMessage, type UploadRequestOptions} from 'element-plus'
import axios from 'axios'

// --- 类型定义 ---
interface IngestionTask {
  ingestionId: string
  fileName: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  progress: number
  message: string
}

// --- 状态管理 ---
const isUploading = ref(false)
const currentTask = ref<IngestionTask | null>(null)
let pollingTimer: any = null

// --- 方法 ---

/**
 * 处理文件上传
 */
const handleUpload = async (options: UploadRequestOptions) => {
  const {file} = options
  isUploading.value = true

  const formData = new FormData()
  formData.append('file', file)

  try {
    // 1. 上传文件
    const res = await axios.post('/api/ai/knowledge/upload', formData, {
      headers: {'Content-Type': 'multipart/form-data'}
    })

    if (res.data.status === 'success') {
      ElMessage.success('上传成功，开始后台处理')

      // 2. 初始化任务状态
      currentTask.value = {
        ingestionId: res.data.ingestionId,
        fileName: file.name,
        status: 'PENDING',
        progress: 0,
        message: '等待处理...'
      }

      // 3. 开始轮询状态
      startPolling(res.data.ingestionId)
    }
  } catch (error: any) {
    console.error('上传失败', error)
    ElMessage.error(error.response?.data?.message || '上传失败')
    currentTask.value = null
  } finally {
    isUploading.value = false
  }
}

/**
 * 轮询任务状态
 */
const startPolling = (ingestionId: string) => {
  if (pollingTimer) clearInterval(pollingTimer)

  pollingTimer = setInterval(async () => {
    try {
      const res = await axios.get(`/api/ai/knowledge/status/${ingestionId}`)
      const data = res.data

      if (currentTask.value) {
        // 更新状态
        currentTask.value.status = data.status
        currentTask.value.progress = parseInt(data.progress || '0')
        currentTask.value.message = data.message

        // 终态检查
        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          stopPolling()
          if (data.status === 'COMPLETED') {
            ElMessage.success('文档处理完成！')
          } else {
            ElMessage.error('文档处理失败: ' + data.message)
          }
        }
      }
    } catch (error) {
      console.error('状态查询失败', error)
      // 不立即停止，可能是网络波动
    }
  }, 1000) // 每秒轮询一次
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 组件销毁时停止轮询
onUnmounted(() => {
  stopPolling()
})

// --- UI 辅助方法 ---
const getStatusType = (status: string) => {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PROCESSING':
      return 'primary'
    default:
      return 'info'
  }
}

const getProgressStatus = (status: string) => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'exception'
  return ''
}
</script>

<style scoped>
.ingestion-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.header {
  margin-bottom: 30px;
  text-align: center;
}

.subtitle {
  color: #909399;
  font-size: 14px;
  margin-top: 10px;
}

.upload-section {
  margin-bottom: 40px;
}

.task-status-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-name {
  font-weight: bold;
  font-size: 16px;
}

.progress-section {
  padding: 10px 0;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  color: #606266;
}

.status-message {
  margin-top: 15px;
  font-size: 13px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 5px;
}
</style>