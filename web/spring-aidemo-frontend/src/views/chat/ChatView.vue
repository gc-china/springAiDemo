<template>
  <div
      class="chat-layout h-[calc(100vh-60px)] flex flex-col md:flex-row overflow-hidden bg-white dark:bg-[#131314] text-[#111] dark:text-[#e3e3e3]">

    <!-- Login Modal -->
    <div v-if="!currentUser"
         class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 dark:bg-black/80 backdrop-blur-sm">
      <div
          class="bg-white dark:bg-[#1e1f20] p-8 rounded-2xl shadow-2xl w-96 border border-gray-200 dark:border-gray-700">
        <div class="text-center mb-8">
          <h1 class="text-3xl font-bold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent">
            Gemini Pro</h1>
          <p class="text-gray-500 dark:text-gray-400 mt-2">智能仓储助手</p>
        </div>
        <form @submit.prevent="login">
          <div class="mb-6">
            <label class="block text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">用户名</label>
            <input v-model="loginForm.username"
                   class="w-full bg-gray-50 dark:bg-[#131314] border border-gray-300 dark:border-gray-600 rounded-lg px-4 py-3 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none transition-all"
                   placeholder="请输入您的名字"
                   required
                   type="text">
          </div>
          <button
              class="w-full bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-bold py-3 px-4 rounded-lg transition-all transform hover:scale-[1.02]"
              type="submit">
            进入系统
          </button>
        </form>
      </div>
    </div>

    <!-- Sidebar -->
    <aside
        :class="['bg-gray-50 dark:bg-[#1e1f20] border-r border-gray-200 dark:border-gray-800 transition-all duration-300 flex flex-col z-20', isSidebarOpen ? 'w-64' : 'w-0 md:w-16']">
      <div class="p-4 flex items-center justify-between border-b border-gray-200 dark:border-gray-800">
        <div v-if="isSidebarOpen"
             class="font-bold text-xl bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent truncate">
          Gemini Pro
        </div>
        <button
            class="p-2 hover:bg-gray-200 dark:hover:bg-gray-700 rounded-lg text-gray-500 dark:text-gray-400 transition-colors"
            @click="toggleSidebar">
          <i class="ri-menu-line text-xl"></i>
        </button>
      </div>

      <div class="p-3">
        <button
            class="w-full flex items-center justify-center gap-2 bg-white dark:bg-[#2d2e30] hover:bg-gray-100 dark:hover:bg-[#3c3d40] text-gray-700 dark:text-gray-200 py-3 px-4 rounded-full transition-all border border-gray-200 dark:border-gray-700 shadow-sm group"
            @click="startNewChat">
          <i class="ri-add-line text-xl group-hover:rotate-90 transition-transform"></i>
          <span v-if="isSidebarOpen" class="font-medium">新对话</span>
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-2 space-y-1">
        <div v-if="isSidebarOpen" class="text-xs font-semibold text-gray-500 px-3 py-2">最近对话</div>
        <div v-for="chat in chatHistory" :key="chat.id"
             :class="['group flex items-center gap-3 p-3 rounded-lg cursor-pointer transition-colors relative', currentChatId === chat.id ? 'bg-blue-50 dark:bg-[#004a77]/30 text-blue-600 dark:text-blue-300' : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-[#2d2e30] hover:text-gray-900 dark:hover:text-gray-200']"
             @click="loadChat(chat.id)">
          <i class="ri-message-3-line"></i>
          <span v-if="isSidebarOpen" class="truncate text-sm">{{ chat.title || '新对话' }}</span>
          <button v-if="isSidebarOpen"
                  class="absolute right-2 opacity-0 group-hover:opacity-100 p-1 hover:text-red-500 dark:hover:text-red-400 transition-opacity"
                  @click.stop="deleteChat(chat.id)">
            <i class="ri-delete-bin-line"></i>
          </button>
        </div>
      </div>

      <div class="p-4 border-t border-gray-200 dark:border-gray-800 space-y-2">
        <!-- Monitor Link -->
        <button v-if="isSidebarOpen"
                class="w-full flex items-center gap-3 p-2 rounded-lg text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-[#2d2e30] transition-colors"
                @click="$emit('open-monitor')">
          <i class="ri-dashboard-line"></i>
          <span class="text-sm">系统监控</span>
        </button>

        <!-- Theme Toggle -->
        <button
            class="w-full flex items-center gap-3 p-2 rounded-lg text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-[#2d2e30] transition-colors"
            @click="toggleTheme">
          <i :class="isDarkMode ? 'ri-moon-line' : 'ri-sun-line'"></i>
          <span v-if="isSidebarOpen" class="text-sm">{{ isDarkMode ? '深色模式' : '浅色模式' }}</span>
        </button>

        <!-- User Info -->
        <div class="flex items-center gap-3 pt-2 border-t border-gray-200 dark:border-gray-800">
          <div
              class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-xs font-bold text-white">
            {{ currentUser ? currentUser.charAt(0).toUpperCase() : 'U' }}
          </div>
          <div v-if="isSidebarOpen" class="flex-1 min-w-0">
            <div class="text-sm font-medium truncate text-gray-700 dark:text-gray-200">{{ currentUser }}</div>
          </div>
          <button v-if="isSidebarOpen" class="text-gray-500 hover:text-red-500 transition-colors"
                  @click="logout">
            <i class="ri-logout-box-r-line"></i>
          </button>
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="flex-1 flex flex-col relative h-full">
      <!-- Header (Mobile) -->
      <div
          class="md:hidden p-4 flex items-center justify-between border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-[#131314]">
        <button class="text-gray-500 dark:text-gray-400" @click="toggleSidebar">
          <i class="ri-menu-line text-xl"></i>
        </button>
        <span class="font-bold">Gemini Pro</span>
        <div class="w-6"></div>
      </div>

      <!-- Chat Area -->
      <div ref="chatContainer" class="flex-1 overflow-y-auto p-4 md:p-8 space-y-6 scroll-smooth">
        <div v-if="currentMessages.length === 0"
             class="h-full flex flex-col items-center justify-center text-center opacity-50">
          <div
              class="w-20 h-20 bg-gradient-to-br from-blue-500/20 to-purple-500/20 rounded-full flex items-center justify-center mb-6 animate-pulse-slow">
            <i class="ri-sparkling-2-fill text-4xl text-blue-500"></i>
          </div>
          <h2 class="text-2xl font-bold mb-2">有什么可以帮您的吗？</h2>
          <p class="text-gray-500 dark:text-gray-400 max-w-md">
            我可以帮您查询库存、调拨商品，或者回答关于仓储管理的任何问题。</p>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-8 w-full max-w-2xl">
            <button
                class="p-4 bg-gray-50 dark:bg-[#1e1f20] hover:bg-gray-100 dark:hover:bg-[#2d2e30] rounded-xl text-left transition-colors border border-gray-200 dark:border-gray-800 hover:border-gray-300 dark:hover:border-gray-600"
                @click="quickAsk('查一下苹果15的库存')">
              <i class="ri-search-line text-blue-500 mb-2 block"></i>
              <span class="text-sm font-medium">查一下苹果15的库存</span>
            </button>
            <button
                class="p-4 bg-gray-50 dark:bg-[#1e1f20] hover:bg-gray-100 dark:hover:bg-[#2d2e30] rounded-xl text-left transition-colors border border-gray-200 dark:border-gray-800 hover:border-gray-300 dark:hover:border-gray-600"
                @click="quickAsk('把上海的10个iPhone 15发到北京')">
              <i class="ri-truck-line text-purple-500 mb-2 block"></i>
              <span class="text-sm font-medium">把上海的10个iPhone 15发到北京</span>
            </button>
          </div>
        </div>

        <div v-for="(msg, index) in currentMessages" :key="index"
             :class="['flex gap-4 max-w-4xl mx-auto', msg.role === 'user' ? 'flex-row-reverse' : '']">

          <!-- Avatar -->
          <div :class="['w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center text-xs font-bold',
                        msg.role === 'user' ? 'bg-gray-200 dark:bg-gray-600 text-gray-700 dark:text-white' : 'bg-gradient-to-br from-blue-500 to-purple-600 text-white']">
            <i v-if="msg.role === 'ai'" class="ri-sparkling-2-fill"></i>
            <span v-else>U</span>
          </div>

          <!-- Message Bubble -->
          <div :class="['max-w-[85%] rounded-2xl px-5 py-3 shadow-sm',
                        msg.role === 'user' ? 'bg-gray-100 dark:bg-[#2d2e30] text-gray-900 dark:text-white' : 'bg-transparent text-gray-900 dark:text-gray-100']">

            <!-- Thinking Chain (Collapsible) -->
            <div v-if="msg.thinking" class="mb-3">
              <details class="group">
                <summary
                    class="flex items-center gap-2 cursor-pointer text-xs font-medium text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-colors select-none">
                  <i class="ri-brain-line"></i>
                  <span>思考过程</span>
                  <i class="ri-arrow-down-s-line group-open:rotate-180 transition-transform"></i>
                </summary>
                <div
                    class="mt-2 p-3 bg-gray-50 dark:bg-[#1e1f20] rounded-lg border border-gray-200 dark:border-gray-800 text-xs text-gray-600 dark:text-gray-400 font-mono whitespace-pre-wrap leading-relaxed">
                  {{ msg.thinking }}
                </div>
              </details>
            </div>

            <!-- Content -->
            <div v-if="msg.role === 'ai'" class="markdown-body">
              <div v-html="renderMarkdown(msg.content)"></div>

              <!-- 引用来源列表 -->
              <div v-if="msg.citations && msg.citations.length > 0" class="citations-section">
                <h4 class="citations-title">📚 参考来源：</h4>
                <div class="citations-list">
                  <div
                      v-for="(citation, index) in msg.citations"
                      :id="`citation-${index + 1}`"
                      :key="index"
                      :class="{ 'highlighted': highlightedCitation === index + 1 }"
                      class="citation-item"
                  >
                    <div class="citation-header">
                      <span class="citation-number">[{{ index + 1 }}]</span>
                      <span class="citation-filename">{{ citation.filename }}</span>
                      <span class="citation-location">{{ citation.location }}</span>
                    </div>
                    <div class="citation-actions">
                      <button
                          :disabled="false"
                          class="citation-btn download-btn"
                          @click="downloadFile(citation.documentId)"
                      >
                        <i class="ri-download-line"></i>
                        下载
                      </button>
                      <button
                          :disabled="false"
                          class="citation-btn preview-btn"
                          @click="previewFile(citation.documentId)"
                      >
                        <i class="ri-eye-line"></i>
                        预览
                      </button>
                      <button
                          class="citation-btn info-btn"
                          @click="showCitationDetails(citation)"
                      >
                        <i class="ri-information-line"></i>
                        详情
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="whitespace-pre-wrap">{{ msg.content }}</div>

            <span v-if="msg.isStreaming" class="typing-cursor"></span>

            <div v-if="msg.role === 'ai' && msg.verification"
                 :class="['status-badge', msg.verification.status]" :title="msg.verification.reason">
              <i :class="msg.verification.icon"></i>
              <span>{{ msg.verification.text }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <div class="p-4 bg-white dark:bg-[#131314] border-t border-gray-200 dark:border-gray-800">
        <div class="max-w-4xl mx-auto relative">
          <textarea ref="textarea" v-model="inputMessage"
                    class="w-full bg-gray-100 dark:bg-[#1e1f20] text-gray-900 dark:text-white rounded-2xl pl-4 pr-12 py-4 focus:outline-none focus:ring-2 focus:ring-blue-500/50 resize-none overflow-hidden shadow-lg border border-gray-200 dark:border-gray-700 placeholder-gray-500"
                    placeholder="输入您的问题..."
                    rows="1"
                    style="min-height: 56px; max-height: 200px;"
                    @keydown.enter.prevent="handleEnter"></textarea>

          <button :disabled="!inputMessage.trim() || isGenerating"
                  class="absolute right-2 bottom-2 p-2 rounded-xl bg-blue-600 dark:bg-white text-white dark:text-black hover:bg-blue-700 dark:hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                  @click="sendMessage">
            <i v-if="isGenerating" class="ri-stop-circle-line text-xl"></i>
            <i v-else class="ri-send-plane-fill text-xl"></i>
          </button>
        </div>
        <div class="text-center text-xs text-gray-500 mt-2">
          Gemini Pro may display inaccurate info, including about people, so double-check its responses.
        </div>
      </div>
    </main>

    <!-- 引用详情模态框 -->
    <div v-if="citationDetailsVisible" class="citation-modal-overlay" @click="citationDetailsVisible = false">
      <div class="citation-modal" @click.stop>
        <div class="citation-modal-header">
          <h3>📄 文档详情</h3>
          <button class="close-btn" @click="citationDetailsVisible = false">
            <i class="ri-close-line"></i>
          </button>
        </div>
        <div v-if="selectedCitation" class="citation-modal-content">
          <div class="citation-detail-item">
            <label>文件名：</label>
            <span>{{ selectedCitation.filename }}</span>
          </div>
          <div class="citation-detail-item">
            <label>位置：</label>
            <span>{{ selectedCitation.location }}</span>
          </div>
          <div v-if="selectedCitation.mimeType" class="citation-detail-item">
            <label>文件类型：</label>
            <span>{{ selectedCitation.mimeType }}</span>
          </div>
          <div v-if="selectedCitation.totalTokens" class="citation-detail-item">
            <label>总Token数：</label>
            <span>{{ selectedCitation.totalTokens }}</span>
          </div>
          <div v-if="selectedCitation.chunkCount" class="citation-detail-item">
            <label>切片数量：</label>
            <span>{{ selectedCitation.chunkCount }}</span>
          </div>
          <div v-if="selectedCitation.createdAt" class="citation-detail-item">
            <label>创建时间：</label>
            <span>{{ new Date(selectedCitation.createdAt).toLocaleString() }}</span>
          </div>
          <div class="citation-modal-actions">
            <button
                :disabled="false"
                class="citation-btn download-btn"
                @click="downloadFile(selectedCitation.documentId)"
            >
              <i class="ri-download-line"></i>
              下载文件
            </button>
            <button
                :disabled="false"
                class="citation-btn preview-btn"
                @click="previewFile(selectedCitation.documentId)"
            >
              <i class="ri-eye-line"></i>
              预览文件
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {ref, nextTick, onMounted, watch} from 'vue';
import {marked} from 'marked';
import hljs from 'highlight.js';

const emit = defineEmits(['open-monitor']);

// --- Types ---
interface Message {
  role: 'user' | 'ai';
  content: string;
  timestamp: number;
  isStreaming?: boolean;
  thinking?: string;
  verification?: {
    status: string;
    icon: string;
    text: string;
    reason?: string;
  } | null;
  citations?: Citation[];
}

interface Citation {
  documentId: string;
  filename: string;
  location: string;
  downloadUrl: string;
  previewUrl: string;
  fileExists: boolean;
  fileStatus: string; // 新增：文件状态描述
  mimeType?: string;
  totalTokens?: number;
  chunkCount?: number;
  createdAt?: string;
}

interface ChatSession {
  id: string;
  uuid: string;
  username: string;
  title: string;
  messages: Message[];
  timestamp: number;
}

// --- State ---
const currentUser = ref(localStorage.getItem('currentUser') || '');
const loginForm = ref({username: ''});
const isSidebarOpen = ref(window.innerWidth > 768);
const isDarkMode = ref(localStorage.getItem('theme') !== 'light');
const inputMessage = ref('');
const isGenerating = ref(false);
const chatContainer = ref<HTMLElement | null>(null);
const textarea = ref<HTMLElement | null>(null);

const chatHistory = ref<ChatSession[]>([]);
const currentChatId = ref<string | null>(null);
const currentChatUUID = ref<string | null>(null);
const currentMessages = ref<Message[]>([]);
const highlightedCitation = ref<number | null>(null);
const citationDetailsVisible = ref(false);
const selectedCitation = ref<Citation | null>(null);

// --- Markdown Setup ---
marked.setOptions({
  highlight: function (code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, {language: lang}).value;
    }
    return hljs.highlightAuto(code).value;
  },
  breaks: true
});

