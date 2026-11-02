package pro.branta

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.DestinationType
import pro.branta.enums.VerifyStatus
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.exceptions.IncompleteException
import pro.branta.models.Destination
import pro.branta.models.Payment
import kotlin.test.assertFailsWith

class BrantaServiceTest {
    private val secret = "test-secret"
    private val address = "bc1qmerchant"
    private val bolt11 = "lnbc100n1ptest"

    private class FakeClient : BrantaClient {
        val lookups = mutableListOf<String>()
        var payments: List<Payment> = emptyList()
        var posted: Payment? = null
        var failIncomplete = false
        override suspend fun getPayments(destinationValue: String, options: BrantaApiOptions?): List<Payment> {
            lookups += destinationValue
            if (failIncomplete) throw IncompleteException("InternalServerError")
            return payments
        }
        override suspend fun postPayment(payment: Payment, options: BrantaApiOptions?): Payment {
            posted = payment
            return payment
        }
        override suspend fun isApiKeyValid(options: BrantaApiOptions?): Boolean = true
    }

    private class FixedSecret : SecretGenerator {
        private val values: ArrayDeque<String>
        constructor(vararg values: String) {
            this.values = ArrayDeque(values.toList())
        }
        override fun generate(): String = values.removeFirst()
        override val deterministicNonce: Boolean = false
    }

    private fun service(client: FakeClient, secrets: SecretGenerator = FixedSecret(secret)): BrantaService =
        BrantaService(
            BrantaApiOptions(environment = BrantaEnvironment.Localhost),
            client,
            secrets
        )

    @Test
    fun `bare address is unknown and does not look up`() = runTest {
        val client = FakeClient()
        val result = service(client).verify(address)
        assertEquals(VerifyStatus.Unknown, result.status)
        assertTrue(client.lookups.isEmpty())
        assertTrue(result.verifyUrl.contains("/v2/verify/"))
    }

    @Test
    fun `branta bitcoin qr decrypts the registered address`() = runTest {
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = cipher, type = DestinationType.BitcoinAddress, id = "id 1", isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(
            "bitcoin:$address?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret"
        )
        assertEquals(VerifyStatus.Match, result.status)
        assertEquals(address, result.payments.single().destinations.single().value)
        assertFalse(result.payments.single().destinations.single().isEncrypted)
        assertTrue(result.verifyUrl.contains("k-id%201="))
        assertEquals(cipher, client.lookups.single())
    }

