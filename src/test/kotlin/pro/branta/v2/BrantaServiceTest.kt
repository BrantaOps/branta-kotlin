package pro.branta.v2

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pro.branta.BrantaClientOptions
import pro.branta.enums.BrantaServerBaseUrl
import pro.branta.enums.DestinationType
import pro.branta.enums.PrivacyMode
import pro.branta.exceptions.BrantaPaymentException
import pro.branta.v2.interfaces.IAesEncryption
import pro.branta.v2.interfaces.IBrantaClient
import pro.branta.v2.interfaces.ISecretGenerator
import pro.branta.v2.models.Destination
import pro.branta.v2.models.Payment
import kotlin.test.assertFailsWith

class BrantaServiceTest {

    private lateinit var clientMock: IBrantaClient
    private lateinit var aesMock: IAesEncryption
    private lateinit var secretGeneratorMock: ISecretGenerator
    private lateinit var service: BrantaService
    private lateinit var strictService: BrantaService

    private val defaultOptions = BrantaClientOptions(
        baseUrl = BrantaServerBaseUrl.Localhost,
        defaultApiKey = "test-api-key",
        privacy = PrivacyMode.Loose
    )
    private val strictOptions = BrantaClientOptions(
        baseUrl = BrantaServerBaseUrl.Localhost,
        defaultApiKey = "test-api-key",
        privacy = PrivacyMode.Strict
    )

    companion object {
        const val BITCOIN_ADDRESS = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
        const val ENCRYPTED_BITCOIN_ADDRESS = "encrypted-bitcoin-address"
        const val SECRET = "test-secret"

        const val BOLT11_INVOICE = "lnbc100n1ptest"
        const val ENCRYPTED_BOLT11 = "encrypted-bolt11-value"
        const val DECRYPTED_BOLT11 = "lnbc100n1pdecrypted"

        const val ARK_ADDRESS = "ark100testaddress"
        const val ENCRYPTED_ARK_ADDRESS = "encrypted-ark-address"

        const val PLAIN_METADATA = "{\"email\":\"test@example.com\"}"
        const val ENCRYPTED_METADATA = "encrypted-metadata-ciphertext"
        const val DEK = "test-dek"
        const val ENCRYPTED_DEK_FOR_BITCOIN = "encrypted-dek-for-bitcoin"
        const val ENCRYPTED_DEK_FOR_BOLT11 = "encrypted-dek-for-bolt11"

        const val ENCRYPTED_DEK_VALUE = "encrypted-dek-blob"
        const val DECRYPTED_DEK = "decrypted-dek-value"
        const val DECRYPTED_METADATA = "{\"email\":\"alice@example.com\"}"
        const val ENCRYPTED_METADATA_BLOB = "encrypted-metadata-blob"
    }

    private val bolt11Hash get() = BOLT11_INVOICE.toNormalizedHash()
    private val arkHash get() = ARK_ADDRESS.toNormalizedHash()

    private fun plainBitcoinPayment() = PaymentBuilder()
        .addDestination(BITCOIN_ADDRESS, DestinationType.BitcoinAddress).build()

