package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.flows.TwoPartyDealFlow

object AutoOfferFlow{
    @InitiatingFlow
    @StartableByRPC
    class StartCampaign(private val newCampaign: Campaign): FlowLogic<SignedTransaction>(){
        companion object {
            object RECEIVED : ProgressTracker.Step("Received API call")
            object DEALING : ProgressTracker.Step("Starting the deal flow") {
                override fun childProgressTracker(): ProgressTracker = TwoPartyDealFlow.Primary.tracker()
            }
            fun tracker() = ProgressTracker(RECEIVED, DEALING)
        }
        override val progressTracker = tracker()
        init {
            progressTracker.currentStep = RECEIVED
        }
        @Suspendable
        override fun call(): SignedTransaction{
            //Pick notary
            val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
            //Assemble the campaign components
            val startcommand = Command(CampaignContract.Commands.Start(), listOf(ourIdentity.owningKey))
            val outputState = StateAndContract(newCampaign,CampaignContract.ID)
            //Build, sign and record the campaign
            val utx = TransactionBuilder(notary = notary).withItems(outputState,startcommand)
            val stx = serviceHub.signInitialTransaction(utx)

            val ftx = subFlow(FinalityFlow(stx))
            //broadcast campaign to all parties on the network
            subFlow(BroadcastTransaction(ftx))
            return ftx
        }
    }

        @InitiatingFlow
        class BroadcastTransaction(val stx: SignedTransaction): FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                // Get a list of all identities from the network map cache.
                val everyone = serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }
                // Filter out the notary identities and remove our identity.
                println("everyone: $everyone")
                val ltx = stx.tx.toLedgerTransaction(serviceHub)
                val participants = (ltx.outputStates.flatMap { it.participants } + ltx.inputStates.flatMap { it.participants }).toSet()
                val allParticipants = groupAbstractPartyByWellKnownParty(serviceHub, participants).keys
                val everyoneButMeAndNotary = everyone.filter { serviceHub.networkMapCache.isNotary(it).not() }

                val everyoneButMeAndNotaryAndParticipants = everyoneButMeAndNotary - allParticipants
                println("everyoneButMeAndNotary: $everyoneButMeAndNotary")
                println("participants: $participants")
                println("everyoneButMeAndNotaryAndParticipants: $everyoneButMeAndNotaryAndParticipants")
                // Create a session for each remaining party.
                val sessions = everyoneButMeAndNotaryAndParticipants.map { initiateFlow(it) }
                println("session: $sessions")
                // Send the transaction to all the remaining parties.
                sessions.forEach { subFlow(SendTransactionFlow(it,stx )) }

            }

        }
        // The responder can only observe the states
        @InitiatedBy(BroadcastTransaction::class)
//Flow session used for sending and receiving transaction between parties
        class  RecordTransactionAsObserver(val otherSession: FlowSession): FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                println("RecordTransactionAsObserver")
                val flow = ReceiveTransactionFlow(
                        otherSideSession = otherSession,
                        checkSufficientSignatures = true,
                        statesToRecord = StatesToRecord.ALL_VISIBLE
                )
                subFlow(flow)
            }
        }
    }

