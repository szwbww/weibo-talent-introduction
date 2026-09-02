package com.weibo.talentintroduction.rag.repository

import com.weibo.talentintroduction.rag.domain.RagFact
import org.springframework.data.repository.CrudRepository

/**
 * 计划 01 (T3): `rag_fact` 的数据访问。只暴露两个查询：
 * - [findAllByOrderBySortOrderAscIdAsc] —— 启动期整表读入（45 行，表小），
 *   快照加载唯一入口（I-6）；
 * - [findByFactCode] —— 按业务键（G-1）取单条，供对账/测试用。
 *
 * 刻意不暴露其他查询：`rag_fact` 没有逐条查库的运行期读路径（I-6）。
 */
interface RagFactRepository : CrudRepository<RagFact, Long> {

    fun findAllByOrderBySortOrderAscIdAsc(): List<RagFact>

    fun findByFactCode(factCode: String): RagFact?
}