    private fun zkBitcoinPayment() = PaymentBuilder()
        .addDestination(ENCRYPTED_BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk().build()

    private fun zkBolt11Payment() = PaymentBuilder()
        .addDestination(ENCRYPTED_BOLT11, DestinationType.Bolt11).setZk().build()

    private fun plainBolt11Payment() = PaymentBuilder()
        .addDestination(BOLT11_INVOICE, DestinationType.Bolt11).build()

    private fun zkArkPayment() = PaymentBuilder()
        .addDestination(ENCRYPTED_ARK_ADDRESS, DestinationType.ArkAddress).setZk().build()

    @BeforeEach
    fun setUp() {
        clientMock = mockk()
        aesMock = mockk()
        secretGeneratorMock = mockk()

        every { secretGeneratorMock.generate() } returns SECRET
        every { secretGeneratorMock.deterministicNonce } returns false

        // Catch-all: any unexpected decrypt throws (service catches this silently)
        every { aesMock.decrypt(any(), any()) } throws Exception("Decryption failed: auth tag mismatch")
        // Known decrypt answers:
        every { aesMock.decrypt(ENCRYPTED_BITCOIN_ADDRESS, SECRET) } returns BITCOIN_ADDRESS
        every { aesMock.decrypt(ENCRYPTED_BOLT11, bolt11Hash) } returns DECRYPTED_BOLT11
        every { aesMock.decrypt(ENCRYPTED_ARK_ADDRESS, arkHash) } returns ARK_ADDRESS

        every { aesMock.encrypt(BOLT11_INVOICE, bolt11Hash, true) } returns ENCRYPTED_BOLT11
        every { aesMock.encrypt(BITCOIN_ADDRESS, SECRET, false) } returns ENCRYPTED_BITCOIN_ADDRESS
        every { aesMock.encrypt(ARK_ADDRESS, arkHash, true) } returns ENCRYPTED_ARK_ADDRESS

        service = BrantaService(defaultOptions, clientMock, aesMock, secretGeneratorMock)
        strictService = BrantaService(strictOptions, clientMock, aesMock, secretGeneratorMock)
    }

    // region getPaymentsByQrCode

    @Test
    fun `getPaymentsByQrCode ZK bitcoin URI uses branta_id as lookup key and decrypts`() = runTest {
        val payment = zkBitcoinPayment()
        coEvery { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) } returns listOf(payment)

        val qrText = "bitcoin:$BITCOIN_ADDRESS?branta_id=$ENCRYPTED_BITCOIN_ADDRESS&branta_secret=$SECRET"
        val result = service.getPaymentsByQrCode(qrText)

        coVerify { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) }
        assertEquals(BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
    }

    @Test
    fun `getPaymentsByQrCode plain bitcoin URI uses address as lookup key`() = runTest {
        coEvery { clientMock.getPayments(BITCOIN_ADDRESS, null) } returns listOf(plainBitcoinPayment())

        val result = service.getPaymentsByQrCode("bitcoin:$BITCOIN_ADDRESS")

        coVerify { clientMock.getPayments(BITCOIN_ADDRESS, null) }
        assertEquals(1, result.payments.size)
    }

    @Test
    fun `getPaymentsByQrCode lightning bolt11 URI uses encrypted invoice lookup`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(plainBolt11Payment())

        service.getPaymentsByQrCode("lightning:$BOLT11_INVOICE")

