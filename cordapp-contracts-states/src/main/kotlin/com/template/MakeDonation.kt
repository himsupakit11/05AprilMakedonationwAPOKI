package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import org.hibernate.Session
import java.security.PublicKey
import java.time.Instant
import java.util.*

class DonationContract: Contract {
    companion object {
        @JvmStatic
        val ID = "com.template.DonationContract"
    }

    interface  Commands : CommandData
    class Create: TypeOnlyCommandData(), Commands

    override fun verify(tx: LedgerTransaction) {
        val donationCommand: CommandWithParties<Commands> = tx.commands.requireSingleCommand()
        val setOfSigners: Set<PublicKey> = donationCommand.signers.toSet()

        when(donationCommand.value) {
            is Create -> verifyCreate(tx,setOfSigners)
            else -> throw IllegalArgumentException("Command not found")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Group donation by campaign id
        val donationState: List<LedgerTransaction.InOutGroup<Donation,UniqueIdentifier>> = tx.groupStates(Donation::class.java,{it.linearId})
        "Only one donation can be made at a time" using (donationState.size == 1)
        val campaignStates: List<LedgerTransaction.InOutGroup<Campaign,UniqueIdentifier>> = tx.groupStates(Campaign::class.java,{it.linearId})
        "There must be a campaign state when making a donation" using (campaignStates.isNotEmpty())

        val donationStatesGroup: LedgerTransaction.InOutGroup<Donation,UniqueIdentifier> = donationState.single()
        "No input states should be consumed when making a donation" using (donationStatesGroup.outputs.size == 1)
        val donation: Donation = donationStatesGroup.outputs.single()

        "Donation amount cannot be zero amount" using (donation.amount > Amount(0,donation.amount.token))

        "The campaign must be signed by donor nad manager" using (signers == keysFromParticipants(donation))

    }
}



/** Donation flow: After donor received a Campaign from fundraiser,
  * donor can make a donation to a specific campaign */

object MakeDonation{
     /** Create a new donation state for updating the existing campaign state*/
     @StartableByRPC
     @InitiatingFlow
     class Initiator(
             private val amount: Amount<Currency>,
             private val campaignReference: UniqueIdentifier,
             private val broadcastToObservers: Boolean
     ) : FlowLogic<SignedTransaction>() {

         @Suspendable
         override fun call(): SignedTransaction {
             //Pick notary
             val notary = serviceHub.networkMapCache.notaryIdentities.first()

             //Query Campaign state from donor's vault
             println("campaignId $campaignReference")

             val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(campaignReference))
             println("queryCriteria: $queryCriteria")
             val campaignInputStateAndRef = serviceHub.vaultService.queryBy<Campaign>(queryCriteria).states.single()
             println("campaignInputStateAndRef ${campaignInputStateAndRef.state}")

             val campaignState = campaignInputStateAndRef.state.data
             println("MakeDonationF1: ${campaignState.fundraiser}")
             println("MakeDonationD1: ${campaignState.recipient}")
             println("makedoantion: $campaignState")



             // Generate anonymous key in order to other donors don't know who we are
             val myKey = serviceHub.keyManagementService.freshKeyAndCert(
                     ourIdentityAndCert,
                     revocationEnabled = false
             ).party.anonymise()

             // Assemble the transaction component
             val acceptDonationCommand: Command<CampaignContract.Commands.AcceptDonation> = Command(CampaignContract.Commands.AcceptDonation(),campaignState.fundraiser.owningKey)
             val createDonationCommand: Command<DonationContract.Create> = Command(DonationContract.Create(), listOf(myKey.owningKey,campaignState.fundraiser.owningKey))

             //Output states

             val donationOutputState = Donation(campaignReference,campaignState.fundraiser,myKey,amount)
             val donationOutputStateAndContract = StateAndContract(donationOutputState,DonationContract.ID)

             val newRaised = campaignState.raised + amount
             val campaignOutputState = campaignState.copy(raised = newRaised)
             val campaignOutputStateAndContract = StateAndContract(campaignOutputState,CampaignContract.ID)

            //Build transaction
             val utx = TransactionBuilder(notary = notary).withItems(
                    donationOutputStateAndContract, //Output state
                     campaignOutputStateAndContract,//Output state
                     campaignInputStateAndRef,      //Input
                     acceptDonationCommand,         //Command
                     createDonationCommand          //Command

             )
             utx.setTimeWindow(Instant.now(),30.seconds)

             //Sign, sync, finalise, and commit transaction
             val ptx = serviceHub.signInitialTransaction(builder = utx,signingPubKeys = listOf(myKey.owningKey))
             val session = initiateFlow(campaignState.fundraiser)
             subFlow(IdentitySyncFlow.Send(session,tx = ptx.tx))
             println("ptx: $ptx")
             println("session: $session")
             //Collect signature
             val stx = subFlow(CollectSignaturesFlow(ptx, setOf(session), setOf(myKey.owningKey)))
             val ftx = subFlow(FinalityFlow(stx))
             println("stx: $stx")
             println("ftx: $ftx")
             //Donor broadcast transaction to fundraiser
             session.sendAndReceive<Unit>(broadcastToObservers)

             return ftx

         }
     }
    /**
     * The responder run by fundraiser, to check the proposed transaction
     * to be committed and broadcasts to all parties on the netowrk
     * */
    @InitiatedBy(Initiator::class)
    class Responder(val othersession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            println("Responder1")
            subFlow(IdentitySyncFlow.Receive(otherSideSession = othersession))
            println("othersession: $othersession")
            val flow: SignTransactionFlow = object :SignTransactionFlow(othersession){
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val tx = stx.tx
                    println("tx $tx")
                }
            }
            val stx: SignedTransaction = subFlow(flow)
            println("Responder2")
            // transaction will commit and broadcast the committed transaction to fundraiser, if donor want to broadcast
            val broadcastToObservers: Boolean = othersession.receive<Boolean>().unwrap { it }
            if(broadcastToObservers){
                println("Responder3")
                //wait for transaction has been committed
                val ftx: SignedTransaction = waitForLedgerCommit(stx.id)
                println("Responder4")
                subFlow(AutoOfferFlow.BroadcastTransaction(ftx))
                println("Responder5")
            }
            println("Responder6")
            othersession.send(Unit)
        }
    }
 }