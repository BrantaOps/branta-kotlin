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
            .setBrand("Child")
            .build()
        val result = service(client, FixedSecret("dek", secret)).addPayment(payment)
        assertEquals(address, payment.destinations.single().value)
        assertTrue(payment.metadata!!.contains("Invoice"))
        val posted = client.posted!!
        assertEquals(secret, result.secret)
        assertTrue(posted.metadata != payment.metadata)
        assertEquals("Child", posted.brand?.name)
        assertNull(posted.description)
        val wire = paymentToJson(posted)
        assertTrue(wire.contains("\"zk\":true"))
        assertTrue(wire.contains("\"brand\""))
        assertFalse(wire.contains("child_platform"))
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
        assertNull(payment.brand)
        @Suppress("DEPRECATION")
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

    private fun rail(type: DestinationType): DestinationType.EvmTokenRail =
        DestinationType.EvmTokenRails.first { it.type == type }

    /** ERC-681 transfer URI for one EVM rail, as a wallet would show it. */
    private fun transferUri(
        type: DestinationType,
        recipient: String,
        encrypted: String,
        secretParam: String = secret,
        amount: String = "1000000"
    ): String {
        val rail = rail(type)
        return "ethereum:${rail.contract}@${rail.chainId}/transfer?address=$recipient&uint256=$amount" +
            "&branta_id=${encodeUriComponent(encrypted)}&branta_secret=${encodeUriComponent(secretParam)}"
    }

    /** One mixed-case deposit per rail, so a lowercase scan still differs from the stored text. */
    private fun depositFor(type: DestinationType): String {
        val index = DestinationType.EvmTokenRails.indexOfFirst { it.type == type } + 1
        val hex = index.toString(16)
        return "0xAb" + "1".repeat(38 - hex.length) + hex
    }

    /** A fresh client and payment. The service mutates the payments it gets back, so never reuse one. */
    private fun clientWith(vararg destinations: Pair<DestinationType, String>): FakeClient {
        val client = FakeClient()
        client.payments = listOf(
            Payment(
                destinations = destinations.mapIndexed { i, (type, cipher) ->
                    Destination(value = cipher, type = type, id = "zk-$i", isEncrypted = true)
                }.toMutableList()
            )
        )
        return client
    }

    private fun cipherOf(address: String): String = AesEncryption.encrypt(address, secret)

    // ---- Polygon

    @Test
    fun `polygon usdt match ignores amount and sets matchedDestinations`() = runTest {
        val cipher = cipherOf(usdtPolygonAddr)
        val client = clientWith(DestinationType.UsdtPolygon to cipher)
        val result = service(client).verify(transferUri(DestinationType.UsdtPolygon, usdtPolygonAddr, cipher))
        assertEquals(VerifyStatus.Match, result.status)
        assertEquals(listOf(cipher), client.lookups)
        assertEquals(DestinationType.UsdtPolygon, result.payments.single().destinations.single().type)
        assertEquals(usdtPolygonAddr, result.payments.single().destinations.single().value)
        assertEquals(1, result.matchedDestinations.size)
        assertEquals(DestinationType.UsdtPolygon, result.matchedDestinations.single().type)
    }

    @Test
    fun `amount is not part of the match`() = runTest {
        val cipher = cipherOf(usdtPolygonAddr)
        for (amount in listOf("1", "50000000", "999999999999")) {
            val client = clientWith(DestinationType.UsdtPolygon to cipher)
            val result = service(client).verify(
                transferUri(DestinationType.UsdtPolygon, usdtPolygonAddr, cipher, amount = amount)
            )
            assertEquals(VerifyStatus.Match, result.status, amount)
        }
    }

    @Test
    fun `polygon usdt case-insensitive and 0X prefix match`() = runTest {
        val cipher = cipherOf(usdtPolygonAddr)
        val lower = service(clientWith(DestinationType.UsdtPolygon to cipher))
            .verify(transferUri(DestinationType.UsdtPolygon, usdtPolygonAddr.lowercase(), cipher))
        assertEquals(VerifyStatus.Match, lower.status)
        val mixed = "0X" + usdtPolygonAddr.substring(2)
        val prefixed = service(clientWith(DestinationType.UsdtPolygon to cipher))
            .verify(transferUri(DestinationType.UsdtPolygon, mixed, cipher))
        assertEquals(VerifyStatus.Match, prefixed.status)
    }

    @Test
    fun `polygon usdt swapped is tampered`() = runTest {
        val cipher = cipherOf(usdtPolygonAddr)
        val result = service(clientWith(DestinationType.UsdtPolygon to cipher))
            .verify(transferUri(DestinationType.UsdtPolygon, swappedPolygonAddr, cipher))
        assertEquals(VerifyStatus.Tampered, result.status)
        assertTrue(result.matchedDestinations.isEmpty())
    }

    @Test
    fun `polygon scan ignores sibling bitcoin rail`() = runTest {
        val usdtCipher = cipherOf(usdtPolygonAddr)
        val btcCipher = cipherOf(address)
        val client = clientWith(DestinationType.BitcoinAddress to btcCipher, DestinationType.UsdtPolygon to usdtCipher)
        val result = service(client).verify(transferUri(DestinationType.UsdtPolygon, usdtPolygonAddr, usdtCipher))
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertTrue(dests[0].isEncrypted)
        assertEquals(DestinationType.BitcoinAddress, dests[0].type)
        assertFalse(dests[1].isEncrypted)
        assertEquals(usdtPolygonAddr, dests[1].value)
    }

    @Test
    fun `bitcoin scan ignores sibling usdt rail`() = runTest {
        val usdtCipher = cipherOf(usdtPolygonAddr)
        val btcCipher = cipherOf(address)
        val client = clientWith(DestinationType.BitcoinAddress to btcCipher, DestinationType.UsdtPolygon to usdtCipher)
        val result = service(client).verify("bitcoin:$address?branta_id=${encodeUriComponent(btcCipher)}&branta_secret=$secret")
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertFalse(dests[0].isEncrypted)
        assertEquals(address, dests[0].value)
        assertTrue(dests[1].isEncrypted)
        assertEquals(DestinationType.UsdtPolygon, dests[1].type)
    }

    @Test
    fun `polygon swapped is still tampered with a sibling bitcoin rail`() = runTest {
        val usdtCipher = cipherOf(usdtPolygonAddr)
        val client = clientWith(
            DestinationType.BitcoinAddress to cipherOf(address),
            DestinationType.UsdtPolygon to usdtCipher
        )
        val result = service(client).verify(transferUri(DestinationType.UsdtPolygon, swappedPolygonAddr, usdtCipher))
        assertEquals(VerifyStatus.Tampered, result.status)
    }

    @Test
    fun `a second usdt destination with another address stays encrypted`() = runTest {
        val matchCipher = cipherOf(usdtPolygonAddr)
        val siblingCipher = cipherOf(siblingUsdtAddr)
        val client = clientWith(
            DestinationType.UsdtPolygon to siblingCipher,
            DestinationType.UsdtPolygon to matchCipher
        )
        val result = service(client).verify(transferUri(DestinationType.UsdtPolygon, usdtPolygonAddr, matchCipher))
        assertEquals(VerifyStatus.Match, result.status)
        val dests = result.payments.single().destinations
        assertTrue(dests[0].isEncrypted)
        assertEquals(siblingCipher, dests[0].value)
        assertFalse(dests[1].isEncrypted)
        assertEquals(usdtPolygonAddr, dests[1].value)
    }

    @Test
    fun `swapped is tampered when no destination of the type matches`() = runTest {
        val matchCipher = cipherOf(usdtPolygonAddr)
        val client = clientWith(
            DestinationType.UsdtPolygon to cipherOf(siblingUsdtAddr),
            DestinationType.UsdtPolygon to matchCipher
        )
        val result = service(client).verify(transferUri(DestinationType.UsdtPolygon, swappedPolygonAddr, matchCipher))
        assertEquals(VerifyStatus.Tampered, result.status)
    }

    @Test
    fun `polygon usdc match and swapped`() = runTest {
        val cipher = cipherOf(usdcPolygonAddr)
        val match = service(clientWith(DestinationType.UsdcPolygon to cipher))
            .verify(transferUri(DestinationType.UsdcPolygon, usdcPolygonAddr, cipher))
        assertEquals(VerifyStatus.Match, match.status)
        assertEquals(DestinationType.UsdcPolygon, match.matchedDestinations.single().type)
        val swapped = service(clientWith(DestinationType.UsdcPolygon to cipher))
            .verify(transferUri(DestinationType.UsdcPolygon, swappedPolygonAddr, cipher))
        assertEquals(VerifyStatus.Tampered, swapped.status)
    }

    @Test
    fun `a scan binds the scanned token and leaves the other encrypted`() = runTest {
        val usdtCipher = cipherOf(usdtPolygonAddr)
        val usdcCipher = cipherOf(usdcPolygonAddr)
        val cases = listOf(
            Triple(DestinationType.UsdtPolygon, usdtPolygonAddr, usdtCipher) to 0,
            Triple(DestinationType.UsdcPolygon, usdcPolygonAddr, usdcCipher) to 1
        )
        for ((scan, boundIndex) in cases) {
            val client = clientWith(
                DestinationType.UsdtPolygon to usdtCipher,
                DestinationType.UsdcPolygon to usdcCipher
            )
            val result = service(client).verify(transferUri(scan.first, scan.second, scan.third))
            assertEquals(VerifyStatus.Match, result.status, scan.first.wire)
            result.payments.single().destinations.forEachIndexed { i, d ->
                assertEquals(i != boundIndex, d.isEncrypted, "${scan.first.wire} #$i")
            }
        }
    }

    @Test
    fun `polygon wrong secret is unknown`() = runTest {
        val cipher = cipherOf(usdtPolygonAddr)
        val result = service(clientWith(DestinationType.UsdtPolygon to cipher))
            .verify(transferUri(DestinationType.UsdtPolygon, usdtPolygonAddr, cipher, secretParam = "wrong-secret"))
        assertEquals(VerifyStatus.Unknown, result.status)
    }

    @Test
    fun `the same address under two tokens binds only the scanned token`() = runTest {
        val cipher = cipherOf(usdtPolygonAddr)
        for (scan in listOf(DestinationType.UsdtPolygon, DestinationType.UsdcPolygon)) {
            val client = clientWith(DestinationType.UsdtPolygon to cipher, DestinationType.UsdcPolygon to cipher)
            val result = service(client).verify(transferUri(scan, usdtPolygonAddr, cipher))
            assertEquals(VerifyStatus.Match, result.status, scan.wire)
            assertEquals(listOf(scan), result.matchedDestinations.map { it.type }, scan.wire)
        }
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

    // ---- Every EVM rail

    @Test
    fun `every evm rail matches and returns the scanned type`() = runTest {
        for (rail in DestinationType.EvmTokenRails) {
            val type = rail.type
            val addr = depositFor(type)
            val cipher = cipherOf(addr)

            val client = clientWith(type to cipher)
            val result = service(client).verify(transferUri(type, addr, cipher))
            assertEquals(VerifyStatus.Match, result.status, type.wire)
            assertEquals(listOf(cipher), client.lookups, type.wire)
            assertEquals(listOf(type), result.matchedDestinations.map { it.type }, type.wire)
            assertEquals(listOf(addr), result.matchedDestinations.map { it.value }, type.wire)

            val lower = service(clientWith(type to cipher)).verify(transferUri(type, addr.lowercase(), cipher))
            assertEquals(VerifyStatus.Match, lower.status, type.wire)

            val tampered = service(clientWith(type to cipher)).verify(transferUri(type, swappedEvmAddr, cipher))
            assertEquals(VerifyStatus.Tampered, tampered.status, type.wire)
            assertTrue(tampered.matchedDestinations.isEmpty(), type.wire)
        }
    }

    @Test
    fun `every evm rail on the wrong chain or without branta params is unknown without a lookup`() = runTest {
        val client = FakeClient()
        for (rail in DestinationType.EvmTokenRails) {
            val addr = depositFor(rail.type)
            val wrongChain = service(client).verify(
                "ethereum:${rail.contract}@999999/transfer?address=$addr&branta_id=enc&branta_secret=$secret"
            )
            assertEquals(VerifyStatus.Unknown, wrongChain.status, rail.type.wire)
            val noParams = service(client).verify(
                "ethereum:${rail.contract}@${rail.chainId}/transfer?address=$addr&uint256=1000000"
            )
            assertEquals(VerifyStatus.Unknown, noParams.status, rail.type.wire)
        }
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `an ethereum scan ignores a sibling bitcoin rail`() = runTest {
        val usdtCipher = cipherOf(usdtEthereumAddr)
        val btcCipher = cipherOf(address)
        val result = service(
            clientWith(DestinationType.BitcoinAddress to btcCipher, DestinationType.UsdtEthereum to usdtCipher)
        ).verify(transferUri(DestinationType.UsdtEthereum, usdtEthereumAddr, usdtCipher))
        assertEquals(VerifyStatus.Match, result.status)
        assertTrue(result.payments.single().destinations[0].isEncrypted)
        assertEquals(DestinationType.UsdtEthereum, result.matchedDestinations.single().type)

        val swapped = service(
            clientWith(DestinationType.BitcoinAddress to btcCipher, DestinationType.UsdtEthereum to usdtCipher)
        ).verify(transferUri(DestinationType.UsdtEthereum, swappedEvmAddr, usdtCipher))
        assertEquals(VerifyStatus.Tampered, swapped.status)
    }

    @Test
    fun `a dual-token payment binds only the scanned token`() = runTest {
        val usdtCipher = cipherOf(usdtEthereumAddr)
        val usdcCipher = cipherOf(usdcEthereumAddr)
        fun dual() = clientWith(
            DestinationType.UsdtEthereum to usdtCipher,
            DestinationType.UsdcEthereum to usdcCipher
        )

        val usdt = service(dual()).verify(transferUri(DestinationType.UsdtEthereum, usdtEthereumAddr, usdtCipher))
        assertEquals(VerifyStatus.Match, usdt.status)
        assertEquals(listOf(DestinationType.UsdtEthereum), usdt.matchedDestinations.map { it.type })
        assertFalse(usdt.payments.single().destinations[0].isEncrypted)
        assertTrue(usdt.payments.single().destinations[1].isEncrypted)

        val usdc = service(dual()).verify(transferUri(DestinationType.UsdcEthereum, usdcEthereumAddr, usdtCipher))
        assertEquals(VerifyStatus.Match, usdc.status)
        assertEquals(listOf(DestinationType.UsdcEthereum), usdc.matchedDestinations.map { it.type })
        assertTrue(usdc.payments.single().destinations[0].isEncrypted)
        assertFalse(usdc.payments.single().destinations[1].isEncrypted)

        val swapped = service(dual()).verify(transferUri(DestinationType.UsdtEthereum, swappedEvmAddr, usdtCipher))
        assertEquals(VerifyStatus.Tampered, swapped.status)
    }

    @Test
    fun `a usdc scan against a usdt-only payment is unknown`() = runTest {
        val cipher = cipherOf(usdtEthereumAddr)
        val result = service(clientWith(DestinationType.UsdtEthereum to cipher))
            .verify(transferUri(DestinationType.UsdcEthereum, usdcEthereumAddr, cipher))
        assertEquals(VerifyStatus.Unknown, result.status)
        assertTrue(result.matchedDestinations.isEmpty())
    }

    @Test
    fun `an ethereum scan does not match a polygon-only payment`() = runTest {
        val cipher = cipherOf(usdtPolygonAddr)
        val result = service(clientWith(DestinationType.UsdtPolygon to cipher))
            .verify(transferUri(DestinationType.UsdtEthereum, usdtEthereumAddr, cipher))
        assertEquals(VerifyStatus.Unknown, result.status)
        assertTrue(result.matchedDestinations.isEmpty())
    }

    @Test
    fun `an ethereum scan leaves a polygon rail in the same payment encrypted`() = runTest {
        val polCipher = cipherOf(usdtPolygonAddr)
        val ethCipher = cipherOf(usdtEthereumAddr)
        val result = service(
            clientWith(DestinationType.UsdtPolygon to polCipher, DestinationType.UsdtEthereum to ethCipher)
        ).verify(transferUri(DestinationType.UsdtEthereum, usdtEthereumAddr, ethCipher))
        assertEquals(VerifyStatus.Match, result.status)
        assertTrue(result.payments.single().destinations[0].isEncrypted)
        assertFalse(result.payments.single().destinations[1].isEncrypted)
        assertEquals(1, result.matchedDestinations.size)
    }

    // ---- Forms that do not verify

    @Test
    fun `other functions are unknown without a lookup`() = runTest {
        val rail = rail(DestinationType.UsdtBase)
        val client = FakeClient()
        for (function in listOf("approve", "transferFrom", "transfer;x=1")) {
            val result = service(client).verify(
                "ethereum:${rail.contract}@${rail.chainId}/$function?address=$swappedEvmAddr&uint256=1&branta_id=abc&branta_secret=$secret"
            )
            assertEquals(VerifyStatus.Unknown, result.status, function)
        }
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `the authority form is unknown without a lookup`() = runTest {
        val rail = rail(DestinationType.UsdtBase)
        val client = FakeClient()
        val result = service(client).verify(
            "ethereum://${rail.contract}@${rail.chainId}/transfer?address=$swappedEvmAddr&branta_id=abc&branta_secret=$secret"
        )
        assertEquals(VerifyStatus.Unknown, result.status)
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `unparseable input is unknown and does not throw`() = runTest {
        val client = FakeClient()
        val inputs = listOf(
            "ethereum://[bad/transfer",
            "bitcoin://[bad",
            "ethereum:0xc2132d05d31c914a87c6611c10748aeb04b58e8f@" + "9".repeat(5000) +
                "/transfer?address=$swappedEvmAddr"
        )
        for (input in inputs) {
            assertEquals(VerifyStatus.Unknown, service(client).verify(input).status, input.take(40))
        }
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `removed schemes are unknown without a lookup even with valid params`() = runTest {
        val cipher = cipherOf(usdtPolygonAddr)
        val client = clientWith(DestinationType.UsdtPolygon to cipher)
        for (scheme in listOf("pol", "eth", "bsc", "tron", "ton", "sol")) {
            val result = service(client).verify(
                "$scheme:$usdtPolygonAddr?value=50&branta_id=${encodeUriComponent(cipher)}&branta_secret=$secret"
            )
            assertEquals(VerifyStatus.Unknown, result.status, scheme)
        }
        assertTrue(client.lookups.isEmpty())
    }

    @Test
    fun `bare chain addresses are unknown without a lookup`() = runTest {
        val client = FakeClient()
        listOf(usdtPolygonAddr, usdtEthereumAddr, usdtTronAddr, usdtTonAddr, usdcSolanaAddr).forEach {
            assertEquals(VerifyStatus.Unknown, service(client).verify(it).status, it)
        }
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
            depositFor(DestinationType.UsdtBase) to DestinationType.UsdtBase,
            depositFor(DestinationType.UsdcBase) to DestinationType.UsdcBase,
            depositFor(DestinationType.UsdtArbitrum) to DestinationType.UsdtArbitrum,
            depositFor(DestinationType.UsdcArbitrum) to DestinationType.UsdcArbitrum,
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
