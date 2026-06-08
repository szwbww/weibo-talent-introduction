package com.weibo.talentintroduction.expert.service

fun interface MxLookupClient {
    fun lookup(domain: String): MxLookupResult
}