        coVerify { clientMock.getPayments(ENCRYPTED_BOLT11, any()) }
    }

    @Test
    fun `getPaymentsByQrCode uppercase lightning bolt11 URI uses encrypted lookup`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(plainBolt11Payment())

        service.getPaymentsByQrCode("lightning:${BOLT11_INVOICE.uppercase()}")

        coVerify { clientMock.getPayments(ENCRYPTED_BOLT11, any()) }
    }

    @Test
    fun `getPaymentsByQrCode bolt11 URI leaves unrelated ZK bitcoin destination encrypted`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(ENCRYPTED_BOLT11, DestinationType.Bolt11).setZk()
            .addDestination(ENCRYPTED_BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk()
            .build()
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(payment)

        val result = service.getPaymentsByQrCode("lightning:$BOLT11_INVOICE")

        assertEquals(1, result.payments.size)
        assertEquals(DECRYPTED_BOLT11, result.payments[0].destinations[0].value)
        assertFalse(result.payments[0].destinations[0].isEncrypted)
        assertEquals(ENCRYPTED_BITCOIN_ADDRESS, result.payments[0].destinations[1].value)
        assertTrue(result.payments[0].destinations[1].isEncrypted)
    }

    @Test
    fun `getPaymentsByQrCode combined ZK QR decrypts address invoice and ark`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(ENCRYPTED_BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk()
            .addDestination(ENCRYPTED_BOLT11, DestinationType.Bolt11).setZk()
            .addDestination(ENCRYPTED_ARK_ADDRESS, DestinationType.ArkAddress).setZk()
            .build()
        coEvery { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) } returns listOf(payment)

        val qrText = "bitcoin:$BITCOIN_ADDRESS?branta_id=$ENCRYPTED_BITCOIN_ADDRESS&branta_secret=$SECRET&lightning=$BOLT11_INVOICE&ark=$ARK_ADDRESS"
        val result = service.getPaymentsByQrCode(qrText)

        val zkId = payment.destinations[0].zkId!!
        val bolt11ZkId = payment.destinations[1].zkId!!
        val arkZkId = payment.destinations[2].zkId!!
        assertEquals(1, result.payments.size)
        assertTrue(result.verifyUrl.contains("k-$zkId=$SECRET"))
        assertTrue(result.verifyUrl.contains("k-$bolt11ZkId=$bolt11Hash"))
        assertTrue(result.verifyUrl.contains("k-$arkZkId=$arkHash"))
        assertEquals(BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
        assertEquals(DECRYPTED_BOLT11, result.payments[0].destinations[1].value)
        verify { aesMock.decrypt(ENCRYPTED_BITCOIN_ADDRESS, SECRET) }
        verify { aesMock.decrypt(ENCRYPTED_BOLT11, bolt11Hash) }
    }

    // endregion

    // region getPayments

    @Test
    fun `getPayments returns payments when client succeeds`() = runTest {
        coEvery { clientMock.getPayments(BITCOIN_ADDRESS, null) } returns listOf(plainBitcoinPayment())

        val result = service.getPayments(BITCOIN_ADDRESS)

        assertEquals(1, result.payments.size)
        assertEquals(BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
    }

    @Test
    fun `getPayments returns empty list when client returns empty and sets verifyUrl`() = runTest {
        coEvery { clientMock.getPayments(any(), any()) } returns emptyList()

        val result = service.getPayments(BITCOIN_ADDRESS)

        assertTrue(result.payments.isEmpty())
        assertEquals("http://localhost:3000/v2/verify/$BITCOIN_ADDRESS", result.verifyUrl)
    }

    @Test
    fun `getPayments forwards options to client`() = runTest {
        coEvery { clientMock.getPayments(any(), defaultOptions) } returns listOf(plainBitcoinPayment())

        service.getPayments(BITCOIN_ADDRESS, options = defaultOptions)

        coVerify { clientMock.getPayments(any(), defaultOptions) }
    }

    @Test
    fun `getPayments ZK bitcoin address decrypts destination value`() = runTest {
        coEvery { clientMock.getPayments(any(), any()) } returns listOf(zkBitcoinPayment())

        val result = service.getPayments(ENCRYPTED_BITCOIN_ADDRESS, destinationEncryptionKey = SECRET)

        assertEquals(BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
        verify { aesMock.decrypt(ENCRYPTED_BITCOIN_ADDRESS, SECRET) }
    }

    @Test
    fun `getPayments ZK bitcoin address no key leaves encrypted`() = runTest {
        coEvery { clientMock.getPayments(any(), any()) } returns listOf(zkBitcoinPayment())

        val result = service.getPayments(ENCRYPTED_BITCOIN_ADDRESS, destinationEncryptionKey = null)

        assertEquals(ENCRYPTED_BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
        assertTrue(result.payments[0].destinations[0].isEncrypted)
        verify(exactly = 0) { aesMock.decrypt(any(), any()) }
    }

    @Test
    fun `getPayments ZK bitcoin address wrong key leaves encrypted`() = runTest {
        every { aesMock.decrypt(ENCRYPTED_BITCOIN_ADDRESS, "wrong-key") } throws Exception("Decryption failed: auth tag mismatch")
        coEvery { clientMock.getPayments(any(), any()) } returns listOf(zkBitcoinPayment())

        val result = service.getPayments(ENCRYPTED_BITCOIN_ADDRESS, destinationEncryptionKey = "wrong-key")

        assertEquals(ENCRYPTED_BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
        assertTrue(result.payments[0].destinations[0].isEncrypted)
    }

    @Test
    fun `getPayments non-ZK destination does not decrypt`() = runTest {
        coEvery { clientMock.getPayments(any(), any()) } returns listOf(plainBitcoinPayment())

        val result = service.getPayments(BITCOIN_ADDRESS, destinationEncryptionKey = SECRET)

        assertEquals(BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
        verify(exactly = 0) { aesMock.decrypt(any(), any()) }
    }

    @Test
    fun `getPayments ZK bolt11 decrypts using hash`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(zkBolt11Payment())

        val result = service.getPayments(BOLT11_INVOICE)

        assertEquals(1, result.payments.size)
        assertEquals(DECRYPTED_BOLT11, result.payments[0].destinations[0].value)
        coVerify { clientMock.getPayments(ENCRYPTED_BOLT11, any()) }
        verify { aesMock.decrypt(ENCRYPTED_BOLT11, bolt11Hash) }
    }

    @Test
    fun `getPayments ZK bolt11 with non-bolt11 value does not decrypt`() = runTest {
        val nonBolt11 = "not-a-bolt11-value"
        coEvery { clientMock.getPayments(nonBolt11, any()) } returns listOf(zkBolt11Payment())

        val result = service.getPayments(nonBolt11)

        assertEquals(ENCRYPTED_BOLT11, result.payments[0].destinations[0].value)
        verify(exactly = 0) { aesMock.decrypt(any(), any()) }
    }

    @Test
    fun `getPayments non-ZK bolt11 does not decrypt`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(plainBolt11Payment())

        val result = service.getPayments(BOLT11_INVOICE)

        assertEquals(BOLT11_INVOICE, result.payments[0].destinations[0].value)
        verify(exactly = 0) { aesMock.decrypt(any(), any()) }
    }

    @Test
    fun `getPayments plain bitcoin address sets verifyUrl`() = runTest {
        coEvery { clientMock.getPayments(BITCOIN_ADDRESS, null) } returns listOf(plainBitcoinPayment())

        val result = service.getPayments(BITCOIN_ADDRESS)

        assertEquals("http://localhost:3000/v2/verify/$BITCOIN_ADDRESS", result.verifyUrl)
    }

    @Test
    fun `getPayments ZK bitcoin sets verifyUrl with key fragment`() = runTest {
        val payment = zkBitcoinPayment()
        val zkId = payment.destinations[0].zkId!!
        coEvery { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) } returns listOf(payment)

        val result = service.getPayments(ENCRYPTED_BITCOIN_ADDRESS, destinationEncryptionKey = SECRET)

        assertEquals("http://localhost:3000/v2/verify/$ENCRYPTED_BITCOIN_ADDRESS#k-$zkId=$SECRET", result.verifyUrl)
    }

    @Test
    fun `getPayments ZK bolt11 sets verifyUrl with key fragment`() = runTest {
        val payment = zkBolt11Payment()
        val zkId = payment.destinations[0].zkId!!
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(payment)

        val result = service.getPayments(BOLT11_INVOICE)

        assertEquals("http://localhost:3000/v2/verify/$ENCRYPTED_BOLT11#k-$zkId=$bolt11Hash", result.verifyUrl)
    }

    @Test
    fun `getPayments loose mode bolt11 not found verifyUrl uses plain value`() = runTest {
        coEvery { clientMock.getPayments(any(), any()) } returns emptyList()

        val result = service.getPayments(BOLT11_INVOICE)

        assertTrue(result.payments.isEmpty())
        assertEquals("http://localhost:3000/v2/verify/$BOLT11_INVOICE", result.verifyUrl)
        coVerify { clientMock.getPayments(ENCRYPTED_BOLT11, any()) }
        coVerify { clientMock.getPayments(BOLT11_INVOICE, any()) }
    }

    // endregion

    // region addPayment

    @Test
    fun `addPayment plain destination does not encrypt`() = runTest {
        val payment = plainBitcoinPayment()
        coEvery { clientMock.postPayment(payment, any()) } returns plainBitcoinPayment()

        service.addPayment(payment)

        verify(exactly = 0) { aesMock.encrypt(any(), any(), any()) }
    }

    @Test
    fun `addPayment ZK bitcoin address encrypts with secret`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk().build()
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_BITCOIN_ADDRESS, type = DestinationType.BitcoinAddress, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        val result = service.addPayment(payment)

        verify { aesMock.encrypt(BITCOIN_ADDRESS, SECRET, false) }
        assertEquals(SECRET, result.secret)
        assertEquals(ENCRYPTED_BITCOIN_ADDRESS, payment.destinations[0].value)
    }

    @Test
    fun `addPayment ZK bolt11 encrypts with hash`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(BOLT11_INVOICE, DestinationType.Bolt11).setZk().build()
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_BOLT11, type = DestinationType.Bolt11, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        service.addPayment(payment)

        verify { aesMock.encrypt(BOLT11_INVOICE, bolt11Hash, true) }
        assertEquals(ENCRYPTED_BOLT11, payment.destinations[0].value)
    }

    @Test
    fun `addPayment ZK ark address encrypts with hash`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(ARK_ADDRESS, DestinationType.ArkAddress).setZk().build()
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_ARK_ADDRESS, type = DestinationType.ArkAddress, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        service.addPayment(payment)

        verify { aesMock.encrypt(ARK_ADDRESS, arkHash, true) }
    }

    @Test
    fun `addPayment ZK bitcoin sets verifyUrl with key fragment`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk().build()
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_BITCOIN_ADDRESS, type = DestinationType.BitcoinAddress, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        val result = service.addPayment(payment)

        assertEquals("http://localhost:3000/v2/verify/$ENCRYPTED_BITCOIN_ADDRESS#k-$zkId=$SECRET", result.verifyUrl)
    }

    @Test
    fun `addPayment returns generated secret`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk().build()
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_BITCOIN_ADDRESS, type = DestinationType.BitcoinAddress, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        assertEquals(SECRET, service.addPayment(payment).secret)
    }

    @Test
    fun `addPayment unsupported ZK type throws`() = runTest {
        val payment = PaymentBuilder()
            .addDestination("0xdeadbeef", DestinationType.TetherAddress).setZk().build()

        assertFailsWith<BrantaPaymentException> { service.addPayment(payment) }
        coVerify(exactly = 0) { clientMock.postPayment(any(), any()) }
    }

    // endregion

    // region isApiKeyValid

    @Test
    fun `isApiKeyValid returns true when client returns true`() = runTest {
        coEvery { clientMock.isApiKeyValid(any()) } returns true
        assertTrue(service.isApiKeyValid())
    }

    @Test
    fun `isApiKeyValid returns false when client returns false`() = runTest {
        coEvery { clientMock.isApiKeyValid(any()) } returns false
        assertFalse(service.isApiKeyValid())
    }

    @Test
    fun `isApiKeyValid forwards options to client`() = runTest {
        coEvery { clientMock.isApiKeyValid(defaultOptions) } returns true

        service.isApiKeyValid(options = defaultOptions)

        coVerify { clientMock.isApiKeyValid(defaultOptions) }
    }

    // endregion

    // region strict mode

    @Test
    fun `getPayments strict mode plain bitcoin address throws`() = runTest {
        assertFailsWith<BrantaPaymentException> { strictService.getPayments(BITCOIN_ADDRESS) }
        coVerify(exactly = 0) { clientMock.getPayments(any(), any()) }
    }

    @Test
    fun `getPayments strict mode encrypted bitcoin with secret decrypts destination`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) } returns listOf(zkBitcoinPayment())

        val result = strictService.getPayments(ENCRYPTED_BITCOIN_ADDRESS, destinationEncryptionKey = SECRET)

        assertEquals(BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
        assertFalse(result.payments[0].destinations[0].isEncrypted)
        coVerify { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) }
        verify { aesMock.decrypt(ENCRYPTED_BITCOIN_ADDRESS, SECRET) }
        verify(exactly = 0) { aesMock.encrypt(any(), any(), any()) }
    }

    @Test
    fun `getPayments strict mode encrypted bitcoin with secret sets verifyUrl with key fragment`() = runTest {
        val payment = zkBitcoinPayment()
        val zkId = payment.destinations[0].zkId!!
        coEvery { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) } returns listOf(payment)

        val result = strictService.getPayments(ENCRYPTED_BITCOIN_ADDRESS, destinationEncryptionKey = SECRET)

        assertEquals("http://localhost:3000/v2/verify/$ENCRYPTED_BITCOIN_ADDRESS#k-$zkId=$SECRET", result.verifyUrl)
    }

    @Test
    fun `getPayments strict mode encrypted bitcoin wrong key leaves encrypted`() = runTest {
        every { aesMock.decrypt(ENCRYPTED_BITCOIN_ADDRESS, "wrong-key") } throws Exception("Decryption failed: auth tag mismatch")
        coEvery { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) } returns listOf(zkBitcoinPayment())

        val result = strictService.getPayments(ENCRYPTED_BITCOIN_ADDRESS, destinationEncryptionKey = "wrong-key")

        assertEquals(ENCRYPTED_BITCOIN_ADDRESS, result.payments[0].destinations[0].value)
        assertTrue(result.payments[0].destinations[0].isEncrypted)
    }

    @Test
    fun `getPayments strict mode bolt11 does not throw uses encrypted lookup`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(zkBolt11Payment())

        strictService.getPayments(BOLT11_INVOICE)

        coVerify { clientMock.getPayments(ENCRYPTED_BOLT11, any()) }
    }

    @Test
    fun `getPayments strict mode ark does not throw uses encrypted lookup`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_ARK_ADDRESS, any()) } returns listOf(zkArkPayment())

        strictService.getPayments(ARK_ADDRESS)

        coVerify { clientMock.getPayments(ENCRYPTED_ARK_ADDRESS, any()) }
    }

    @Test
    fun `getPayments strict mode bolt11 no fallback to plain text`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns emptyList()
        coEvery { clientMock.getPayments(BOLT11_INVOICE, any()) } returns listOf(plainBolt11Payment())

        val result = strictService.getPayments(BOLT11_INVOICE)

        assertTrue(result.payments.isEmpty())
        assertEquals("http://localhost:3000/v2/verify/$ENCRYPTED_BOLT11", result.verifyUrl)
        coVerify { clientMock.getPayments(ENCRYPTED_BOLT11, any()) }
        coVerify(exactly = 0) { clientMock.getPayments(BOLT11_INVOICE, any()) }
    }

    @Test
    fun `getPaymentsByQrCode strict mode plain bitcoin URI returns empty`() = runTest {
        val result = strictService.getPaymentsByQrCode("bitcoin:$BITCOIN_ADDRESS")

        assertTrue(result.payments.isEmpty())
        assertEquals("http://localhost:3000/v2/verify/$BITCOIN_ADDRESS", result.verifyUrl)
        coVerify(exactly = 0) { clientMock.getPayments(any(), any()) }
    }

    @Test
    fun `getPaymentsByQrCode strict mode ZK bitcoin URI succeeds`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) } returns listOf(zkBitcoinPayment())

        val result = strictService.getPaymentsByQrCode("bitcoin:$BITCOIN_ADDRESS?branta_id=$ENCRYPTED_BITCOIN_ADDRESS&branta_secret=$SECRET")

        assertEquals(1, result.payments.size)
        coVerify { clientMock.getPayments(ENCRYPTED_BITCOIN_ADDRESS, any()) }
    }

    @Test
    fun `getPaymentsByQrCode strict mode lightning bolt11 URI succeeds`() = runTest {
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(plainBolt11Payment())

        strictService.getPaymentsByQrCode("lightning:$BOLT11_INVOICE")

        coVerify { clientMock.getPayments(ENCRYPTED_BOLT11, any()) }
    }

    @Test
    fun `addPayment strict mode plain destination throws`() = runTest {
        assertFailsWith<BrantaPaymentException> { strictService.addPayment(plainBitcoinPayment()) }
        coVerify(exactly = 0) { clientMock.postPayment(any(), any()) }
    }

    @Test
    fun `addPayment strict mode all ZK destinations succeeds`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk().build()
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_BITCOIN_ADDRESS, type = DestinationType.BitcoinAddress, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        strictService.addPayment(payment)

        coVerify(exactly = 1) { clientMock.postPayment(any(), any()) }
    }

    @Test
    fun `addPayment strict mode mixed destinations throws`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk()
            .addDestination(BOLT11_INVOICE, DestinationType.Bolt11)
            .build()

        assertFailsWith<BrantaPaymentException> { strictService.addPayment(payment) }
        coVerify(exactly = 0) { clientMock.postPayment(any(), any()) }
    }

    // endregion

    // region metadata / DEK envelope (addPayment)

    @Test
    fun `addPayment ZK destination with metadata encrypts DEK and metadata`() = runTest {
        val aesMock2 = mockk<IAesEncryption>()
        val genMock2 = mockk<ISecretGenerator>()
        every { genMock2.generate() } returnsMany listOf(DEK, SECRET)
        every { genMock2.deterministicNonce } returns false
        every { aesMock2.encrypt(PLAIN_METADATA, DEK, false) } returns ENCRYPTED_METADATA
        every { aesMock2.encrypt(DEK, SECRET, false) } returns ENCRYPTED_DEK_FOR_BITCOIN
        every { aesMock2.encrypt(BITCOIN_ADDRESS, SECRET, false) } returns ENCRYPTED_BITCOIN_ADDRESS

        val svc = BrantaService(defaultOptions, clientMock, aesMock2, genMock2)
        val payment = PaymentBuilder()
            .addDestination(BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk().build()
        payment.metadata = PLAIN_METADATA
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_BITCOIN_ADDRESS, type = DestinationType.BitcoinAddress, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        svc.addPayment(payment)

        verify { aesMock2.encrypt(PLAIN_METADATA, DEK, false) }
        assertEquals(ENCRYPTED_METADATA, payment.metadata)
        assertEquals(ENCRYPTED_DEK_FOR_BITCOIN, payment.destinations[0].encryptedDek)
    }

    @Test
    fun `addPayment ZK bolt11 with metadata sets encryptedDek with hash key`() = runTest {
        val aesMock2 = mockk<IAesEncryption>()
        val genMock2 = mockk<ISecretGenerator>()
        every { genMock2.generate() } returnsMany listOf(DEK, SECRET)
        every { genMock2.deterministicNonce } returns false
        every { aesMock2.encrypt(PLAIN_METADATA, DEK, false) } returns ENCRYPTED_METADATA
        every { aesMock2.encrypt(DEK, bolt11Hash, false) } returns ENCRYPTED_DEK_FOR_BOLT11
        every { aesMock2.encrypt(BOLT11_INVOICE, bolt11Hash, true) } returns ENCRYPTED_BOLT11

        val svc = BrantaService(defaultOptions, clientMock, aesMock2, genMock2)
        val payment = PaymentBuilder()
            .addDestination(BOLT11_INVOICE, DestinationType.Bolt11).setZk().build()
        payment.metadata = PLAIN_METADATA
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_BOLT11, type = DestinationType.Bolt11, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        svc.addPayment(payment)

        verify { aesMock2.encrypt(PLAIN_METADATA, DEK, false) }
        assertEquals(ENCRYPTED_METADATA, payment.metadata)
        assertEquals(ENCRYPTED_DEK_FOR_BOLT11, payment.destinations[0].encryptedDek)
    }

    @Test
    fun `addPayment non-ZK destination with metadata does not set encryptedDek`() = runTest {
        val payment = plainBitcoinPayment()
        payment.metadata = PLAIN_METADATA
        coEvery { clientMock.postPayment(payment, any()) } returns plainBitcoinPayment()

        service.addPayment(payment)

        assertEquals(PLAIN_METADATA, payment.metadata)
        assertNull(payment.destinations[0].encryptedDek)
        verify(exactly = 0) { aesMock.encrypt(PLAIN_METADATA, any(), any()) }
    }

    @Test
    fun `addPayment no metadata does not set encryptedDek`() = runTest {
        val payment = PaymentBuilder()
            .addDestination(BITCOIN_ADDRESS, DestinationType.BitcoinAddress).setZk().build()
        val zkId = payment.destinations[0].zkId!!
        val responsePayment = Payment(destinations = mutableListOf(
            Destination(value = ENCRYPTED_BITCOIN_ADDRESS, type = DestinationType.BitcoinAddress, isZk = true, zkId = zkId)
        ))
        coEvery { clientMock.postPayment(any(), any()) } returns responsePayment

        service.addPayment(payment)

        assertNull(payment.destinations[0].encryptedDek)
    }

    // endregion

    // region metadata / DEK envelope (getPayments decryption)

    @Test
    fun `getPayments ZK bolt11 with encryptedDek decrypts metadata`() = runTest {
        val payment = Payment(
            destinations = mutableListOf(
                Destination(value = ENCRYPTED_BOLT11, type = DestinationType.Bolt11, isZk = true, zkId = "zk1", encryptedDek = ENCRYPTED_DEK_VALUE)
            ),
            metadata = ENCRYPTED_METADATA_BLOB
        )
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(payment)
        every { aesMock.decrypt(ENCRYPTED_DEK_VALUE, bolt11Hash) } returns DECRYPTED_DEK
        every { aesMock.decrypt(ENCRYPTED_METADATA_BLOB, DECRYPTED_DEK) } returns DECRYPTED_METADATA

        val result = service.getPayments(BOLT11_INVOICE)

        assertEquals(DECRYPTED_METADATA, result.payments[0].metadata)
        assertTrue(result.payments[0].isMetadataDecrypted)
    }

    @Test
    fun `getPayments ZK destination without encryptedDek leaves metadata as-is`() = runTest {
        val payment = Payment(
            destinations = mutableListOf(
                Destination(value = ENCRYPTED_BOLT11, type = DestinationType.Bolt11, isZk = true, zkId = "zk1", encryptedDek = null)
            ),
            metadata = ENCRYPTED_METADATA_BLOB
        )
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(payment)

        val result = service.getPayments(BOLT11_INVOICE)

        assertEquals(ENCRYPTED_METADATA_BLOB, result.payments[0].metadata)
        assertFalse(result.payments[0].isMetadataDecrypted)
    }

    @Test
    fun `getPayments encryptedDek decryption failure leaves metadata as-is`() = runTest {
        val payment = Payment(
            destinations = mutableListOf(
                Destination(value = ENCRYPTED_BOLT11, type = DestinationType.Bolt11, isZk = true, zkId = "zk1", encryptedDek = ENCRYPTED_DEK_VALUE)
            ),
            metadata = ENCRYPTED_METADATA_BLOB
        )
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(payment)
        every { aesMock.decrypt(ENCRYPTED_DEK_VALUE, bolt11Hash) } throws Exception("decrypt failed")

        val result = service.getPayments(BOLT11_INVOICE)

        assertEquals(ENCRYPTED_METADATA_BLOB, result.payments[0].metadata)
        assertFalse(result.payments[0].isMetadataDecrypted)
    }

    @Test
    fun `getPayments multiple ZK destinations decrypts metadata only once`() = runTest {
        val payment = Payment(
            destinations = mutableListOf(
                Destination(value = ENCRYPTED_BOLT11, type = DestinationType.Bolt11, isZk = true, zkId = "zk1", encryptedDek = ENCRYPTED_DEK_VALUE),
                Destination(value = ENCRYPTED_ARK_ADDRESS, type = DestinationType.ArkAddress, isZk = true, zkId = "zk2", encryptedDek = "another-dek")
            ),
            metadata = ENCRYPTED_METADATA_BLOB
        )
        coEvery { clientMock.getPayments(ENCRYPTED_BOLT11, any()) } returns listOf(payment)
        every { aesMock.decrypt(ENCRYPTED_DEK_VALUE, bolt11Hash) } returns DECRYPTED_DEK
        every { aesMock.decrypt(ENCRYPTED_METADATA_BLOB, DECRYPTED_DEK) } returns DECRYPTED_METADATA

        val result = service.getPayments(BOLT11_INVOICE)

        verify(exactly = 1) { aesMock.decrypt(ENCRYPTED_METADATA_BLOB, DECRYPTED_DEK) }
        assertEquals(DECRYPTED_METADATA, result.payments[0].metadata)
    }

    // endregion
}
