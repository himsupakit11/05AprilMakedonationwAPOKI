package com.template

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

data class Donation(
    val campaignReference: UniqueIdentifier,
    val fundraiser: Party,
    val donor: AbstractParty,
    val amount: Amount<Currency>,
    override val participants: List<AbstractParty> = listOf(donor,fundraiser),
    override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState,QueryableState{
    override fun supportedSchemas() = listOf(DonationSchema)
    override fun generateMappedObject(schema: MappedSchema) = DonationSchema.DonationEntity(this)

    object DonationSchema : MappedSchema(Donation::class.java,1, listOf(DonationEntity::class.java)){
    @Entity
    @Table(name = "donations")
    class DonationEntity(donation: Donation): PersistentState() {
        @Column
        var currency: String = donation.amount.token.toString()
        @Column
        var amount: Long = donation.amount.quantity
        @Column
        @Lob
        var donor: ByteArray = donation.donor.owningKey.encoded
        @Column
        @Lob
        var fundraiser: ByteArray = donation.fundraiser.owningKey.encoded
        @Column
        var campaign_reference: String = donation.campaignReference.id.toString()
        @Column
        var linear_id: String = donation.linearId.id.toString()
        }
    }
}
