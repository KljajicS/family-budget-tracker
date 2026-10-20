package app.kinjar.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
enum class Currency { RSD, EUR, USD }

@Serializable
data class Money(
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val currency: Currency,
) {
    init {
        require(amount.scale() == SCALE) { "Money amount must have scale $SCALE, use Money.of" }
    }

    val isZero: Boolean get() = amount.signum() == 0
    val isNegative: Boolean get() = amount.signum() < 0

    operator fun plus(other: Money): Money {
        requireSame(other)
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        requireSame(other)
        return Money(amount - other.amount, currency)
    }

    operator fun unaryMinus(): Money = Money(amount.negate(), currency)

    private fun requireSame(other: Money) =
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }

    companion object {
        const val SCALE = 2

        fun of(amount: BigDecimal, currency: Currency): Money =
            Money(amount.setScale(SCALE, RoundingMode.HALF_UP), currency)

        fun of(amount: String, currency: Currency): Money = of(BigDecimal(amount), currency)

        fun zero(currency: Currency): Money = of(BigDecimal.ZERO, currency)
    }
}

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}