// --- Methods ---

// UUID Generator
function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

// Global copy function (attached to window for marked HTML onclick)
onMounted(() => {
  (window as any).copyCode = (btn: HTMLElement) => {
    const pre = btn.parentElement;
    const code = pre?.querySelector('code');
    const text = code?.innerText || '';

    navigator.clipboard.writeText(text).then(() => {
      const originalText = btn.innerText;
      btn.innerText = '已复制!';
      setTimeout(() => {
        btn.innerText = '复制';
      }, 2000);
    });
  };

  // 全局引用高亮函数
  (window as any).highlightCitation = (citationNumber: number) => {
    highlightedCitation.value = citationNumber;

    // 滚动到对应的引用来源
    const element = document.getElementById(`citation-${citationNumber}`);
    if (element) {
      element.scrollIntoView({behavior: 'smooth', block: 'center'});
    }

    // 2秒后取消高亮
    setTimeout(() => {
      highlightedCitation.value = null;
    }, 2000);
  };

  updateTheme();
  if (currentUser.value) {
    loadHistory();
  }
});

const renderMarkdown = (text: string) => {
  let html = marked.parse(text) as string;
  // Add copy button
  html = html.replace(/<pre><code/g, '<pre><button class="copy-btn" onclick="copyCode(this)">复制</button><code');

  // 处理引用链接，添加点击事件
  html = html.replace(/\[(\d+)\]/g, (match, number) => {
    return `<span class="citation-link" onclick="highlightCitation(${number})">[${number}]</span>`;
  });

  return html;
};

