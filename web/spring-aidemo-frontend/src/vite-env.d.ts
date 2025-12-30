/// <reference types="vite/client" />
// Vite 客户端类型引用，提供 Vite 相关的类型定义

// Vue 单文件组件模块声明
// 告诉 TypeScript 如何处理 .vue 文件的导入
declare module '*.vue' {
    import type {DefineComponent} from 'vue'
    // 定义 Vue 组件的类型，允许在 TypeScript 中导入 .vue 文件
    const component: DefineComponent<{}, {}, any>
    export default component
}