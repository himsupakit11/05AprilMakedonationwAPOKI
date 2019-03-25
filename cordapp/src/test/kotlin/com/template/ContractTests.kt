package com.template

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices

class ContractTests {
    private val ledgerServices = MockServices()
    private val Fundraiser = TestIdentity(CordaX500Name("P'Toon","BKK","TH"))
    private val Bank = TestIdentity(CordaX500Name("BKK","Bangkok","TH"))
    private val Donor = TestIdentity(CordaX500Name("Alice","BKK","TH"))
    private val Recipient = TestIdentity(CordaX500Name("Hospital","PCKK","TH"))

//    private val validCampaign = Campaign(
//                name = "Kaokonlakao Campaign",
//                target = 1000.DOLLARS,
//                fundraiser = Recipient.party,
//                recipient = Recipient.party,
//                deadline = oneDayFromNow,
//                category = "Charity"
//        )
//    private fun calculatedDeadlineInSeconds(interval: Long) = Instant.now().plusSeconds(interval)
//    private val oneDayFromNow : Instant get() = calculatedDeadlineInSeconds(86400L)
//
//    @Test
//    fun `There must be no campaign input`() {
//        ledgerServices.ledger {
//            transaction {
//               // output(CampaignContract.ID){validCampaign}
//
//            }
//        }
//
//    }

}