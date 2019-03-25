package com.template

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import java.time.Instant
import kotlin.test.assertEquals


class CampaignTest{
    lateinit var network: MockNetwork
    lateinit var Bank: StartedMockNode
    lateinit var Donor: StartedMockNode
    lateinit var Fundraiser: StartedMockNode
    lateinit var Recipient: StartedMockNode

    @Before
    fun setup(){
        network = MockNetwork(cordappPackages =  listOf("com.template"), threadPerNode =  true)
        Fundraiser = network.createPartyNode(CordaX500Name("PToon","BKK","TH"))
        Bank = network.createPartyNode(CordaX500Name("Bank","Bangkok","TH"))
        Donor = network.createPartyNode(CordaX500Name("Alice","BKK","TH"))
        Recipient = network.createPartyNode(CordaX500Name("Hospital","PCKK", "TH"))

        listOf(Bank, Donor).forEach { it.registerInitiatedFlow(AutoOfferFlow.RecordTransactionAsObserver::class.java) }

    }
    @After
    fun tearDown(){
        network.stopNodes()
    }
    companion object {
        val logger: Logger = loggerFor<CampaignTest>()
    }
    private fun calculatedDeadlineInSeconds(interval: Long) = Instant.now().plusSeconds(interval)
    private val fiveSecondsFromNow: Instant get() = calculatedDeadlineInSeconds(5L)
    private val kaokonlakaoCampaign
        get() = Campaign(
                name = "Kaokonlakao Campaign",
                target = 1000.DOLLARS,
                fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
                recipient = Recipient.services.myInfo.legalIdentities.first(),
                deadline = fiveSecondsFromNow,
                category = "Charity"
        )
    private val kaokonlakao2Campaign
        get() = Campaign(
                name = "Kaokonlakao Campaign",
                target = 1000.DOLLARS,
                fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
                recipient = Recipient.services.myInfo.legalIdentities.first(),
                deadline = fiveSecondsFromNow,
                category = "Charity"
        )

    @Test
    fun `Start and broadcast campaign to all parties on the network successfully`() {

        println("Recipient ${Recipient.services.myInfo.legalIdentities}")

        // Start a new campaign
        val flow = AutoOfferFlow.StartCampaign(kaokonlakaoCampaign)
        val campaign = Fundraiser.startFlow(flow).getOrThrow()//getOrThrow = time duration
        network.waitQuiescent()
        //Extract the state from the transaction
        val campaignStateRef = campaign.tx.outRef<Campaign>(0).ref // campaign index
        val campaignState = campaign.tx.outputs.single() // Pick only one transactionState
        println("The created campaignState  : $campaignState")

        val a = Recipient.services.vaultService.queryBy<Campaign>().states
        val b = Donor.services.vaultService.queryBy<Campaign>().states
        val c = Bank.services.vaultService.queryBy<Campaign>().states
        val d = Fundraiser.services.vaultService.queryBy<Campaign>().states
        println("===========================================")
        println("After the campaign state has committed on the fundraiser vault ")
        println("Recipient Vault : $a")
        println("Donor Vault     : $b")
        println("Bank Vault      : $c")
        println("Fundraiser Vault: $d")
        println("===========================================")
        //Get the Campaign state of all party nodes on the network from its node

        val fundraiserCampaign = Fundraiser.services.validatedTransactions.getTransaction(campaignStateRef.txhash)
        val bankCampaign = Bank.services.validatedTransactions.getTransaction(campaignStateRef.txhash)
        val donorCampaign = Donor.services.validatedTransactions.getTransaction(campaignStateRef.txhash)
        val recipientCampaign = Recipient.services.validatedTransactions.getTransaction(campaignStateRef.txhash)

        // To check that all parties have the same campaignState

        assertEquals(1, setOf(fundraiserCampaign, bankCampaign, donorCampaign, recipientCampaign).size)
        println("===========================================")
      println("lollllllll")
        logger.info("Fundraiser: $campaignState")
        logger.info("Bank: $bankCampaign")
        logger.info("Donor: $donorCampaign")
        logger.info("Recipient: $recipientCampaign")
        println("===========================================")


    }

}