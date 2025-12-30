// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.mapper;

// 导入 MyBatis-Plus 相关类
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
// 导入 MyBatis 注解
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
// 导入项目实体类
import org.zerolg.aidemo2.entity.Document;
import org.zerolg.aidemo2.entity.DocumentChunk;

// 导入 Java 标准库
import java.util.List;

/**
 * 文档数据访问层接口
 * <p>
 * 这是一个 MyBatis-Plus 的 Mapper 接口，用于操作 documents 表
 * <p>
 * MyBatis-Plus 框架说明：
 * MyBatis-Plus 是 MyBatis 的增强工具，在 MyBatis 的基础上只做增强不做改变，
 * 为简化开发、提高效率而生
 * <p>
 * 核心特性：
 * 1. 无侵入：只做增强不做改变，引入它不会对现有工程产生影响
 * 2. 损耗小：启动即会自动注入基本 CURD，性能基本无损耗，直接面向对象操作
 * 3. 强大的 CRUD 操作：内置通用 Mapper、通用 Service，仅仅通过少量配置即可实现单表大部分 CRUD 操作
 * 4. 支持 Lambda 形式调用：通过 Lambda 表达式，方便的编写各类查询条件，无需再担心字段写错
 * 5. 支持主键自动生成：支持多达 4 种主键策略（内含分布式唯一 ID 生成器 - Sequence）
 * 6. 支持 ActiveRecord 模式：支持 ActiveRecord 形式调用，实体类只需继承 Model 类即可进行强大的 CRUD 操作
 * 7. 支持自定义全局通用操作：支持全局通用方法注入（ Write once, use anywhere ）
 * 8. 内置代码生成器：采用代码或者 Maven 插件可快速生成 Mapper 、 Model 、 Service 、 Controller 层代码
 * 9. 内置分页插件：基于 MyBatis 物理分页，开发者无需关心具体操作，配置好插件之后，写分页等同于普通 List 查询
 * 10. 分页插件支持多种数据库：支持 MySQL、MariaDB、Oracle、DB2、H2、HSQL、SQLite、Postgre、SQLServer 等多种数据库
 * 11. 内置性能分析插件：可输出 Sql 语句以及其执行时间，建议开发测试时启用该功能，能快速揪出慢查询
 * 12. 内置全局拦截插件：提供全表 delete 、 update 操作智能分析阻断，也可自定义拦截规则，预防误操作
 * <p>
 * BaseMapper<T> 提供的基础方法：
 * <p>
 * 插入操作：
 * - insert(T entity): 插入一条记录
 * - insertBatchSomeColumn(Collection<T> entityList): 批量插入
 * <p>
 * 删除操作：
 * - deleteById(Serializable id): 根据 ID 删除
 * - deleteByMap(Map<String, Object> columnMap): 根据 columnMap 条件删除记录
 * - delete(Wrapper<T> queryWrapper): 根据 entity 条件删除记录
 * - deleteBatchIds(Collection<? extends Serializable> idList): 删除（根据ID批量删除）
 * <p>
 * 修改操作：
 * - updateById(T entity): 根据 ID 修改
 * - update(T entity, Wrapper<T> updateWrapper): 根据 whereEntity 条件，更新记录
 * <p>
 * 查询操作：
 * - selectById(Serializable id): 根据 ID 查询
 * - selectBatchIds(Collection<? extends Serializable> idList): 查询（根据ID批量查询）
 * - selectByMap(Map<String, Object> columnMap): 查询（根据 columnMap 条件）
 * - selectOne(Wrapper<T> queryWrapper): 根据 entity 条件，查询一条记录
 * - selectCount(Wrapper<T> queryWrapper): 根据 Wrapper 条件，查询总记录数
 * - selectList(Wrapper<T> queryWrapper): 根据 entity 条件，查询全部记录
 * - selectMaps(Wrapper<T> queryWrapper): 根据 Wrapper 条件，查询全部记录
 * - selectMapsPage(IPage<T> page, Wrapper<T> queryWrapper): 根据 Wrapper 条件，查询全部记录（并翻页）
 * - selectPage(IPage<T> page, Wrapper<T> queryWrapper): 根据 entity 条件，查询全部记录（并翻页）
 * <p>
 * 使用示例：
 * <p>
 * // 插入文档
 * Document doc = new Document();
 * doc.setTitle("测试文档");
 * doc.setContent("文档内容");
 * documentMapper.insert(doc);
 * <p>
 * // 根据ID查询
 * Document doc = documentMapper.selectById(1L);
 * <p>
 * // 条件查询
 * QueryWrapper<Document> wrapper = new QueryWrapper<>();
 * wrapper.eq("title", "测试文档");
 * List<Document> docs = documentMapper.selectList(wrapper);
 * <p>
 * // 分页查询
 * Page<Document> page = new Page<>(1, 10);
 * IPage<Document> result = documentMapper.selectPage(page, wrapper);
 * <p>
 * // 更新
 * Document doc = new Document();
 * doc.setId(1L);
 * doc.setTitle("更新后的标题");
 * documentMapper.updateById(doc);
 * <p>
 * // 删除
 * documentMapper.deleteById(1L);
 * <p>
 * 为什么继承 BaseMapper：
 * 1. 减少重复代码：不需要写基础的 CRUD 方法
 * 2. 统一接口：所有 Mapper 都有相同的基础方法
 * 3. 类型安全：泛型确保类型安全
 * 4. 自动生成 SQL：MyBatis-Plus 会根据实体类自动生成 SQL
 * <p>
 * 注解说明：
 *
 * @Mapper: 标记这是一个 MyBatis 的 Mapper 接口
 * - 告诉 Spring 这是一个数据访问层组件
 * - MyBatis 会为这个接口创建代理实现
 * - 支持依赖注入到其他组件中
 */
