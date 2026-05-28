package com.weibo.talentintroduction.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "talent-introduction.mail-queue", name = ["enabled"], havingValue = "true")
class RabbitMailQueueConfig(
    private val properties: MailQueueProperties
) {
    @Bean
    fun mailExchange(): DirectExchange =
        DirectExchange(properties.exchange, true, false)

    @Bean
    fun mailDeadLetterExchange(): DirectExchange =
        DirectExchange(properties.deadLetterExchange, true, false)

    @Bean
    fun initialOutreachQueue(): Queue =
        durableQueue(properties.initialOutreachQueue, properties.initialOutreachDeadLetterRoutingKey)

    @Bean
    fun autoReplyAccountQueue(): Queue =
        durableQueue(properties.autoReplyAccountQueue, properties.autoReplyAccountDeadLetterRoutingKey)

    @Bean
    fun autoReplyAllAccountsQueue(): Queue =
        durableQueue(properties.autoReplyAllAccountsQueue, properties.autoReplyAllAccountsDeadLetterRoutingKey)

    @Bean
    fun initialOutreachDeadLetterQueue(): Queue =
        Queue(properties.initialOutreachDeadLetterQueue, true)

    @Bean
    fun autoReplyAccountDeadLetterQueue(): Queue =
        Queue(properties.autoReplyAccountDeadLetterQueue, true)

    @Bean
    fun autoReplyAllAccountsDeadLetterQueue(): Queue =
        Queue(properties.autoReplyAllAccountsDeadLetterQueue, true)

    @Bean
    fun initialOutreachBinding(mailExchange: DirectExchange, initialOutreachQueue: Queue): Binding =
        BindingBuilder.bind(initialOutreachQueue)
            .to(mailExchange)
            .with(properties.initialOutreachRoutingKey)

    @Bean
    fun autoReplyAccountBinding(mailExchange: DirectExchange, autoReplyAccountQueue: Queue): Binding =
        BindingBuilder.bind(autoReplyAccountQueue)
            .to(mailExchange)
            .with(properties.autoReplyAccountRoutingKey)

    @Bean
    fun autoReplyAllAccountsBinding(mailExchange: DirectExchange, autoReplyAllAccountsQueue: Queue): Binding =
        BindingBuilder.bind(autoReplyAllAccountsQueue)
            .to(mailExchange)
            .with(properties.autoReplyAllAccountsRoutingKey)

    @Bean
    fun initialOutreachDeadLetterBinding(
        mailDeadLetterExchange: DirectExchange,
        initialOutreachDeadLetterQueue: Queue
    ): Binding =
        BindingBuilder.bind(initialOutreachDeadLetterQueue)
            .to(mailDeadLetterExchange)
            .with(properties.initialOutreachDeadLetterRoutingKey)

    @Bean
    fun autoReplyAccountDeadLetterBinding(
        mailDeadLetterExchange: DirectExchange,
        autoReplyAccountDeadLetterQueue: Queue
    ): Binding =
        BindingBuilder.bind(autoReplyAccountDeadLetterQueue)
            .to(mailDeadLetterExchange)
            .with(properties.autoReplyAccountDeadLetterRoutingKey)

    @Bean
    fun autoReplyAllAccountsDeadLetterBinding(
        mailDeadLetterExchange: DirectExchange,
        autoReplyAllAccountsDeadLetterQueue: Queue
    ): Binding =
        BindingBuilder.bind(autoReplyAllAccountsDeadLetterQueue)
            .to(mailDeadLetterExchange)
            .with(properties.autoReplyAllAccountsDeadLetterRoutingKey)

    @Bean
    fun jackson2JsonMessageConverter(): Jackson2JsonMessageConverter =
        Jackson2JsonMessageConverter()

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        jackson2JsonMessageConverter: Jackson2JsonMessageConverter
    ): RabbitTemplate =
        RabbitTemplate(connectionFactory).apply {
            messageConverter = jackson2JsonMessageConverter
        }

    private fun durableQueue(queueName: String, deadLetterRoutingKey: String): Queue =
        Queue(
            queueName,
            true,
            false,
            false,
            mapOf(
                "x-dead-letter-exchange" to properties.deadLetterExchange,
                "x-dead-letter-routing-key" to deadLetterRoutingKey
            )
        )
}
