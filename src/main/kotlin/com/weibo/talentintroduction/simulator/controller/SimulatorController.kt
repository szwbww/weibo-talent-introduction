package com.weibo.talentintroduction.simulator.controller

import com.weibo.talentintroduction.simulator.dto.InboundRequest
import com.weibo.talentintroduction.simulator.dto.ResetContactRequest
import com.weibo.talentintroduction.simulator.dto.SeedContactRequest
import com.weibo.talentintroduction.simulator.service.SimulatorService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/simulator")
@Profile("simulator")
@ConditionalOnProperty("talent-introduction.simulator.enabled", havingValue = "true")
class SimulatorController(private val simulatorService: SimulatorService) {

    @PostMapping("/contacts")
    fun seedContact(@RequestBody req: SeedContactRequest) = simulatorService.seedContact(req)

    @PostMapping("/contacts/{id}/reset")
    fun resetContact(@PathVariable id: Long, @RequestBody req: ResetContactRequest) =
        simulatorService.resetContact(id, req)

    @GetMapping("/contacts")
    fun listContacts() = simulatorService.listContacts()

    @GetMapping("/contacts/{id}/snapshot")
    fun snapshot(@PathVariable id: Long) = simulatorService.snapshot(id)

    @PostMapping("/contacts/{id}/inbound")
    fun inbound(@PathVariable id: Long, @RequestBody req: InboundRequest) =
        simulatorService.simulateInbound(id, req)

    @GetMapping("/presets")
    fun presets() = simulatorService.listPresets()

    @GetMapping("/outbound-buffer")
    fun outboundBuffer() = simulatorService.outboundBuffer()

    @GetMapping("/scenarios")
    fun scenarios() = simulatorService.listScenarios()

    @PostMapping("/scenarios/{key}/run")
    fun runScenario(@PathVariable key: String) = simulatorService.runScenario(key)
}
