package com.template

import net.corda.core.contracts.StateRef
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
sealed class CampaignResult{
    class Success(val campaignRef: StateRef) : CampaignResult()
    class Failure : CampaignResult()
}