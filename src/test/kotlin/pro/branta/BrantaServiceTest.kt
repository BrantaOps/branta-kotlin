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
    private val swapped = "bc1qattacker"
    private val bolt11 = "lnbc100n1ptest"

    private class FakeClient : BrantaClient {
        val lookups = mutableListOf<String>()
        var payments: List<Payment> = emptyList()
        var posted: Payment? = null
        var failIncomplete = false
        var incompleteMessage: String? = null
        override suspend fun getPayments(destinationValue: String, options: BrantaApiOptions?): List<Payment> {
            lookups += destinationValue
            if (failIncomplete) throw IncompleteException(incompleteMessage ?: "InternalServerError")
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
    fun `swapped address with wrong secret is unknown`() = runTest {
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
            "bitcoin:$swapped?branta_id=${encodeUriComponent(cipher)}&branta_secret=00000000-0000-4000-8000-000000000000"
        )
        assertEquals(VerifyStatus.Unknown, result.status)
        assertTrue(result.payments.isEmpty())
    }

    @Test
    fun `attacker lightning or ark leg is unknown`() = runTest {
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = cipher, type = DestinationType.BitcoinAddress, isEncrypted = true)
                )
            )
        )
        assertEquals(
            VerifyStatus.Unknown,
            service(client).verify(
                "bitcoin:$address?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret&lightning=lnbc1attacker"
            ).status,
        )
        assertEquals(
            VerifyStatus.Unknown,
            service(client).verify(
                "bitcoin:$address?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret&ark=ark1attacker"
            ).status,
        )
    }

    @Test
    fun `bip321 bc swap is tampered and matching bc is match`() = runTest {
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = cipher, type = DestinationType.BitcoinAddress, isEncrypted = true)
                )
            )
        )
        assertEquals(
            VerifyStatus.Tampered,
            service(client).verify(
                "bitcoin:?bc=$swapped&branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret"
            ).status,
        )
        assertEquals(
            VerifyStatus.Tampered,
            service(client).verify(
                "bitcoin:$address?bc=$swapped&branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret"
            ).status,
        )
        val match = service(client).verify(
            "bitcoin:?bc=$address&branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret"
        )
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(address, match.payments.single().destinations.single().value)
    }

    @Test
    fun `control char scheme with swapped address is tampered`() = runTest {
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = cipher, type = DestinationType.BitcoinAddress, isEncrypted = true)
                )
            )
        )
        for (uri in listOf(
            "bitcoi\tn:$swapped?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret",
            "bitcoi\nn:$swapped?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret",
            "\u0001bitcoin:$swapped?branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret",
        )) {
            assertEquals(VerifyStatus.Tampered, service(client).verify(uri).status, uri)
        }
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
    fun `malformed lookup is incomplete`() = runTest {
        val client = FakeClient()
        client.failIncomplete = true
        client.incompleteMessage = "MalformedResponse"
        val result = service(client).verify(bolt11)
        assertEquals(VerifyStatus.Incomplete, result.status)
        assertTrue(result.payments.isEmpty())
        assertTrue(result.matchedDestinations.isEmpty())
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

    private fun polUri(recipient: String, encrypted: String, secret: String): String =
        "pol:$recipient?value=50&user=send&currency=USD&branta_id=$encrypted&branta_secret=$secret"

    @Test
    fun `pol usdt match ignores amount query and sets matchedDestinations`() = runTest {
        val cipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = cipher, type = DestinationType.UsdtPolygon, id = "zk-usdt", isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(polUri(usdtPolygonAddr, cipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        assertEquals(DestinationType.UsdtPolygon, result.payments.single().destinations.single().type)
        assertEquals(usdtPolygonAddr, result.payments.single().destinations.single().value)
        assertEquals(1, result.matchedDestinations.size)
        assertEquals(DestinationType.UsdtPolygon, result.matchedDestinations.single().type)
    }

    @Test
    fun `pol usdt case-insensitive match`() = runTest {
        val cipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtPolygon, isEncrypted = true)))
        )
        val result = service(client).verify(polUri(usdtPolygonAddr.lowercase(), cipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
    }

    @Test
    fun `pol usdt 0x prefix case-insensitive match`() = runTest {
        val cipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtPolygon, isEncrypted = true)))
        )
        val mixed = "0X" + usdtPolygonAddr.substring(2)
        val result = service(client).verify(polUri(mixed, cipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
    }

    @Test
    fun `pol usdt swapped is tampered`() = runTest {
        val cipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtPolygon, isEncrypted = true)))
        )
        assertEquals(VerifyStatus.Tampered, service(client).verify(polUri(swappedPolygonAddr, cipher, secret)).status)
    }

    @Test
    fun `bare usdt address is unknown`() = runTest {
        val client = FakeClient()
        val result = service(client).verify(usdtPolygonAddr)
        assertEquals(VerifyStatus.Unknown, result.status)
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `pol without branta params is unknown`() = runTest {
        val client = FakeClient()
        val result = service(client).verify("pol:$usdtPolygonAddr?value=50")
        assertEquals(VerifyStatus.Unknown, result.status)
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `pol ignores sibling bitcoin rail`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val btcCipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = btcCipher, type = DestinationType.BitcoinAddress, isEncrypted = true),
                    Destination(value = usdtCipher, type = DestinationType.UsdtPolygon, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(polUri(usdtPolygonAddr, usdtCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertTrue(dests[0].isEncrypted)
        assertEquals(DestinationType.BitcoinAddress, dests[0].type)
        assertFalse(dests[1].isEncrypted)
        assertEquals(usdtPolygonAddr, dests[1].value)
    }

    @Test
    fun `bitcoin ignores sibling usdt rail`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val btcCipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = btcCipher, type = DestinationType.BitcoinAddress, isEncrypted = true),
                    Destination(value = usdtCipher, type = DestinationType.UsdtPolygon, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify("bitcoin:$address?branta_id=${encodeUriComponent(btcCipher)}&branta_secret=$secret")
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertFalse(dests[0].isEncrypted)
        assertEquals(address, dests[0].value)
        assertTrue(dests[1].isEncrypted)
        assertEquals(DestinationType.UsdtPolygon, dests[1].type)
    }

    @Test
    fun `pol swapped still tampered with sibling bitcoin`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val btcCipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = btcCipher, type = DestinationType.BitcoinAddress, isEncrypted = true),
                    Destination(value = usdtCipher, type = DestinationType.UsdtPolygon, isEncrypted = true)
                )
            )
        )
        assertEquals(VerifyStatus.Tampered, service(client).verify(polUri(swappedPolygonAddr, usdtCipher, secret)).status)
    }

    @Test
    fun `pol match-if-any dual usdt leaves sibling encrypted`() = runTest {
        val matchCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val siblingCipher = AesEncryption.encrypt(siblingUsdtAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = siblingCipher, type = DestinationType.UsdtPolygon, isEncrypted = true),
                    Destination(value = matchCipher, type = DestinationType.UsdtPolygon, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(polUri(usdtPolygonAddr, matchCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertTrue(dests[0].isEncrypted)
        assertEquals(siblingCipher, dests[0].value)
        assertFalse(dests[1].isEncrypted)
        assertEquals(usdtPolygonAddr, dests[1].value)
    }

    @Test
    fun `pol swapped tampered when no polygon family rail matches`() = runTest {
        val matchCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val siblingCipher = AesEncryption.encrypt(siblingUsdtAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = siblingCipher, type = DestinationType.UsdtPolygon, isEncrypted = true),
                    Destination(value = matchCipher, type = DestinationType.UsdtPolygon, isEncrypted = true)
                )
            )
        )
        assertEquals(VerifyStatus.Tampered, service(client).verify(polUri(swappedPolygonAddr, matchCipher, secret)).status)
    }

    @Test
    fun `pol usdc match`() = runTest {
        val cipher = AesEncryption.encrypt(usdcPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdcPolygon, isEncrypted = true)))
        )
        val match = service(client).verify(polUri(usdcPolygonAddr, cipher, secret))
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(DestinationType.UsdcPolygon, match.matchedDestinations.single().type)
    }

    @Test
    fun `pol usdc swapped is tampered`() = runTest {
        val cipher = AesEncryption.encrypt(usdcPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdcPolygon, isEncrypted = true)))
        )
        assertEquals(VerifyStatus.Tampered, service(client).verify(polUri(swappedPolygonAddr, cipher, secret)).status)
    }

    @Test
    fun `pol binds usdt and leaves sibling usdc encrypted`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val usdcCipher = AesEncryption.encrypt(usdcPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = usdtCipher, type = DestinationType.UsdtPolygon, isEncrypted = true),
                    Destination(value = usdcCipher, type = DestinationType.UsdcPolygon, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(polUri(usdtPolygonAddr, usdtCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertFalse(dests[0].isEncrypted)
        assertEquals(DestinationType.UsdtPolygon, dests[0].type)
        assertTrue(dests[1].isEncrypted)
        assertEquals(DestinationType.UsdcPolygon, dests[1].type)
    }

    @Test
    fun `pol binds usdc and leaves sibling usdt encrypted`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val usdcCipher = AesEncryption.encrypt(usdcPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = usdtCipher, type = DestinationType.UsdtPolygon, isEncrypted = true),
                    Destination(value = usdcCipher, type = DestinationType.UsdcPolygon, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(polUri(usdcPolygonAddr, usdcCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertTrue(dests[0].isEncrypted)
        assertEquals(DestinationType.UsdtPolygon, dests[0].type)
        assertFalse(dests[1].isEncrypted)
        assertEquals(DestinationType.UsdcPolygon, dests[1].type)
    }

    @Test
    fun `pol wrong secret is unknown`() = runTest {
        val cipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtPolygon, isEncrypted = true)))
        )
        assertEquals(VerifyStatus.Unknown, service(client).verify(polUri(usdtPolygonAddr, cipher, "wrong-secret")).status)
    }

    @Test
    fun `same address usdt and usdc both match`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val usdcCipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = usdtCipher, type = DestinationType.UsdtPolygon, isEncrypted = true),
                    Destination(value = usdcCipher, type = DestinationType.UsdcPolygon, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(polUri(usdtPolygonAddr, usdtCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        assertEquals(2, result.matchedDestinations.size)
    }

    @Test
    fun `addPayment encrypts usdt and usdc polygon`() = runTest {
        val client = FakeClient()
        val caller = PaymentBuilder().addDestination(usdtPolygonAddr, DestinationType.UsdtPolygon).build()
        val usdt = service(client, FixedSecret("polygon-secret")).addPayment(caller)
        assertEquals(usdtPolygonAddr, caller.destinations.single().value)
        assertTrue(client.posted!!.destinations.single().value != usdtPolygonAddr)
        assertEquals(DestinationType.UsdtPolygon, client.posted!!.destinations.single().type)
        assertEquals("polygon-secret", usdt.secret)

        val usdcClient = FakeClient()
        service(usdcClient, FixedSecret("polygon-secret")).addPayment(
            PaymentBuilder().addDestination(usdcPolygonAddr, DestinationType.UsdcPolygon).build()
        )
        assertEquals(DestinationType.UsdcPolygon, usdcClient.posted!!.destinations.single().type)
        assertTrue(usdcClient.posted!!.destinations.single().value != usdcPolygonAddr)
    }

    private fun chainUri(scheme: String, recipient: String, encrypted: String, secret: String): String =
        "$scheme:$recipient?value=50&branta_id=${encodeUriComponent(encrypted)}&branta_secret=${encodeUriComponent(secret)}"

    private suspend fun assertChainMatch(scheme: String, address: String, type: DestinationType) {
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = type, id = "zk", isEncrypted = true)))
        )
        val result = service(client).verify(chainUri(scheme, address, cipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        assertEquals(listOf(cipher), client.lookups)
        assertEquals(type, result.matchedDestinations.single().type)
        assertEquals(address, result.matchedDestinations.single().value)
    }

    private suspend fun assertChainSwapped(scheme: String, address: String, swapped: String, type: DestinationType) {
        val cipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = type, isEncrypted = true)))
        )
        assertEquals(VerifyStatus.Tampered, service(client).verify(chainUri(scheme, swapped, cipher, secret)).status)
    }

    @Test
    fun `eth usdt match`() = runTest { assertChainMatch("eth", usdtEthereumAddr, DestinationType.UsdtEthereum) }

    @Test
    fun `eth usdc match`() = runTest { assertChainMatch("eth", usdcEthereumAddr, DestinationType.UsdcEthereum) }

    @Test
    fun `bsc usdt match`() = runTest { assertChainMatch("bsc", usdtBscAddr, DestinationType.UsdtBsc) }

    @Test
    fun `bsc usdc match`() = runTest { assertChainMatch("bsc", usdcBscAddr, DestinationType.UsdcBsc) }

    @Test
    fun `tron usdt match`() = runTest { assertChainMatch("tron", usdtTronAddr, DestinationType.UsdtTron) }

    @Test
    fun `ton usdt match`() = runTest { assertChainMatch("ton", usdtTonAddr, DestinationType.UsdtTon) }

    @Test
    fun `sol usdt match`() = runTest { assertChainMatch("sol", usdtSolanaAddr, DestinationType.UsdtSolana) }

    @Test
    fun `sol usdc match`() = runTest { assertChainMatch("sol", usdcSolanaAddr, DestinationType.UsdcSolana) }

    @Test
    fun `eth usdt case insensitive match`() = runTest {
        val cipher = AesEncryption.encrypt(usdtEthereumAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtEthereum, isEncrypted = true)))
        )
        assertEquals(
            VerifyStatus.Match,
            service(client).verify(chainUri("eth", usdtEthereumAddr.lowercase(), cipher, secret)).status
        )
    }

    @Test
    fun `eth usdt 0X prefix match`() = runTest {
        val cipher = AesEncryption.encrypt(usdtEthereumAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtEthereum, isEncrypted = true)))
        )
        val mixedPrefix = "0X" + usdtEthereumAddr.substring(2)
        assertEquals(VerifyStatus.Match, service(client).verify(chainUri("eth", mixedPrefix, cipher, secret)).status)
    }

    @Test
    fun `bsc usdt case insensitive match`() = runTest {
        val cipher = AesEncryption.encrypt(usdtBscAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtBsc, isEncrypted = true)))
        )
        assertEquals(
            VerifyStatus.Match,
            service(client).verify(chainUri("bsc", usdtBscAddr.lowercase(), cipher, secret)).status
        )
    }

    @Test
    fun `bsc usdt 0X prefix match`() = runTest {
        val cipher = AesEncryption.encrypt(usdtBscAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtBsc, isEncrypted = true)))
        )
        assertEquals(
            VerifyStatus.Match,
            service(client).verify(chainUri("bsc", "0X" + usdtBscAddr.substring(2), cipher, secret)).status
        )
    }

    @Test
    fun `eth bsc tron ton sol swapped are tampered`() = runTest {
        assertChainSwapped("eth", usdtEthereumAddr, swappedEvmAddr, DestinationType.UsdtEthereum)
        assertChainSwapped("bsc", usdtBscAddr, swappedEvmAddr, DestinationType.UsdtBsc)
        assertChainSwapped("tron", usdtTronAddr, "TAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", DestinationType.UsdtTron)
        assertChainSwapped("ton", usdtTonAddr, "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c", DestinationType.UsdtTon)
        assertChainSwapped("sol", usdcSolanaAddr, "11111111111111111111111111111111", DestinationType.UsdcSolana)
    }

    @Test
    fun `eth ignores sibling bitcoin rail`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtEthereumAddr, secret)
        val btcCipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = btcCipher, type = DestinationType.BitcoinAddress, isEncrypted = true),
                    Destination(value = usdtCipher, type = DestinationType.UsdtEthereum, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(chainUri("eth", usdtEthereumAddr, usdtCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertTrue(dests[0].isEncrypted)
        assertFalse(dests[1].isEncrypted)
        assertEquals(usdtEthereumAddr, dests[1].value)
    }

    @Test
    fun `eth swapped still tampered with sibling bitcoin`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtEthereumAddr, secret)
        val btcCipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = btcCipher, type = DestinationType.BitcoinAddress, isEncrypted = true),
                    Destination(value = usdtCipher, type = DestinationType.UsdtEthereum, isEncrypted = true)
                )
            )
        )
        assertEquals(
            VerifyStatus.Tampered,
            service(client).verify(chainUri("eth", swappedEvmAddr, usdtCipher, secret)).status
        )
    }

    @Test
    fun `bsc ignores sibling bitcoin rail`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtBscAddr, secret)
        val btcCipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = btcCipher, type = DestinationType.BitcoinAddress, isEncrypted = true),
                    Destination(value = usdtCipher, type = DestinationType.UsdtBsc, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(chainUri("bsc", usdtBscAddr, usdtCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        assertTrue(result.payments.single().destinations[0].isEncrypted)
        assertFalse(result.payments.single().destinations[1].isEncrypted)
    }

    @Test
    fun `bsc swapped still tampered with sibling bitcoin`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtBscAddr, secret)
        val btcCipher = AesEncryption.encrypt(address, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = btcCipher, type = DestinationType.BitcoinAddress, isEncrypted = true),
                    Destination(value = usdtCipher, type = DestinationType.UsdtBsc, isEncrypted = true)
                )
            )
        )
        assertEquals(
            VerifyStatus.Tampered,
            service(client).verify(chainUri("bsc", swappedEvmAddr, usdtCipher, secret)).status
        )
    }

    @Test
    fun `eth binds usdt and leaves sibling usdc encrypted`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtEthereumAddr, secret)
        val usdcCipher = AesEncryption.encrypt(usdcEthereumAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = usdtCipher, type = DestinationType.UsdtEthereum, isEncrypted = true),
                    Destination(value = usdcCipher, type = DestinationType.UsdcEthereum, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(chainUri("eth", usdtEthereumAddr, usdtCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertFalse(dests[0].isEncrypted)
        assertEquals(DestinationType.UsdtEthereum, dests[0].type)
        assertTrue(dests[1].isEncrypted)
        assertEquals(DestinationType.UsdcEthereum, dests[1].type)
    }

    @Test
    fun `bsc binds usdt and leaves sibling usdc encrypted`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtBscAddr, secret)
        val usdcCipher = AesEncryption.encrypt(usdcBscAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = usdtCipher, type = DestinationType.UsdtBsc, isEncrypted = true),
                    Destination(value = usdcCipher, type = DestinationType.UsdcBsc, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(chainUri("bsc", usdtBscAddr, usdtCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        assertEquals(DestinationType.UsdtBsc, result.matchedDestinations.single().type)
        assertTrue(result.payments.single().destinations[1].isEncrypted)
    }

    @Test
    fun `sol binds usdt and leaves sibling usdc encrypted`() = runTest {
        val usdtCipher = AesEncryption.encrypt(usdtSolanaAddr, secret)
        val usdcCipher = AesEncryption.encrypt(usdcSolanaAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = mutableListOf(
                    Destination(value = usdtCipher, type = DestinationType.UsdtSolana, isEncrypted = true),
                    Destination(value = usdcCipher, type = DestinationType.UsdcSolana, isEncrypted = true)
                )
            )
        )
        val result = service(client).verify(chainUri("sol", usdtSolanaAddr, usdtCipher, secret))
        assertEquals(VerifyStatus.Match, result.status)
        assertEquals(DestinationType.UsdtSolana, result.matchedDestinations.single().type)
        assertTrue(result.payments.single().destinations[1].isEncrypted)
    }

    @Test
    fun `eth uri with polygon-only payment is unknown`() = runTest {
        val cipher = AesEncryption.encrypt(usdtPolygonAddr, secret)
        val client = FakeClient()
        client.payments = listOf(
            Payment(destinations = mutableListOf(Destination(value = cipher, type = DestinationType.UsdtPolygon, isEncrypted = true)))
        )
        assertEquals(
            VerifyStatus.Unknown,
            service(client).verify(chainUri("eth", usdtEthereumAddr, cipher, secret)).status
        )
    }

    @Test
    fun `bare chain addresses and uris without branta params are unknown`() = runTest {
        val client = FakeClient()
        listOf(usdtEthereumAddr, usdtBscAddr, usdtTronAddr, usdtTonAddr, usdcSolanaAddr).forEach {
            assertEquals(VerifyStatus.Unknown, service(client).verify(it).status)
        }
        assertEquals(VerifyStatus.Unknown, service(client).verify("eth:$usdtEthereumAddr?value=50").status)
        assertEquals(VerifyStatus.Unknown, service(client).verify("ton:$usdtTonAddr").status)
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `addPayment encrypts every secret-key rail`() = runTest {
        val rails = listOf(
            usdtPolygonAddr to DestinationType.UsdtPolygon,
            usdcPolygonAddr to DestinationType.UsdcPolygon,
            usdtEthereumAddr to DestinationType.UsdtEthereum,
            usdcEthereumAddr to DestinationType.UsdcEthereum,
            usdtBscAddr to DestinationType.UsdtBsc,
            usdcBscAddr to DestinationType.UsdcBsc,
            usdtTronAddr to DestinationType.UsdtTron,
            usdtTonAddr to DestinationType.UsdtTon,
            usdtSolanaAddr to DestinationType.UsdtSolana,
            usdcSolanaAddr to DestinationType.UsdcSolana,
            address to DestinationType.BitcoinAddress
        )
        for ((addr, type) in rails) {
            val client = FakeClient()
            val caller = PaymentBuilder().addDestination(addr, type).build()
            val result = service(client, FixedSecret("rail-secret")).addPayment(caller)
            assertEquals(addr, caller.destinations.single().value)
            val posted = client.posted!!.destinations.single()
            assertTrue(posted.value != addr, type.wire)
            assertEquals(type, posted.type)
            assertEquals("rail-secret", result.secret)
            assertEquals(addr, AesEncryption.decrypt(posted.value, "rail-secret"))
        }
    }

    @Test
    fun `addPayment bare 0x without type is unsupported`() = runTest {
        val client = FakeClient()
        assertFailsWith<BrantaPaymentException> {
            service(client).addPayment(PaymentBuilder().addDestination(usdtPolygonAddr).build())
        }
        assertNull(client.posted)
    }

    companion object {
        private const val usdtPolygonAddr = "0x278297528c32eCa01295B0d2135984d3442ec963"
        private const val usdcPolygonAddr = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B"
        private const val usdtEthereumAddr = "0xBF5550253939187432Ef4dccE8089765d3DCb47f"
        private const val usdcEthereumAddr = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
        private const val usdtBscAddr = "0x55d398326f99059fF775485246999027B3197955"
        private const val usdcBscAddr = "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d"
        private const val usdtTronAddr = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
        private const val usdtTonAddr = "EQDtFpEwcFAEcRe5mLVh2N6C0y-_tur3BYOM93pEZPw390by"
        private const val usdtSolanaAddr = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
        private const val usdcSolanaAddr = "7EqQdEULxWcraVx3mXKFjc84LhCkMGZCkRuDofycyinf"
        private const val siblingUsdtAddr = "0x2222222222222222222222222222222222222222"
        private const val swappedPolygonAddr = "0x1111111111111111111111111111111111111111"
        private const val swappedEvmAddr = "0x3333333333333333333333333333333333333333"
    }
}