const toggleTheme = () => {
  isDarkMode.value = !isDarkMode.value;
  updateTheme();
};

const updateTheme = () => {
  if (isDarkMode.value) {
    document.documentElement.classList.add('dark');
    document.body.classList.add('dark');
    localStorage.setItem('theme', 'dark');
  } else {
    document.documentElement.classList.remove('dark');
    document.body.classList.remove('dark');
    localStorage.setItem('theme', 'light');
  }
};

const login = () => {
  if (loginForm.value.username.trim()) {
    currentUser.value = loginForm.value.username;
    localStorage.setItem('currentUser', currentUser.value);
    loadHistory();
  }
};

const logout = () => {
  currentUser.value = '';
  localStorage.removeItem('currentUser');
  currentMessages.value = [];
  currentChatId.value = null;
  chatHistory.value = [];
};

const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value;
};

const getHistoryKey = () => `chatHistory_${currentUser.value}`;

const loadHistory = () => {
  const key = getHistoryKey();
  chatHistory.value = JSON.parse(localStorage.getItem(key) || '[]');
  if (chatHistory.value.length === 0) {
    startNewChat();
  } else {
    loadChat(chatHistory.value[0].id);
  }
};

const startNewChat = () => {
  const newChat: ChatSession = {
    id: Date.now().toString(),
    uuid: generateUUID(),
    username: currentUser.value,
    title: '新对话',
    messages: [],
    timestamp: Date.now()
  };
  chatHistory.value.unshift(newChat);
  saveHistory();
  loadChat(newChat.id);
};

