<!-- ==================== Vue 3 根组件 ==================== -->
<!-- 这是应用的根组件，负责整体布局和页面路由管理 -->

<script lang="ts" setup>
// ==================== 导入依赖 ====================
import {ref} from 'vue'  // Vue 3 响应式API
// 导入各个功能模块的组件
import SystemMonitor from './views/monitor/SystemMonitor.vue'        // 系统性能监控组件
import AuditDashboard from './views/monitor/AuditDashboard.vue'      // 审计日志监控组件  
import ChatView from './views/chat/ChatView.vue'                     // AI对话界面组件
import KnowledgeIngestion from './views/knowledge/KnowledgeIngestion.vue' // 知识库管理组件

// ==================== 响应式状态 ====================
// 当前激活的标签页，默认显示AI对话界面
// 使用ref()创建响应式引用，当值改变时会自动更新UI
const currentTab = ref('chat')
</script>

<template>
  <!-- ==================== 应用容器 ==================== -->
  <div class="app-container">

    <!-- ==================== 顶部导航栏 ==================== -->
    <!-- 提供应用的主要功能入口，采用标签页形式切换不同模块 -->
    <header class="nav-header">
      <!-- 应用Logo和名称 -->
      <div class="logo">Gemini Pro Admin</div>

      <!-- 导航菜单 -->
      <!-- 每个按钮对应一个功能模块，点击时切换currentTab的值 -->
      <nav>
        <!-- AI对话模块：核心功能，支持与AI进行智能对话 -->
        <button :class="{ active: currentTab === 'chat' }" @click="currentTab = 'chat'">AI 对话</button>

        <!-- 系统监控模块：查看系统性能指标、资源使用情况 -->
        <button :class="{ active: currentTab === 'monitor' }" @click="currentTab = 'monitor'">系统监控</button>

        <!-- 审计监控模块：查看工具调用日志、参数纠错记录 -->
        <button :class="{ active: currentTab === 'audit' }" @click="currentTab = 'audit'">审计监控</button>

        <!-- 知识库模块：管理文档、测试RAG检索功能 -->
        <button :class="{ active: currentTab === 'knowledge' }" @click="currentTab = 'knowledge'">知识库</button>
      </nav>
    </header>

    <!-- ==================== 内容区域 ==================== -->
    <!-- 根据当前选中的标签页显示对应的组件 -->
    <!-- 使用v-if进行条件渲染，只渲染当前激活的组件，提高性能 -->
    <div class="content">
      <!-- 系统监控组件：显示Redis、数据库、API等系统指标 -->
      <SystemMonitor v-if="currentTab === 'monitor'"/>

      <!-- 审计监控组件：显示工具调用记录、参数纠错统计等 -->
      <AuditDashboard v-if="currentTab === 'audit'"/>

      <!-- AI对话组件：主要功能界面，支持流式对话和知识检索 -->
      <!-- @open-monitor：子组件可以触发此事件来切换到监控页面 -->
      <ChatView v-if="currentTab === 'chat'" @open-monitor="currentTab = 'monitor'"/>

      <!-- 知识库管理组件：文档上传、向量化、搜索测试等功能 -->
      <KnowledgeIngestion v-if="currentTab === 'knowledge'"/>
    </div>
  </div>
</template>

<style>
/* ==================== 全局样式 ==================== */
/* 重置浏览器默认样式，设置应用背景色 */
body {
  margin: 0;
  background-color: #f5f7fa; /* 浅灰色背景，提供舒适的视觉体验 */
}

/* ==================== 导航栏样式 ==================== */
.nav-header {
  height: 60px; /* 固定导航栏高度 */
  background: #fff; /* 白色背景 */
  border-bottom: 1px solid #dcdfe6; /* 底部边框，与内容区分离 */
  display: flex; /* 弹性布局 */
  align-items: center; /* 垂直居中对齐 */
  padding: 0 20px; /* 左右内边距 */
  justify-content: space-between; /* 两端对齐（Logo在左，导航在右） */
}

/* Logo样式 */
.logo {
  font-weight: bold; /* 粗体字 */
  font-size: 1.2rem; /* 字体大小 */
  color: #409eff; /* Element Plus主题色（蓝色） */
}

/* 导航按钮基础样式 */
nav button {
  background: none; /* 无背景 */
  border: none; /* 无边框 */
  padding: 0 15px; /* 左右内边距 */
  height: 60px; /* 与导航栏同高 */
  cursor: pointer; /* 鼠标悬停时显示手型 */
  font-size: 1rem; /* 字体大小 */
  color: #606266; /* 默认文字颜色（深灰） */
  transition: all 0.3s; /* 平滑过渡动画 */
}

/* 导航按钮悬停效果 */
nav button:hover {
  color: #409eff; /* 悬停时变为主题色 */
}

/* 激活状态的导航按钮 */
nav button.active {
  color: #409eff; /* 主题色文字 */
  border-bottom: 2px solid #409eff; /* 底部高亮边框 */
  font-weight: 500; /* 中等粗细字体 */
}

/* ==================== 内容区域样式 ==================== */
.content {
  /* 内容区域高度 = 视窗高度 - 导航栏高度 */
  height: calc(100vh - 60px);
  overflow: hidden; /* 隐藏溢出内容，由各个子组件自行处理滚动 */
}

/* ==================== 设计说明 ==================== */
/*
应用采用经典的顶部导航 + 内容区域布局：

1. 导航栏设计：
   - 固定在顶部，高度60px
   - 左侧显示应用Logo
   - 右侧显示功能模块切换按钮
   - 激活状态有明显的视觉反馈

2. 内容区域设计：
   - 占据剩余的全部视窗高度
   - 根据导航选择动态切换显示的组件
   - 使用v-if条件渲染，避免不必要的组件实例

3. 交互体验：
   - 按钮有悬停和激活状态的视觉反馈
   - 使用CSS过渡动画提供平滑的交互体验
   - 颜色方案与Element Plus保持一致

4. 响应式考虑：
   - 使用相对单位和弹性布局
   - 适配不同屏幕尺寸
   - 各个子组件负责自己的响应式设计
*/
</style>
