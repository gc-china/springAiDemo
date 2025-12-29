# RAG 引用来源和文件溯源完整解决方案

## 问题背景

原有的 RAG 系统存在以下问题：

1. **缺少引用来源标识**：用户不知道答案来自哪个文档
2. **无法文件溯源**：前端无法访问和下载原始文件
3. **元数据信息不完整**：检索结果缺少文件名、页码等关键信息

## 解决方案概览

### 核心功能

✅ **自动引用编号**：为每个检索到的文档自动分配引用编号 [1], [2], [3]...  
✅ **文件下载接口**：支持根据 documentId 下载原始文件  
✅ **文件预览接口**：支持在浏览器中直接预览 PDF、图片等  
✅ **引用信息查询**：获取文档的详细元数据信息  
✅ **智能引用格式**：AI 回答中自动插入引用标记  
✅ **参考来源列表**：在回答末尾显示完整的来源清单

### 技术架构

```
用户查询 → RAG检索 → 增强元数据 → 添加引用编号 → AI生成回答 → 返回带引用的结果
                                                    ↓
文件访问 ← 下载/预览接口 ← 文档ID查询 ← 引用信息API ← 前端引用组件
```

## 详细实现

### 1. 增强 RAG 检索服务 (RagService.java)

#### 新增功能

- **增强文档元数据**：为每个检索结果添加完整的文件信息
- **自动引用编号**：为最终文档分配引用编号 (1, 2, 3...)
- **文件访问链接**：生成下载和预览 URL

#### 关键代码

```java
/**
 * 增强文档元数据，添加文件来源信息
 */
private List<Document> enhanceDocumentsWithSourceInfo(List<Document> documents, String searchType) {
    return documents.stream().map(doc -> {
        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());

        // 添加搜索类型
        metadata.put("search_type", searchType);

        // 标准化文件信息
        String filename = (String) metadata.getOrDefault("filename", "未知文件");
        String documentId = (String) metadata.get("document_id");
        Integer chunkIndex = (Integer) metadata.get("chunk_index");

        metadata.put("source_filename", filename);
        metadata.put("source_document_id", documentId);
        metadata.put("source_chunk_index", chunkIndex != null ? chunkIndex : 0);

        // 生成文件访问 URL
        if (documentId != null) {
            metadata.put("download_url", "/api/ai/knowledge/download/" + documentId);
            metadata.put("preview_url", "/api/ai/knowledge/preview/" + documentId);
        }

        return new Document(doc.getId(), doc.getContent(), metadata);
    }).collect(Collectors.toList());
}

/**
 * 为最终文档添加引用编号
 */
private List<Document> addCitationNumbers(List<Document> documents) {
    List<Document> result = new ArrayList<>();
    for (int i = 0; i < documents.size(); i++) {
        Document doc = documents.get(i);
        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());

        // 添加引用编号（从1开始）
        int citationNumber = i + 1;
        metadata.put("citation_number", citationNumber);
        metadata.put("citation_id", "ref_" + citationNumber);

        result.add(new Document(doc.getId(), doc.getContent(), metadata));
    }
    return result;
}
```

### 2. 更新 AI 服务构建引用上下文 (AiService.java)

#### 新增功能

- **带引用信息的上下文构建**：为每个文档添加来源标识
- **智能引用格式**：【文档 1】(来源: policy.pdf, 第2段)

#### 关键代码

```java
// 构建带引用信息的 Prompt
StringBuilder contextBuilder = new StringBuilder();
for (int i = 0; i < finalDocuments.size(); i++) {
    Document doc = finalDocuments.get(i);
    Map<String, Object> metadata = doc.getMetadata();
    
    // 获取引用编号
    Integer citationNumber = (Integer) metadata.get("citation_number");
    if (citationNumber == null) citationNumber = i + 1;
    
    // 获取文件信息
    String filename = (String) metadata.getOrDefault("source_filename", "未知文件");
    Integer chunkIndex = (Integer) metadata.get("source_chunk_index");
    String chunkInfo = chunkIndex != null ? "第" + (chunkIndex + 1) + "段" : "未知位置";
    
    // 构建引用格式：【文档 1】(来源: policy.pdf, 第2段)
    contextBuilder.append(String.format("【文档 %d】(来源: %s, %s)\n%s\n\n",
            citationNumber, filename, chunkInfo, doc.getContent().trim()));
}
```

### 3. 更新提示词模板支持引用 (rag-enhanced-prompt.st)

#### 新增引用规则