const loadChat = (id: string) => {
  currentChatId.value = id;
  const chat = chatHistory.value.find(c => c.id === id);
  if (chat) {
    currentMessages.value = chat.messages;
    currentChatUUID.value = chat.uuid || null;
    nextTick(scrollToBottom);
  }
};

const deleteChat = (id: string) => {
  chatHistory.value = chatHistory.value.filter(c => c.id !== id);
  saveHistory();
  if (currentChatId.value === id) {
    if (chatHistory.value.length > 0) {
      loadChat(chatHistory.value[0].id);
    } else {
      startNewChat();
    }
  }
};

const saveHistory = () => {
  if (currentUser.value) {
    localStorage.setItem(getHistoryKey(), JSON.stringify(chatHistory.value));
  }
};

const updateCurrentChat = () => {
  const chat = chatHistory.value.find(c => c.id === currentChatId.value);
  if (chat) {
    chat.messages = currentMessages.value;
    if (chat.messages.length > 0 && chat.title === '新对话') {
      chat.title = chat.messages[0].content.slice(0, 20);
    }
    chat.timestamp = Date.now();
    saveHistory();
  }
};

const scrollToBottom = () => {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
  }
};

const autoResizeTextarea = () => {
  const el = textarea.value;
  if (el) {
    el.style.height = 'auto';
    el.style.height = el.scrollHeight + 'px';
  }
};

