package com.template.contractAndstate

import co.paralleluniverse.fibers.Suspendable
import com.template.*
import com.template.CampaignContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash


// *********
// * Flows *
// *********

object EndCampaign{

    @SchedulableFlow
    @InitiatingFlow

    class Initator(private val stateRef: StateRef): FlowLogic<SignedTransaction>(){

        @Suspendable
        // Sent a request to donors and retrieve the transaction
        fun requestDonatedCash(sessions: List<FlowSession>): CashStatesPayload{
            //Generate anonymous identity for each payer
            val cashStates = sessions.map { session ->
                // Send Campaign Success Message.
                session.send(CampaignResult.Success(stateRef))
                // Resolve the transactions.
                subFlow(ReceiveStateAndRefFlow<ContractState>(session))
                // Receive the cash inputs, outputs and public keys.
                session.receive<CashStatesPayload>().unwrap { it } }
            //return the transaction data
            return CashStatesPayload(
                    cashStates.flatMap { it.inputs },
                    cashStates.flatMap { it.outputs },
                    cashStates.flatMap { it.signingKeys }

            )
        }

        private fun cancelDonation(campaign: Campaign): TransactionBuilder{
            val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
            val utx = TransactionBuilder(notary = notary)

            val donorStateAndRefs =  donationsForCampaign(serviceHub,campaign)
            val campaignInputStateAndRedf = serviceHub.toStateAndRef<Campaign>(stateRef)

            val endCampaignCommand = Command(CampaignContract.Commands.End(),campaign.fundraiser.owningKey)
            // cancel command
            donorStateAndRefs.forEach { utx.addInputState(it) }
            utx.addInputState(campaignInputStateAndRedf)
            utx.addCommand(endCampaignCommand)

            return utx
        }

        @Suspendable
        fun handleSuccess(campaign: Campaign, sessions: List<FlowSession>):TransactionBuilder{
            val utx = cancelDonation(campaign)
            val cashStates = requestDonatedCash(sessions)

            cashStates.inputs.forEach { utx.addInputState(it) }
           // cashStates.outputs.forEach { utx.addOutputState(it,CASH_PROGRAM_ID) }
            utx.addCommand(Cash.Commands.Move(),cashStates.signingKeys)

            return utx
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val campaign = serviceHub.loadState(stateRef).data as Campaign
            if (campaign.fundraiser != ourIdentity){
                throw  FlowException("Only fundraiser can run this flow.")
            }
            val donationForCampaign = donationsForCampaign(serviceHub,campaign)
            val sessions = donationForCampaign.map { (state) ->
                val pledger = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.data.fundraiser)
                initiateFlow(pledger)
            }
            val utx = when {
                campaign.raised < campaign.target -> {
                    sessions.forEach { session -> session.send(CampaignResult.Failure()) }
                    cancelDonation(campaign)
                }
                else -> handleSuccess(campaign,sessions)
            }
            val ptx = serviceHub.signInitialTransaction(utx)
            val stx = subFlow(CollectSignaturesFlow(ptx,sessions))
            val ftx = subFlow(FinalityFlow(stx))
            subFlow((AutoOfferFlow.BroadcastTransaction(ftx)))

            return ftx
        }

    }
}
/**Pick all of donations for the specified campaigns*/
fun donationsForCampaign(services: ServiceHub,campaign: Campaign): List<StateAndRef<Donation>>{
    val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
    return builder {
        val campaignReference = Donation.DonationSchema.DonationEntity::campaign_reference.equal(campaign.linearId.id.toString())
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(campaignReference)
        val criteria = generalCriteria `and` customCriteria
        services.vaultService.queryBy<Donation>(criteria)

    }.states

}