@Mapper // MyBatis 注解：标记这是一个 Mapper 接口，用于数据库操作
public interface DocumentMapper extends BaseMapper<Document> {

    // 继承 BaseMapper<Document> 后，自动获得以下方法：
    // - insert(Document entity): 插入文档
    // - deleteById(Long id): 根据ID删除文档
    // - updateById(Document entity): 根据ID更新文档
    // - selectById(Long id): 根据ID查询文档
    // - selectList(Wrapper<Document> queryWrapper): 条件查询文档列表
    // - selectPage(IPage<Document> page, Wrapper<Document> queryWrapper): 分页查询
    // 等等...

    // 如果需要自定义查询方法，可以在这里添加
    // 例如：

    /**
     * 根据标题模糊查询文档
     *
     * 这是一个自定义查询方法的示例，展示如何在 Mapper 中添加自定义 SQL
     *
     * @param title 文档标题关键词
     * @return 匹配的文档列表
     */
    // @Select("SELECT * FROM documents WHERE title LIKE CONCAT('%', #{title}, '%')")
    // List<Document> findByTitleLike(@Param("title") String title);

    /**
     * 查询指定用户的文档数量
     *
     * @param userId 用户ID
     * @return 文档数量
     */
    // @Select("SELECT COUNT(*) FROM documents WHERE user_id = #{userId}")
    // int countByUserId(@Param("userId") Long userId);

    /**
     * 查询最近上传的文档
     *
     * @param limit 限制数量
     * @return 最近上传的文档列表
     */
    // @Select("SELECT * FROM documents ORDER BY created_at DESC LIMIT #{limit}")
    // List<Document> findRecentDocuments(@Param("limit") int limit);

    // 注意：上面的方法都被注释了，因为当前项目可能不需要这些自定义查询
    // 如果需要，可以取消注释并根据实际需求修改

    // 更复杂的查询建议使用 XML 映射文件，而不是注解
    // XML 文件位置：src/main/resources/mapper/DocumentMapper.xml
}
