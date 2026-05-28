package com.weibo.talentintroduction.qa.repository

import com.weibo.talentintroduction.qa.domain.QaCategory
import org.springframework.data.repository.CrudRepository

interface QaCategoryRepository : CrudRepository<QaCategory, Long> {
    fun findAllByOrderByCategoryCodeAsc(): List<QaCategory>

    fun findByCategoryCode(categoryCode: String): QaCategory?

    fun existsByCategoryCode(categoryCode: String): Boolean
}
