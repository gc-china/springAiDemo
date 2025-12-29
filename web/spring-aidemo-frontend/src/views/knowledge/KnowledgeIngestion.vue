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

    <!-- 文档管理区域 -->
    <div class="document-management">
      <div class="section-header">
        <h3>📚 已摄入文档</h3>
        <div class="header-actions">
          <el-input
              v-model="searchKeyword"
              clearable
              placeholder="搜索文档..."
              style="width: 200px"
          >
            <template #prefix>
              <i class="ri-search-line"></i>
            </template>
          </el-input>
          <el-button :loading="isLoadingDocuments" @click="refreshDocuments">
            <i class="ri-refresh-line"></i>
            刷新
          </el-button>
        </div>
      </div>

      <div v-if="isLoadingDocuments" class="loading-state">
        <el-skeleton :rows="3" animated/>
      </div>

      <div v-else-if="filteredDocuments.length === 0" class="empty-state">
        <div class="empty-icon">📄</div>
        <p>暂无文档</p>
        <span class="empty-tip">上传文档后将在此处显示</span>
      </div>

      <div v-else class="documents-grid">
        <div
            v-for="doc in filteredDocuments"
            :key="doc.documentId"
            class="document-card"
        >
          <div class="document-header">
            <div class="document-icon">
              <i :class="getFileIcon(doc.mimeType)"></i>
            </div>
            <div class="document-info">
              <h4 :title="doc.title" class="document-title">{{ doc.title }}</h4>
              <div class="document-meta">
                <span class="meta-item">
                  <i class="ri-file-text-line"></i>
                  {{ doc.chunkCount }} 个切片
                </span>
                <span class="meta-item">
                  <i class="ri-time-line"></i>
                  {{ formatDate(doc.createdAt) }}
                </span>
              </div>
            </div>
          </div>

          <div class="document-actions">
            <el-button
                :disabled="false"
                size="small"
                type="primary"
                @click="previewDocument(doc)"
            >
              <i class="ri-eye-line"></i>
              预览
            </el-button>
            <el-button
                :disabled="false"
                size="small"
                @click="downloadDocument(doc)"
            >
              <i class="ri-download-line"></i>
              下载
            </el-button>
            <el-button
                size="small"
                @click="showDocumentDetails(doc)"
            >
              <i class="ri-information-line"></i>
              详情
            </el-button>
            <el-button
                size="small"
                type="danger"
                @click="confirmDeleteDocument(doc)"
            >
              <i class="ri-delete-bin-line"></i>
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 文档详情对话框 -->
    <el-dialog
        v-model="documentDetailsVisible"
        title="📄 文档详情"
        width="600px"
    >
      <div v-if="selectedDocument" class="document-details">
        <div class="detail-row">
          <label>文档ID：</label>
          <span class="detail-value">{{ selectedDocument.documentId }}</span>
        </div>
        <div class="detail-row">
          <label>文件名：</label>
          <span class="detail-value">{{ selectedDocument.title }}</span>
        </div>
        <div class="detail-row">
          <label>文件类型：</label>
          <span class="detail-value">{{ selectedDocument.mimeType || '未知' }}</span>
        </div>
        <div class="detail-row">
          <label>文件大小：</label>
          <span class="detail-value">{{ formatFileSize(selectedDocument.totalTokens) }}</span>
        </div>
        <div class="detail-row">
          <label>切片数量：</label>
          <span class="detail-value">{{ selectedDocument.chunkCount }}</span>
        </div>
        <div class="detail-row">
          <label>创建时间：</label>
          <span class="detail-value">{{ formatDateTime(selectedDocument.createdAt) }}</span>
        </div>
        <div class="detail-row">
          <label>文件状态：</label>
          <el-tag :type="getFileStatusType(selectedDocument.fileStatus)">
            {{ selectedDocument.fileStatus }}
          </el-tag>
        </div>
        <div v-if="selectedDocument.metadata" class="detail-row">
          <label>元数据：</label>
          <pre class="metadata-display">{{ JSON.stringify(selectedDocument.metadata, null, 2) }}</pre>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="documentDetailsVisible = false">关闭</el-button>
          <el-button
              :disabled="false"
              type="primary"
              @click="previewDocument(selectedDocument)"
          >
            预览文件
          </el-button>
          <el-button
              :disabled="false"
              @click="downloadDocument(selectedDocument)"
          >
            下载文件
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import {ref, onUnmounted, onMounted, computed} from 'vue'
import {UploadFilled} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox, type UploadRequestOptions} from 'element-plus'
import axios from 'axios'

// --- 类型定义 ---
interface IngestionTask {
  ingestionId: string
  fileName: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  progress: number
  message: string
}

