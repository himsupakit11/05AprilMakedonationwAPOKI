package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.time.Instant
import java.util.*


// ************
// * Contract *
// ************
class CampaignContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.CampaignContract"
    }
    
    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val campaignCommand = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = campaignCommand.signers.toSet()

        when(campaignCommand.value){
            is Commands.Start -> verifyStrart(tx,setOfSigners)
            is Commands.AcceptDonation -> verifyDonation(tx,setOfSigners)
            else -> throw IllegalArgumentException("")
        }
    }

    private fun verifyStrart(tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
        "No input states should be consumed when creating a campaign." using(tx.inputStates.isEmpty())
        "Only one campaign state should be produced when creating a campaign." using (tx.outputStates.size == 1)
        val campaign = tx.outputStates.single() as Campaign
        "The target field of a recently created campaign should be a positive value." using (campaign.target > Amount(0,campaign.target.token))
        "There raised field must be 0 when starting a campaign." using(campaign.raised == Amount(0,campaign.target.token))
        "The campaign deadline must be in the future." using (campaign.deadline > Instant.now())
        "There must be a campaign name." using (campaign.name != "")
        "The campaign must only be signed by fundraiser" using (signers == setOf(campaign.fundraiser.owningKey) )
        "There must be a campaign category" using (campaign.category != "")

    }

    private fun verifyDonation(tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
        "An accept donation transaction must be only one input state" using (tx.inputStates.size == 1)
        "Two inputs state must be produced when accepting pledge" using (tx.outputStates.size == 2)
        val campaignInput: Campaign = tx.inputsOfType<Campaign>().single()
        val campaignOutput: Campaign = tx.outputsOfType<Campaign>().single()
        val donationOutput: Donation = tx.outputsOfType<Donation>().single()

        val changeInAmountRaised: Amount<Currency> = campaignOutput.raised - campaignInput.raised
        "The donation must be for this campaign" using (donationOutput.campaignReference == campaignOutput.linearId)
        "The raised amount must be updated by the new amount donated" using (changeInAmountRaised == donationOutput.amount)

        "The campaign name cannot be changed when accepting a donation" using (campaignInput.name == campaignOutput.name)
        "The campaign target cannot be changed when accepting a donation" using (campaignInput.target == campaignOutput.target)
        "The fundraiser cannot be changed when accepting a donation" using (campaignInput.fundraiser == campaignOutput.fundraiser)
        "The Recipient cannot be changed when accepting a donation" using (campaignInput.recipient == campaignOutput.recipient)
        "The campaign deadline cannot be changed when accepting a donation" using (campaignInput.deadline == campaignOutput.deadline)
        "The campaign category cannot be changed when accepting a donation" using (campaignInput.category == campaignOutput.category)

        //Assert that donation cannot make after the deadline
        tx.timeWindow?.midpoint?.let {
            "The donation cannot be accepted after the campaign deadline" using (it < campaignOutput.deadline)
        }?: throw java.lang.IllegalArgumentException("A time stamp is required when making a donation")

        // Assert signer
        "The campaign must only be signed by fundraiser" using (signers.single() == campaignOutput.fundraiser.owningKey)

    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Start : TypeOnlyCommandData(),Commands
        class End : TypeOnlyCommandData(), Commands
        class AcceptDonation: TypeOnlyCommandData(), Commands
    }
}
// Return public key of participants
fun keysFromParticipants(obligation: ContractState): Set<PublicKey>{
    return obligation.participants.map { it.owningKey }.toSet()
}

// *********
// * State *
// *********
data class Campaign(
        val name: String,
        val target: Amount<Currency>,
        val raised: Amount<Currency> = Amount(0,target.token),
        val fundraiser: Party,
        val recipient: Party,
        val deadline: Instant,
        val category: String,
        override val participants: List<AbstractParty > = listOf(fundraiser,recipient),
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState//,SchedulableState {
//    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
//        return ScheduledActivity(flowLogicRefFactory.create(EndCampaign.Initator::class.java,thisStateRef),deadline)
//    }

//}