    @Test
    fun `swapped address is tampered`() = runTest {
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = cipher, type = DestinationType.BitcoinAddress, id = "id", isEncrypted = true)
                )
            )
        )
        val result = service(client).verify("bitcoin:bc1qother?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret")
        assertEquals(VerifyStatus.Tampered, result.status)
        assertTrue(result.payments.isEmpty())
    }

    @Test
    fun `wrong secret is unknown`() = runTest {
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = cipher, type = DestinationType.BitcoinAddress, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(
            "bitcoin:$address?branta_id=${encodeUriComponent(cipher)}&branta_secret=wrong"
        )
        assertEquals(VerifyStatus.Unknown, result.status)
    }

    @Test
    fun `uppercase bech32 matches and base58 case does not`() = runTest {
        val base58 = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
        val cipher = AesEncryption.encrypt(base58, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.BitcoinAddress, isEncrypted = true)))
        )
        val swappedCase = service(client).verify(
            "bitcoin:${base58.lowercase()}?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret"
        )
        assertEquals(VerifyStatus.Tampered, swappedCase.status)

        val bech32Cipher = AesEncryption.encrypt(address, secret)
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = bech32Cipher, type = DestinationType.BitcoinAddress, isEncrypted = true)))
        )
        val upper = service(client).verify(
            "bitcoin:${address.uppercase()}?branta_id=${encodeUriComponent(bech32Cipher)}&branta_secret=$secret"
        )
        assertEquals(VerifyStatus.Match, upper.status)
        assertEquals(address, upper.payments.single().destinations.single().value)
    }

    @Test
    fun `bolt11 resolves from its value and a plaintext mismatch is tampered`() = runTest {
        val normalized = bolt11.lowercase()
        val key = normalized.toNormalizedHash()
        val cipher = AesEncryption.encrypt(normalized, key, deterministicNonce = true)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.Bolt11, isEncrypted = true)))
        )
        val match = service(client).verify(bolt11.uppercase())
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(cipher, client.lookups.single())

        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(
                        value = AesEncryption.encrypt("lnbcother", key, deterministicNonce = true),
                        type = DestinationType.Bolt11,
                        isEncrypted = true
                    )
                )
            )
        )
        val tampered = service(client).verify(bolt11)
        assertEquals(VerifyStatus.Tampered, tampered.status)
    }

    @Test
    fun `lno and an id with no payable destination are unknown without a lookup`() = runTest {
        val client = FakeClient()
        val lno = service(client).verify("bitcoin:bc1qmerchant?lno=lno1example")
        assertEquals(VerifyStatus.Unknown, lno.status)
        val idOnly = service(client).verify("bitcoin:?branta_id=abc&branta_secret=$secret")
        assertEquals(VerifyStatus.Unknown, idOnly.status)
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `a failed lookup is incomplete`() = runTest {
        val client = FakeClient()
        client.failIncomplete = true
        val result = service(client).verify(bolt11)
        assertEquals(VerifyStatus.Incomplete, result.status)
    }

    @Test
    fun `addPayment encrypts a copy and leaves the caller plaintext`() = runTest {
        val client = FakeClient()
        val payment = PaymentBuilder()
            .setDescription("Invoice")
            .addDestination(address, DestinationType.BitcoinAddress)
            .setChildPlatform("Child")
            .build()
        val result = service(client, FixedSecret("dek", secret)).addPayment(payment)
        assertEquals(address, payment.destinations.single().value)
        assertTrue(payment.metadata!!.contains("Invoice"))
        val posted = client.posted!!
        assertEquals(secret, result.secret)
        assertTrue(posted.metadata != payment.metadata)
        assertEquals("Child", posted.childPlatform?.name)
        assertNull(posted.description)
        val wire = paymentToJson(posted)
        assertTrue(wire.contains("\"zk\":true"))
        assertTrue(wire.contains("child_platform"))
        assertFalse(wire.contains("\"description\""))
    }

    @Test
    fun `a value that cannot be encrypted throws before the client is called`() = runTest {
        val client = FakeClient()
        val payment = PaymentBuilder().addDestination("not-a-destination").build()
        assertFailsWith<BrantaPaymentException> { service(client).addPayment(payment) }
        assertEquals("not-a-destination", payment.destinations.single().value)
    }

    @Test
    fun `null platform objects parse as absent`() {
        val payment = paymentFromJson(
            """{"destinations":[],"ttl":null,"parent_platform":null,"child_platform":null}"""
        )
        assertNull(payment.ttl)
        assertNull(payment.parentPlatform)
        assertNull(payment.childPlatform)
    }

    @Test
    fun `description is lifted after the DEK decrypts`() = runTest {
        val dek = "dek-secret"
        val metadata = """{"description":"Invoice"}"""
        val encryptedMetadata = AesEncryption.encrypt(metadata, dek)
        val encryptedDek = AesEncryption.encrypt(dek, secret)
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                metadata = encryptedMetadata,
                destinations = mutableListOf(
                    Destination(
                        value = cipher,
                        type = DestinationType.BitcoinAddress,
                        id = "id",
                        encryptedDek = encryptedDek,
                        isEncrypted = true
                    )
                )
            )
        )
        val result = service(client).verify("bitcoin:$address?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret")
        assertEquals("Invoice", result.payments.single().description)
    }
}