```
【引用规则】：
1. 你的回答中如果使用了【背景知识】中的信息，**必须**显式引用来源编号。
2. 引用格式为 `[引用编号]`，例如 "库存充足`[1]`" 或 "根据政策规定`[2]`"。
3. 每一个关键事实陈述后面都应该紧跟引用。
4. 不要引用【工具】产生的结果，只引用【背景知识】。
5. 在回答末尾提供【参考来源】列表，格式如下：

**参考来源：**
[1] 文件名：xxx.pdf，来源：第X页/第X段
[2] 文件名：yyy.docx，来源：第X页/第X段
```

### 4. 新增文件访问接口 (KnowledgeBaseController.java)

#### 文件下载接口

```java

@GetMapping("/download/{documentId}")
public ResponseEntity<Resource> downloadDocument(@PathVariable String documentId) {
    // 1. 查询文档信息
    Document document = documentMapper.selectById(documentId);
    if (document == null) return ResponseEntity.notFound().build();

    // 2. 检查文件是否存在
    Path path = Paths.get(document.getFilePath());
    if (!Files.exists(path)) return ResponseEntity.notFound().build();

    // 3. 创建文件资源并设置响应头
    Resource resource = new FileSystemResource(path);
    String encodedFilename = URLEncoder.encode(document.getTitle(), StandardCharsets.UTF_8);

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
            .header(HttpHeaders.CONTENT_TYPE, document.getMimeType())
            .body(resource);
}
```

#### 文件预览接口

```java
@GetMapping("/preview/{documentId}")
public ResponseEntity<Resource> previewDocument(@PathVariable String documentId) {
    // 类似下载接口，但使用 "inline" 而不是 "attachment"
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .header(HttpHeaders.CONTENT_TYPE, mimeType)
            .body(resource);
}
```

#### 引用信息查询接口

```java
@GetMapping("/citation/{documentId}")
public ResponseEntity<Map<String, Object>> getDocumentCitation(@PathVariable String documentId) {
    Document document = documentMapper.selectById(documentId);
    if (document == null) return ResponseEntity.notFound().build();
    
    Map<String, Object> citation = new HashMap<>();
    citation.put("documentId", document.getId());
    citation.put("title", document.getTitle());
    citation.put("downloadUrl", "/api/ai/knowledge/download/" + documentId);
    citation.put("previewUrl", "/api/ai/knowledge/preview/" + documentId);
    // ... 其他元数据
    
    return ResponseEntity.ok(citation);
}
```

### 5. 增强文档摄入保存完整元数据 (KnowledgeBaseService.java)

#### 向量存储元数据增强

```java
// 准备写入向量表
Map<String, Object> vectorMeta = new HashMap<>(metadata);
vectorMeta.put("document_id", documentId);
vectorMeta.put("chunk_index", i);
vectorMeta.put("chunk_hash", chunkHash);
// 添加文件信息用于引用
vectorMeta.put("filename", title);
vectorMeta.put("source_filename", title);
vectorMeta.put("source_document_id", documentId);
vectorMeta.put("source_chunk_index", i);
vectorMeta.put("source_mime_type", metadata.get("mime_type"));
```

## API 接口文档

### 文件访问接口

| 接口                                        | 方法  | 功能     | 参数         | 返回值          |
|-------------------------------------------|-----|--------|------------|--------------|
| `/api/ai/knowledge/download/{documentId}` | GET | 下载原始文件 | documentId | 文件流          |
| `/api/ai/knowledge/preview/{documentId}`  | GET | 预览文件   | documentId | 文件流 (inline) |
| `/api/ai/knowledge/citation/{documentId}` | GET | 获取引用信息 | documentId | JSON 元数据     |

### 引用信息 API 响应格式

```json
{
  "documentId": "a338cd4a-43af-487d-b6e5-cdae0ceaed92",
  "title": "仓储计费规则.pdf",
  "filePath": "ragFiles/a338cd4a-43af-487d-b6e5-cdae0ceaed92_仓储计费规则.pdf",
  "mimeType": "application/pdf",
  "totalTokens": 1500,
  "chunkCount": 8,
  "createdAt": "2024-12-29T10:30:00",
  "downloadUrl": "/api/ai/knowledge/download/a338cd4a-43af-487d-b6e5-cdae0ceaed92",
  "previewUrl": "/api/ai/knowledge/preview/a338cd4a-43af-487d-b6e5-cdae0ceaed92",
  "fileExists": true
}
```

## 前端集成示例

### 1. 引用点击处理

```javascript
function showCitation(citationNumber) {
    // 高亮显示对应的引用来源
    const references = document.querySelectorAll('.reference-item');
    references.forEach((ref, index) => {
        if (index + 1 === citationNumber) {
            ref.style.backgroundColor = '#e3f2fd';
            ref.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    });
}
```

