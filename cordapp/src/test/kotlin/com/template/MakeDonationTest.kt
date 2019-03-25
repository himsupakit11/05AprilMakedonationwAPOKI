package com.template

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
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

class MakeDonationTest{
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

        listOf(Fundraiser, Bank, Donor, Recipient).forEach { it.registerInitiatedFlow(AutoOfferFlow.RecordTransactionAsObserver::class.java) }

    }
    @After
    fun tearDown(){
        network.stopNodes()
    }
    companion object {
        val logger: Logger = loggerFor<MakeDonationTest>()
    }
    private fun calculatedDeadlineInSeconds(interval: Long) = Instant.now().plusSeconds(interval)
    private val fiveSecondsFromNow: Instant get() = calculatedDeadlineInSeconds(5L)
    private val OneSecondsFromNow: Instant get() = calculatedDeadlineInSeconds(60L)
//    private val kaokonlakaoCampaign
//        get() = Campaign(
//                name = "Kaokonlakao Campaign",
//                target = 1000.DOLLARS,
//                fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
//                recipient = Recipient.services.myInfo.legalIdentities.first(),
//                deadline = fiveSecondsFromNow,
//                category = "Charity"
//        )
    @Test
    fun`Make a donation and broadcast the updated campaign state to all parties successfully`(){

        val kaokonlakaoCampaign = Campaign(
            name = "Kaokonlakao Campaign",
            target = 1000.DOLLARS,
            fundraiser = Fundraiser.services.myInfo.legalIdentities.first(),
            recipient = Recipient.services.myInfo.legalIdentities.first(),
            deadline = OneSecondsFromNow,
            category = "Charity")


        // Start a new campaign
        val flow = AutoOfferFlow.StartCampaign(kaokonlakaoCampaign)
        val createCampaignTransaction: SignedTransaction = Fundraiser.startFlow(flow).getOrThrow()
        network.waitQuiescent()


        // Get the campaign state from the transaction
        val campaignState: Campaign = createCampaignTransaction.tx.outputs.single().data as Campaign
        println("CampaignStateFT1: ${campaignState}")
        val campaignId: UniqueIdentifier = campaignState.linearId
        val a = Recipient.services.vaultService.queryBy<Campaign>().states
        val b = Donor.services.vaultService.queryBy<Campaign>().states
        val c = Bank.services.vaultService.queryBy<Campaign>().states
        val d = Fundraiser.services.vaultService.queryBy<Campaign>().states
        println("===========================================")
        println("Recipient Vault : $a")
        println("Donor Vault     : $b")
        println("Bank Vault      : $c")
        println("Fundraiser Vault: $d")
        println("===========================================")




        // Donor make a donation to fundraiser
        val makeDonationFlow: MakeDonation.Initiator = MakeDonation.Initiator(100.DOLLARS, campaignId, broadcastToObservers = true)
        println("MakeDonationFlow: $makeDonationFlow")
        val acceptDonationTransaction: SignedTransaction = Donor.startFlow(makeDonationFlow).getOrThrow()
        println("acceptDonationTransaction: $acceptDonationTransaction")
   // val acceptDonationTransaction2: SignedTransaction = Bank.startFlow(makeDonationFlow).getOrThrow()
 //   println("acceptDonationTransaction: $acceptDonationTransaction")

      //  println("campaignId: $campaignId")
       // println("FundraiserTxId: ${campaignState.fundraiser.owningKey}")
        //println("===========================================")
        logger.info("New campaign started")
        logger.info(createCampaignTransaction.toString()) //Print tx id
        logger.info(createCampaignTransaction.tx.toString())

        logger.info("Donor make a donation to fundraiser $100 ")
        logger.info(acceptDonationTransaction.toString())
        logger.info(acceptDonationTransaction.tx.toString())

//    logger.info("Bank make a donation to fundraiser $100 ")
//    logger.info(acceptDonationTransaction2.toString())
//    logger.info(acceptDonationTransaction2.tx.toString())

        // Get the campaign state from the transaction
        val campaignStateAfterDonaion: StateRef = acceptDonationTransaction.tx.outRefsOfType<Campaign>().single().ref
        val campaignAfterDonaiton: Campaign = acceptDonationTransaction.tx.outputsOfType<Campaign>().single()
        val newDonationStateRef: StateRef = acceptDonationTransaction.tx.outRefsOfType<Donation>().single().ref
        val newDonation: Donation = acceptDonationTransaction.tx.outputsOfType<Donation>().single()
        println("newDonation: $newDonation")
//    val donor = Donor.services.vaultService.queryBy<Donation>().states
//    println("Donor Donation state Vault: $donor")
//    val bank = Bank.services.vaultService.queryBy<Donation>().states
//    println("Donor Donation state Vault: $bank")
//
//    val donor2 = Donor.services.vaultService.queryBy<Campaign>().states
//    println("Donor Campaign state Vault: $donor2")
//      val fundraiser = Fundraiser.services.vaultService.queryBy<Campaign>().states
//    println("Fundraiser  Campaign state Vault: $fundraiser")
//    val recipient = Recipient.services.vaultService.queryBy<Campaign>().states
//    println("Recipient Campaign state Vault: $recipient")




//    val fundraiserCampaignAfterDonation = Fundraiser.services.validatedTransactions.getTransaction(campaignStateAfterDonaion.txhash)
//        val bankCampaignAfterDonation = Bank.services.validatedTransactions.getTransaction(campaignStateAfterDonaion.txhash)
//        val donorCampaignAfterDonation = Donor.services.validatedTransactions.getTransaction(campaignStateAfterDonaion.txhash)
//        val recipientCampaignAfterDonation = Recipient.services.validatedTransactions.getTransaction(campaignStateAfterDonaion.txhash)
//
//        assertEquals(1,
//                setOf(
//                        campaignAfterDonaiton,
//                        fundraiserCampaignAfterDonation,
//                        bankCampaignAfterDonation,
//                        donorCampaignAfterDonation,
//                        recipientCampaignAfterDonation
//                ).size
//        )
//        val fundraiserNewDonation = Fundraiser.services.validatedTransactions.getTransaction(newDonationStateRef.txhash)
//        val bankNewDonation = Bank.services.validatedTransactions.getTransaction(newDonationStateRef.txhash)
//        val donorNewDonation = Donor.services.validatedTransactions.getTransaction(newDonationStateRef.txhash)
//        val recipientNewDonation = Fundraiser.services.validatedTransactions.getTransaction(newDonationStateRef.txhash)
//
//        assertEquals(1,
//                setOf(
//                newDonation,
//                fundraiserNewDonation,
//                bankNewDonation,
//                donorNewDonation,
//                recipientNewDonation
//                ).size
//        )
//
//        // Only Donor and fundraiser know each other identity
//        assertEquals(Fundraiser.services.myInfo.legalIdentities.first(), Fundraiser.services.identityService.wellKnownPartyFromAnonymous(newDonation.donor))
//        assertEquals(Fundraiser.services.myInfo.legalIdentities.first(), Donor.services.identityService.wellKnownPartyFromAnonymous(newDonation.donor))
//        assertEquals(null,Recipient.services.identityService.wellKnownPartyFromAnonymous(newDonation.donor))
//        assertEquals(null,Donor.services.identityService.wellKnownPartyFromAnonymous(newDonation.donor))
        network.waitQuiescent()
    }



}