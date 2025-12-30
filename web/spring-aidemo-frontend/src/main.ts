// ==================== Vue 3 应用程序入口文件 ====================
// 这是前端应用的启动入口，负责初始化Vue应用和相关插件

import {createApp} from 'vue'      // 导入Vue 3的应用创建函数
import ElementPlus from 'element-plus'  // 导入Element Plus UI组件库
import 'element-plus/dist/index.css'    // 导入Element Plus的CSS样式
import App from './App.vue'             // 导入根组件

// ==================== 创建Vue应用实例 ====================
// createApp()是Vue 3的新API，用于创建应用实例
// 相比Vue 2的new Vue()，提供了更好的TypeScript支持和组合式API
const app = createApp(App)

// ==================== 注册插件和组件库 ====================
// 使用Element Plus作为UI组件库
// Element Plus是Element UI的Vue 3版本，提供丰富的组件：
// - 表单组件：输入框、按钮、选择器等
// - 布局组件：栅格、容器、分割面板等  
// - 反馈组件：消息提示、对话框、加载等
// - 数据展示：表格、分页、标签等
app.use(ElementPlus)

// ==================== 挂载应用到DOM ====================
// 将Vue应用挂载到HTML中id为'app'的元素上
// 这个元素定义在public/index.html文件中
app.mount('#app')

// ==================== 应用架构说明 ====================
// 前端应用采用以下技术栈：
// - Vue 3: 渐进式JavaScript框架，支持组合式API
// - TypeScript: 提供类型安全和更好的开发体验
// - Element Plus: 基于Vue 3的企业级UI组件库
// - Vite: 现代化的前端构建工具，提供快速的开发体验
// 
// 主要功能模块：
// - AI对话界面：支持流式对话、思维链展示、引用显示
// - 知识库管理：文档上传、向量化、搜索测试
// - 系统监控：性能指标、审计日志、会话统计
// 
// 与后端通信：
// - REST API：用于数据查询和操作
// - Server-Sent Events (SSE)：用于实时接收AI回复流
// - WebSocket：用于实时监控数据推送（如果需要）