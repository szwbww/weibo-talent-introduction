package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** State transitions may hold an older contact snapshot. Repository.save must not undo a primary-email change.
 * Spring Data JDBC invokes this inside save's transaction; the row lock serializes with the explicit setter.
 * New contacts keep their supplied email. Existing contacts change email only via ExpertPrimaryEmailService.
 */
@Component
class ExpertContactPrimaryEmailCallback(private val jdbc: JdbcTemplate) : BeforeConvertCallback<ExpertContact> {
    override fun onBeforeConvert(contact: ExpertContact): ExpertContact {
        val id = contact.id ?: return contact
        val email = jdbc.query("SELECT expert_email FROM expert_contact WHERE id=? FOR UPDATE",
            { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return contact
        return if (email == contact.expertEmail) contact else contact.copy(expertEmail = email)
    }
}
