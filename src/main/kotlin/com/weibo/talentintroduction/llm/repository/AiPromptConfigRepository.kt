package com.weibo.talentintroduction.llm.repository

import com.weibo.talentintroduction.llm.domain.AiPromptConfig
import org.springframework.data.repository.CrudRepository

interface AiPromptConfigRepository : CrudRepository<AiPromptConfig, Long>