interface DocumentInfo {
  documentId: string
  title: string
  filePath: string
  mimeType: string
  totalTokens: number
  chunkCount: number
  createdAt: string
  metadata: any
  downloadUrl: string
  previewUrl: string
  fileExists: boolean
  fileStatus: string // 新增：文件状态描述
}

// --- 状态管理 ---
const isUploading = ref(false)
const currentTask = ref<IngestionTask | null>(null)
let pollingTimer: any = null

// 文档管理相关状态
const documents = ref<DocumentInfo[]>([])
const isLoadingDocuments = ref(false)
const searchKeyword = ref('')
const documentDetailsVisible = ref(false)
const selectedDocument = ref<DocumentInfo | null>(null)

// 计算属性：过滤后的文档列表
const filteredDocuments = computed(() => {
  if (!searchKeyword.value) return documents.value

  const keyword = searchKeyword.value.toLowerCase()
  return documents.value.filter(doc =>
      doc.title.toLowerCase().includes(keyword) ||
      doc.mimeType.toLowerCase().includes(keyword)
  )
})

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

// 组件挂载时加载文档列表
onMounted(() => {
  loadDocuments()
})

// --- 文档管理方法 ---

// 加载文档列表
const loadDocuments = async () => {
  isLoadingDocuments.value = true
  try {
    // 这里需要后端提供文档列表接口
    const response = await axios.get('/api/ai/knowledge/documents')
    documents.value = response.data
  } catch (error) {
    console.error('加载文档列表失败:', error)
    ElMessage.error('加载文档列表失败')
  } finally {
    isLoadingDocuments.value = false
  }
}

// 刷新文档列表
const refreshDocuments = () => {
  loadDocuments()
}

// 预览文档
const previewDocument = async (doc: DocumentInfo) => {
  try {
    // 检查文档类型
    if (doc.fileStatus === '纯文本' || doc.fileStatus === '无文件') {
      // 纯文本文档，获取内容并在新窗口显示
      const contentResponse = await fetch(`/api/ai/knowledge/content/${doc.documentId}`);
      if (!contentResponse.ok) {
        throw new Error('获取文档内容失败');
      }

      const contentData = await contentResponse.json();
      const newWindow = window.open('', '_blank');
      if (newWindow) {
        newWindow.document.write(`
          <!DOCTYPE html>
          <html>
          <head>
            <title>${doc.title || 'Document Preview'}</title>
            <meta charset="utf-8">
            <style>
              body { 
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                line-height: 1.6; 
                max-width: 800px; 
                margin: 0 auto; 
                padding: 20px;
                background: #fff;
                color: #333;
              }
              .header {
                border-bottom: 1px solid #eee;
                padding-bottom: 10px;
                margin-bottom: 20px;
              }
              .title { 
                font-size: 24px; 
                font-weight: bold; 
                margin: 0;
                color: #2563eb;
              }
              .meta {
                font-size: 14px;
                color: #666;
                margin-top: 5px;
              }
              .content { 
                white-space: pre-wrap; 
                font-size: 16px;
                line-height: 1.8;
              }
            </style>
          </head>
          <body>
            <div class="header">
              <h1 class="title">${doc.title || 'Document Preview'}</h1>
              <div class="meta">
                文档类型: ${doc.fileStatus} | 
                切片数量: ${contentData.chunkCount} | 
                总字符数: ${contentData.totalTokens}
              </div>
            </div>
            <div class="content">${contentData.content}</div>
          </body>
          </html>
        `);
        newWindow.document.close();
      }
    } else {
      // 文件上传的文档，直接预览
      window.open(doc.previewUrl, '_blank');
    }
  } catch (error) {
    console.error('预览失败:', error);
    ElMessage.error('预览失败: ' + error.message);
  }
}

