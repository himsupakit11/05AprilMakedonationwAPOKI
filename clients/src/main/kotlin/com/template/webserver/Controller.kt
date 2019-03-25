package com.template.webserver

import com.template.AutoOfferFlow
import com.template.Campaign
import net.corda.core.contracts.filterStatesOfType
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/api")
class Controller {
    companion object {
        private val logger = contextLogger()
    }

    private fun getCampaignLink(deal: Campaign) = "/api/campaigns/" + deal.linearId

    private fun getCampaignByRef(ref: String): Campaign? {
        val vault = rpc.vaultQueryBy<Campaign>().states
        val states = vault.filterStatesOfType<Campaign>().filter { it.state.data.linearId.externalId == ref }
        return if (states.isEmpty()) null else {
            val campaigns = states.map { it.state.data }
            return if (campaigns.isEmpty()) null else campaigns[0]
        }
    }

    @Autowired
    lateinit var rpc: CordaRPCOps

    private fun getAllCampagin(): Array<Campaign> {
        val vault = rpc.vaultQueryBy<Campaign>().states
        val states = vault.filterStatesOfType<Campaign>()
        return states.map { it.state.data }.toTypedArray()
    }

    @GetMapping("/campaigns")
    fun fetchCampaign(): Array<Campaign> = getAllCampagin()

    @PostMapping("/campaigns")
    fun storeCampaign(@RequestBody newCampaign: Campaign): ResponseEntity<Any?> {
        return try {
            rpc.startFlow(AutoOfferFlow::StartCampaign, newCampaign).returnValue.getOrThrow()
            ResponseEntity.created(URI.create(getCampaignLink(newCampaign))).build()
        } catch (ex: Exception) {
            logger.info("Exception when creating deal: $ex", ex)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.toString())
        }
    }

    @GetMapping("/campaigns/{ref:.+}")
    fun fetchCampagin(@PathVariable ref: String?): ResponseEntity<Any?> {
        val campaign = getCampaignByRef(ref!!)
        return if (campaign == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok(campaign)
        }

    }
    @GetMapping("/campaigns/networksnapshot")
    fun fetchDeal() = rpc.networkMapSnapshot().toString()
}







///**
// * Define your API endpoints here.
// */
//@RestController
//@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
//class Controller(rpc: NodeRPCConnection) {
//
//    companion object {
//        private val logger = LoggerFactory.getLogger(RestController::class.java)
//    }
//
//    private val proxy = rpc.proxy
//
//    @GetMapping(value = "/templateendpoint", produces = arrayOf("text/plain"))
//    private fun templateendpoint(): String {
//        return "Define an endpoint here."
//    }
//}