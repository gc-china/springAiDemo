package org.zerolg.aidemo2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 已处理文档记录表实体
 * 用于文件级去重 (MD5 校验)
 * 对应表名: document_file
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_file")
public class DocumentFile {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String fileHash; // MD5 值

    private String filename;

    private String status; // COMPLETED, FAILED

    private LocalDateTime createTime;
}