watch(inputMessage, () => {
  nextTick(autoResizeTextarea);
});

const handleEnter = (e: KeyboardEvent) => {
  if (!e.shiftKey) {
    sendMessage();
  }
};

const quickAsk = (msg: string) => {
  inputMessage.value = msg;
  sendMessage();
};

// 文件下载
const downloadFile = async (documentId: string) => {
  try {
    // 先获取文档信息
    const response = await fetch(`/api/ai/knowledge/citation/${documentId}`);
    if (!response.ok) {
      throw new Error('获取文档信息失败');
    }

    const docInfo = await response.json();

    // 检查文档类型
    if (docInfo.fileStatus === '纯文本' || docInfo.fileStatus === '无文件') {
      // 纯文本文档，下载为文本文件
      const contentResponse = await fetch(`/api/ai/knowledge/content/${documentId}`);
      if (!contentResponse.ok) {
        throw new Error('获取文档内容失败');
      }

      const contentData = await contentResponse.json();
      const blob = new Blob([contentData.content], {type: 'text/plain;charset=utf-8'});
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${docInfo.title || 'document'}.txt`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } else {
      // 文件上传的文档，直接下载
      const url = `/api/ai/knowledge/download/${documentId}`;
      window.open(url, '_blank');
    }
  } catch (error) {
    console.error('下载失败:', error);
    alert('下载失败: ' + error.message);
  }
};

// 文件预览
const previewFile = async (documentId: string) => {
  try {
    // 先获取文档信息
    const response = await fetch(`/api/ai/knowledge/citation/${documentId}`);
    if (!response.ok) {
      throw new Error('获取文档信息失败');
    }

    const docInfo = await response.json();

    // 检查文档类型
    if (docInfo.fileStatus === '纯文本' || docInfo.fileStatus === '无文件') {
      // 纯文本文档，在新窗口显示内容
      const contentResponse = await fetch(`/api/ai/knowledge/content/${documentId}`);
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
            <title>${docInfo.title || 'Document Preview'}</title>
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
              <h1 class="title">${docInfo.title || 'Document Preview'}</h1>
              <div class="meta">
                文档类型: ${docInfo.fileStatus} | 
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
      const url = `/api/ai/knowledge/preview/${documentId}`;
      window.open(url, '_blank');
    }
  } catch (error) {
    console.error('预览失败:', error);
    alert('预览失败: ' + error.message);
  }
};

// 显示引用详情
const showCitationDetails = async (citation: Citation) => {
  try {
    const response = await fetch(`/api/ai/knowledge/citation/${citation.documentId}`);
    const details = await response.json();

    selectedCitation.value = {...citation, ...details};
    citationDetailsVisible.value = true;
  } catch (error) {
    console.error('获取引用详情失败:', error);
    // 使用现有信息显示
    selectedCitation.value = citation;
    citationDetailsVisible.value = true;
  }
};

// 解析引用信息
const parseCitations = (content: string): Citation[] => {
  const citations: Citation[] = [];

  // 查找参考来源部分
  const sourcesMatch = content.match(/\*\*参考来源：?\*\*\s*([\s\S]*?)(?:\n\n|$)/);
  if (!sourcesMatch) return citations;

  const sourcesText = sourcesMatch[1];
  const sourceLines = sourcesText.split('\n').filter(line => line.trim());

  sourceLines.forEach(line => {
    // 匹配格式：[1] 文件名：xxx.pdf，来源：第X页/第X段
    const match = line.match(/\[(\d+)\]\s*文件名：([^，]+)，来源：(.+)/);
    if (match) {
      const [, number, filename, location] = match;
      citations.push({
        documentId: `doc-${number}`, // 临时ID，实际应该从后端获取
        filename: filename.trim(),
        location: location.trim(),
        downloadUrl: `/api/ai/knowledge/download/doc-${number}`,
        previewUrl: `/api/ai/knowledge/preview/doc-${number}`,
        fileExists: true
      });
    }
  });

  return citations;
};

const sendMessage = () => {
  const msg = inputMessage.value.trim();
  if (!msg || isGenerating.value) return;

  // 1. UI 初始化
  currentMessages.value.push({role: 'user', content: msg, timestamp: Date.now()});
  inputMessage.value = '';
  isGenerating.value = true;
  updateCurrentChat();
  nextTick(() => {
    scrollToBottom();
    autoResizeTextarea();
  });

  // 2. AI 消息占位
  const aiMsgIndex = currentMessages.value.push({
    role: 'ai', content: '', isStreaming: true, timestamp: Date.now(),
    verification: {status: 'loading', icon: 'ri-loader-4-line animate-spin', text: '正在验证...'}
  }) - 1;

  // 3. 建立连接
  // 注意：这里使用 /api 前缀，依赖 vite.config.ts 中的 proxy 转发到后端
  const url = `/api/three-stage/stream?chatId=${currentChatId.value}&msg=${encodeURIComponent(msg)}`;
  console.log("发起请求:", url);
  let eventSource: EventSource | null = new EventSource(url);

  let isClosed = false;
  const forceClose = () => {
    if (isClosed) return;
    isClosed = true;

    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }

    isGenerating.value = false;
    if (currentMessages.value[aiMsgIndex]) {
      currentMessages.value[aiMsgIndex].isStreaming = false;
    }
    updateCurrentChat();
    nextTick(() => {
      if (textarea.value) textarea.value.focus();
    });
  };

  eventSource.addEventListener('message', (e) => {
    if (isClosed) return;
    currentMessages.value[aiMsgIndex].content += e.data;
    nextTick(scrollToBottom);
  });

  eventSource.addEventListener('verification', (e) => {
    if (isClosed) return;
    try {
      const result = JSON.parse(e.data);
      const aiMsg = currentMessages.value[aiMsgIndex];

      if (result.passed) {
        if (result.confidence <= 0.55) {
          aiMsg.verification = {status: 'info', icon: 'ri-lightbulb-flash-line', text: '通用知识'};
        } else {
          aiMsg.verification = {
            status: 'success',
            icon: 'ri-shield-check-fill',
            text: `来源可信 (${(result.confidence * 100).toFixed(0)}%)`
          };
        }
      } else {
        aiMsg.verification = {status: 'warning', icon: 'ri-alert-fill', text: '内容存疑', reason: result.reason};
      }

    } catch (err) {
      console.error("验证结果解析失败", err);
      // 降级处理：显示默认验证状态
      const aiMsg = currentMessages.value[aiMsgIndex];
      aiMsg.verification = {status: 'info', icon: 'ri-lightbulb-flash-line', text: '通用知识'};
    }
    forceClose();
  });

  // 新增：处理引用信息事件
  eventSource.addEventListener('citations', (e) => {
    if (isClosed) return;
    try {
      const citationsData = JSON.parse(e.data);
      const aiMsg = currentMessages.value[aiMsgIndex];

      // 将后端返回的引用数据转换为前端格式
      aiMsg.citations = citationsData.map((citation: any) => ({
        documentId: citation.documentId,
        filename: citation.filename,
        location: citation.location,
        downloadUrl: citation.downloadUrl,
        previewUrl: citation.previewUrl,
        fileExists: citation.fileExists,
        fileStatus: citation.fileStatus,
        mimeType: citation.mimeType
      }));

      console.log('收到引用信息:', aiMsg.citations);
    } catch (err) {
      console.error("引用信息解析失败", err);
      // 降级：使用文本解析
      const aiMsg = currentMessages.value[aiMsgIndex];
      aiMsg.citations = parseCitations(aiMsg.content);
    }
  });

  eventSource.onerror = (e) => {
    const aiMsg = currentMessages.value[aiMsgIndex];
    if (aiMsg && aiMsg.verification && aiMsg.verification.status === 'loading') {
      aiMsg.verification = null;
    }
    forceClose();
  };
};
</script>

<style scoped>
/* Markdown Styles */
:deep(.markdown-body) {
  font-size: 1rem;
  line-height: 1.6;
}

:deep(.markdown-body pre) {
  background: #f3f4f6;
  border-radius: 0.5rem;
  padding: 1rem;
  margin: 1rem 0;
  overflow-x: auto;
  position: relative;
}

.dark :deep(.markdown-body pre) {
  background: #282c34;
}

:deep(.markdown-body code) {
  font-family: 'Fira Code', monospace;
  font-size: 0.9em;
}

:deep(.markdown-body p) {
  margin-bottom: 1rem;
}

:deep(.markdown-body ul), :deep(.markdown-body ol) {
  padding-left: 1.5rem;
  margin-bottom: 1rem;
}

:deep(.markdown-body blockquote) {
  border-left: 4px solid #0ea5e9;
  padding-left: 1rem;
  color: #6b7280;
  margin: 1rem 0;
}

.dark :deep(.markdown-body blockquote) {
  color: #9ca3af;
}

/* Copy Button */
:deep(.copy-btn) {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  padding: 0.25rem 0.5rem;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 0.25rem;
  color: #9ca3af;
  font-size: 0.75rem;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s;
  border: none;
}

.dark :deep(.copy-btn) {
  background: rgba(0, 0, 0, 0.3);
}

:deep(pre:hover .copy-btn) {
  opacity: 1;
}

:deep(.copy-btn:hover) {
  background: rgba(0, 165, 233, 0.2);
  color: #0ea5e9;
}

/* Typing Cursor */
.typing-cursor::after {
  content: '▋';
  animation: blink 1s step-start infinite;
  color: #0ea5e9;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

/* Status Badge */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  margin-top: 8px;
  transition: all 0.3s;
}

.status-badge.loading {
  background-color: #f3f4f6;
  color: #6b7280;
  border: 1px solid #e5e7eb;
}

.status-badge.success {
  background-color: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.status-badge.warning {
  background-color: #fffbeb;
  color: #d97706;
  border: 1px solid #fcd34d;
  cursor: help;
}

.status-badge.info {
  background-color: #eff6ff;
  color: #3b82f6;
  border: 1px solid #dbeafe;
}

.dark .status-badge.loading {
  background-color: #374151;
  color: #9ca3af;
  border-color: #4b5563;
}

.dark .status-badge.success {
  background-color: #064e3b;
  color: #34d399;
  border-color: #059669;
}

.dark .status-badge.warning {
  background-color: #78350f;
  color: #fcd34d;
  border-color: #b45309;
}

.dark .status-badge.info {
  background-color: #1e3a8a;
  color: #93c5fd;
  border-color: #1d4ed8;
}

/* 引用链接样式 */
:deep(.citation-link) {
  display: inline-block;
  background: #3b82f6;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  text-decoration: none;
  margin: 0 2px;
  cursor: pointer;
  transition: all 0.2s;
}

:deep(.citation-link:hover) {
  background: #1d4ed8;
  transform: scale(1.05);
}

/* 引用来源区域 */
.citations-section {
  margin-top: 20px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
  border-left: 4px solid #3b82f6;
}

.dark .citations-section {
  background: #1e1f20;
  border-left-color: #60a5fa;
}

.citations-title {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.dark .citations-title {
  color: #e5e7eb;
}

.citations-list {
  space-y: 8px;
}

.citation-item {
  background: white;
  border-radius: 6px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  transition: all 0.3s;
}

.dark .citation-item {
  background: #2d2e30;
  border-color: #4b5563;
}

.citation-item.highlighted {
  background: #dbeafe;
  border-color: #3b82f6;
  transform: scale(1.02);
}

.dark .citation-item.highlighted {
  background: #1e3a8a;
  border-color: #60a5fa;
}

.citation-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.citation-number {
  background: #3b82f6;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.citation-filename {
  font-weight: 500;
  color: #374151;
  flex: 1;
}

.dark .citation-filename {
  color: #e5e7eb;
}

.citation-location {
  font-size: 12px;
  color: #6b7280;
}

.dark .citation-location {
  color: #9ca3af;
}

.citation-actions {
  display: flex;
  gap: 8px;
}

.citation-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.citation-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.download-btn {
  background: #10b981;
  color: white;
}

.download-btn:hover:not(:disabled) {
  background: #059669;
}

.preview-btn {
  background: #6366f1;
  color: white;
}

.preview-btn:hover:not(:disabled) {
  background: #4f46e5;
}

.info-btn {
  background: #6b7280;
  color: white;
}

.info-btn:hover:not(:disabled) {
  background: #4b5563;
}

/* 引用详情模态框 */
.citation-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.citation-modal {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
  max-width: 500px;
  width: 90%;
  max-height: 80vh;
  overflow: hidden;
}

.dark .citation-modal {
  background: #1e1f20;
  border: 1px solid #374151;
}

.citation-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.dark .citation-modal-header {
  border-bottom-color: #374151;
}

.citation-modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.dark .citation-modal-header h3 {
  color: #f9fafb;
}

.close-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #6b7280;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.dark .close-btn:hover {
  background: #374151;
  color: #e5e7eb;
}

.citation-modal-content {
  padding: 20px;
}

.citation-detail-item {
  display: flex;
  margin-bottom: 12px;
  align-items: center;
}

.citation-detail-item label {
  font-weight: 500;
  color: #374151;
  min-width: 80px;
  margin-right: 12px;
}

.dark .citation-detail-item label {
  color: #d1d5db;
}

.citation-detail-item span {
  color: #6b7280;
  flex: 1;
}

.dark .citation-detail-item span {
  color: #9ca3af;
}

.citation-modal-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.dark .citation-modal-actions {
  border-top-color: #374151;
}
</style>