### 2. 文件下载处理

```javascript
function downloadFile(documentId) {
    window.open(`/api/ai/knowledge/download/${documentId}`, '_blank');
}

function previewFile(documentId) {
    window.open(`/api/ai/knowledge/preview/${documentId}`, '_blank');
}
```

### 3. 获取引用详情

```javascript
async function getCitationInfo(documentId) {
    try {
        const response = await fetch(`/api/ai/knowledge/citation/${documentId}`);
        const citation = await response.json();
        return citation;
    } catch (error) {
        console.error('获取引用信息失败:', error);
        return null;
    }
}
```

## 效果展示

### AI 回答示例

```
根据相关政策，仓储收入的计算主要包括以下几个方面：

1. 标准租金：在库件数 * 商品体积 * 单价[1]。
   SKU单体需要计算得出，并且不需要区分重/泡，全部按体积计算[1]。

2. 贴标费：单价 * 贴标数量[2]。
   需要维护贴标标识和应收单价[2]。

**参考来源：**
[1] 文件名：仓储计费规则.pdf，来源：第2页/第3段
[2] 文件名：贴标费用标准.docx，来源：第1页/第2段
```

### 前端引用组件

- **引用标记**：`[1]` `[2]` 可点击高亮对应来源
- **来源列表**：显示文件名、位置信息
- **操作按钮**：下载、预览、查看详情
- **模态框**：显示文档详细信息

## 安全考虑

### 文件访问控制

1. **路径验证**：防止路径遍历攻击
2. **文件存在检查**：避免暴露系统信息
3. **MIME 类型验证**：防止恶意文件执行
4. **文件名编码**：正确处理中文文件名

### 权限控制建议

```java
// 建议添加用户权限检查
@PreAuthorize("hasPermission(#documentId, 'DOCUMENT', 'READ')")
@GetMapping("/download/{documentId}")
public ResponseEntity<Resource> downloadDocument(@PathVariable String documentId) {
    // 实现逻辑...
}
```

## 部署配置

### 文件存储配置

```yaml
# application.yml
file:
  upload-dir: D:/uploads/ragFiles  # Windows 示例路径
  # upload-dir: /var/ragFiles      # Linux 示例路径
  max-file-size: 50MB
  allowed-types: 
    - application/pdf
    - application/msword
    - application/vnd.openxmlformats-officedocument.wordprocessingml.document
    - text/plain
```

### Nginx 配置（生产环境）

```nginx
# 直接由 Nginx 提供文件服务，提高性能
location /files/ {
    alias /var/ragFiles/;
    expires 1d;
    add_header Cache-Control "public, immutable";
}

# API 请求转发到 Spring Boot
location /api/ {
    proxy_pass http://localhost:8888;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

## 测试验证

### 功能测试清单

- [ ] 文档上传并生成引用编号
- [ ] RAG 查询返回带引用的回答
- [ ] 点击引用编号高亮对应来源
- [ ] 文件下载功能正常
- [ ] 文件预览功能正常（PDF、图片等）
- [ ] 引用信息 API 返回完整元数据
- [ ] 中文文件名正确处理
- [ ] 不存在文件返回 404
- [ ] 权限控制生效

### 性能测试

- 文件下载速度
- 大文件预览响应时间
- 并发访问处理能力

## 总结

通过本解决方案，RAG 系统现在具备了完整的引用来源和文件溯源能力：

### ✅ 已实现功能

1. **自动引用编号**：每个检索文档自动分配编号
2. **智能引用格式**：AI 回答中自动插入引用标记
3. **文件下载接口**：支持原始文件下载
4. **文件预览接口**：支持浏览器内预览
5. **引用信息查询**：获取文档详细元数据
6. **前端引用组件**：完整的用户交互体验
7. **安全文件访问**：路径验证和权限控制

### 🚀 用户体验提升

- **透明度**：用户清楚知道答案来源
- **可验证性**：可以查看和下载原始文档
- **交互性**：点击引用编号快速定位来源
- **便利性**：一键下载或预览相关文件

### 📈 系统价值

- **可信度提升**：明确的引用来源增强用户信任
- **合规性**：满足企业对信息溯源的要求
- **用户满意度**：完整的文档访问体验
- **系统完整性**：从检索到访问的闭环体验

这个解决方案将 RAG 系统从"黑盒"变成了"透明盒"，用户不仅能得到准确的答案，还能追溯到答案的具体来源，大大提升了系统的实用性和可信度。