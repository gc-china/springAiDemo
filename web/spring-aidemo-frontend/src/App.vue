<script lang="ts" setup>
import {ref} from 'vue'
import SystemMonitor from './views/monitor/SystemMonitor.vue'
import ChatView from './views/chat/ChatView.vue'
import KnowledgeIngestion from './views/knowledge/KnowledgeIngestion.vue'

const currentTab = ref('chat')
</script>

<template>
  <div class="app-container">
    <!-- 顶部导航栏 -->
    <header class="nav-header">
      <div class="logo">Gemini Pro Admin</div>
      <nav>
        <button :class="{ active: currentTab === 'chat' }" @click="currentTab = 'chat'">AI 对话</button>
        <button :class="{ active: currentTab === 'monitor' }" @click="currentTab = 'monitor'">系统监控</button>
        <button :class="{ active: currentTab === 'knowledge' }" @click="currentTab = 'knowledge'">知识库</button>
      </nav>
    </header>

    <!-- 内容区域 -->
    <div class="content">
      <SystemMonitor v-if="currentTab === 'monitor'"/>
      <ChatView v-if="currentTab === 'chat'" @open-monitor="currentTab = 'monitor'"/>
      <KnowledgeIngestion v-if="currentTab === 'knowledge'"/>
    </div>
  </div>
</template>

<style>
body {
  margin: 0;
  background-color: #f5f7fa;
}

.nav-header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  justify-content: space-between;
}

.logo {
  font-weight: bold;
  font-size: 1.2rem;
  color: #409eff;
}

nav button {
  background: none;
  border: none;
  padding: 0 15px;
  height: 60px;
  cursor: pointer;
  font-size: 1rem;
  color: #606266;
  transition: all 0.3s;
}

nav button:hover {
  color: #409eff;
}

nav button.active {
  color: #409eff;
  border-bottom: 2px solid #409eff;
  font-weight: 500;
}

.content {
  /* 减去导航栏高度 */
  height: calc(100vh - 60px);
  overflow: hidden;
}
</style>