// 下载文档
const downloadDocument = async (doc: DocumentInfo) => {
  try {
    // 检查文档类型
    if (doc.fileStatus === '纯文本' || doc.fileStatus === '无文件') {
      // 纯文本文档，下载为文本文件
      const contentResponse = await fetch(`/api/ai/knowledge/content/${doc.documentId}`);
      if (!contentResponse.ok) {
        throw new Error('获取文档内容失败');
      }

      const contentData = await contentResponse.json();
      const blob = new Blob([contentData.content], {type: 'text/plain;charset=utf-8'});
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${doc.title || 'document'}.txt`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      ElMessage.success('下载成功');
    } else {
      // 文件上传的文档，直接下载
      window.open(doc.downloadUrl, '_blank');
    }
  } catch (error) {
    console.error('下载失败:', error);
    ElMessage.error('下载失败: ' + error.message);
  }
}

// 显示文档详情
const showDocumentDetails = async (doc: DocumentInfo) => {
  try {
    const response = await axios.get(`/api/ai/knowledge/citation/${doc.documentId}`)
    selectedDocument.value = {...doc, ...response.data}
    documentDetailsVisible.value = true
  } catch (error) {
    console.error('获取文档详情失败:', error)
    selectedDocument.value = doc
    documentDetailsVisible.value = true
  }
}

// 确认删除文档
const confirmDeleteDocument = (doc: DocumentInfo) => {
  ElMessageBox.confirm(
      `确定要删除文档 "${doc.title}" 吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
  ).then(() => {
    deleteDocument(doc.documentId)
  }).catch(() => {
    // 用户取消删除
  })
}

// 删除文档
const deleteDocument = async (documentId: string) => {
  try {
    await axios.delete(`/api/ai/knowledge/document/${documentId}`)
    ElMessage.success('文档删除成功')
    loadDocuments() // 重新加载列表
  } catch (error) {
    console.error('删除文档失败:', error)
    ElMessage.error('删除文档失败')
  }
}

// --- 工具方法 ---

// 获取文件图标
const getFileIcon = (mimeType: string) => {
  if (!mimeType) return 'ri-file-line'

  if (mimeType.includes('pdf')) return 'ri-file-pdf-line'
  if (mimeType.includes('word') || mimeType.includes('document')) return 'ri-file-word-line'
  if (mimeType.includes('excel') || mimeType.includes('spreadsheet')) return 'ri-file-excel-line'
  if (mimeType.includes('powerpoint') || mimeType.includes('presentation')) return 'ri-file-ppt-line'
  if (mimeType.includes('image')) return 'ri-image-line'
  if (mimeType.includes('text')) return 'ri-file-text-line'

  return 'ri-file-line'
}

// 格式化日期
const formatDate = (dateString: string) => {
  if (!dateString) return '未知时间'

  try {
    const date = new Date(dateString)
    // 检查日期是否有效
    if (isNaN(date.getTime())) {
      return '无效时间'
    }

    const now = new Date()
    const diffTime = now.getTime() - date.getTime()
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24))

    if (diffDays === 0) return '今天'
    if (diffDays === 1) return '昨天'
    if (diffDays < 7) return `${diffDays}天前`

    return date.toLocaleDateString()
  } catch (error) {
    console.error('时间格式化错误:', error, dateString)
    return '时间格式错误'
  }
}

// 格式化完整日期时间
const formatDateTime = (dateString: string) => {
  if (!dateString) return '未知时间'

  try {
    const date = new Date(dateString)
    // 检查日期是否有效
    if (isNaN(date.getTime())) {
      return '无效时间'
    }

    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch (error) {
    console.error('时间格式化错误:', error, dateString)
    return '时间格式错误'
  }
}

// 获取文件状态对应的标签类型
const getFileStatusType = (status: string) => {
  switch (status) {
    case '文件正常':
      return 'success'
    case '纯文本':
      return 'info'
    case '文件缺失':
      return 'danger'
    case '无文件':
      return 'warning'
    default:
      return 'info'
  }
}

// 格式化文件大小（基于token数量估算）
const formatFileSize = (tokens: number) => {
  if (!tokens) return '未知'

  // 粗略估算：1个token约等于4个字符，1个字符约2字节
  const bytes = tokens * 4 * 2

  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`

  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

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

/* 文档管理样式 */
.document-management {
  margin-top: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.loading-state {
  padding: 20px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 16px;
  margin: 0 0 8px 0;
  color: #606266;
}

.empty-tip {
  font-size: 14px;
  color: #c0c4cc;
}

.documents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 16px;
}

.document-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: white;
  transition: all 0.3s;
}

.document-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.1);
}

.document-header {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.document-icon {
  width: 40px;
  height: 40px;
  background: #f0f9ff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #409eff;
  flex-shrink: 0;
}

.document-info {
  flex: 1;
  min-width: 0;
}

.document-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.meta-item i {
  font-size: 14px;
}

.document-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.document-actions .el-button {
  flex: 1;
  min-width: 0;
}

/* 文档详情对话框 */
.document-details {
  max-height: 400px;
  overflow-y: auto;
}

.detail-row {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
}

.detail-row label {
  font-weight: 500;
  color: #606266;
  min-width: 80px;
  margin-right: 12px;
  flex-shrink: 0;
}

.detail-value {
  color: #303133;
  flex: 1;
  word-break: break-all;
}

.metadata-display {
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  font-size: 12px;
  color: #606266;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  margin: 0;
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .documents-grid {
    grid-template-columns: 1fr;
  }

  .section-header {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .header-actions {
    justify-content: space-between;
  }

  .document-actions {
    flex-direction: column;
  }

  .document-actions .el-button {
    flex: none;
  }
}
</style>