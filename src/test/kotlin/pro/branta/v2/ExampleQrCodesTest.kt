package pro.branta.v2

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pro.branta.BrantaClientOptions
import pro.branta.enums.BrantaServerBaseUrl
import pro.branta.enums.PrivacyMode

/**
 * Integration tests that call the real Branta servers.
 * Requires BRANTA_API_KEY to be set as an environment variable for POST tests.
 * GET / lookup tests do not require an API key.
 */
class ExampleQrCodesTest {

    private fun makeService(baseUrl: BrantaServerBaseUrl, privacy: PrivacyMode) =
        BrantaService(BrantaClientOptions(baseUrl = baseUrl, defaultApiKey = apiKey, privacy = privacy))

    companion object {
        private val apiKey: String? = System.getenv("BRANTA_API_KEY")

        // Production QR codes
        private const val PROD_ON_CHAIN = "bitcoin:bc1qu3k6geqdjncaarsu2vq56tt8php5vsug9kasmq"
        private const val PROD_LIGHTNING = "lightning:lnbc17760n1p4r4tqupp5yuapqmxldkc8smuwa6t8shkdg9gezulu0vc7htepfsvweph8kqfsdphgfexzmn5vysygetkv4kx7ur9wgsyc6t8dp6xu6twvusy27rpd4cxcegcqzzsxq97zvuqsp53564rg6w4xjqy7jamcfqxyy83a0j8nzfs0wpevs37t5ln49q6hrs9qxpqysgq47hpqmv34g25le8sceq9jdvul2nz7ucyu0vucv56nlfe40x7n3jsu8duxjrn6tgvdspt872crk9zeatafznm9c57m039z7wyx6g3njsqkchkdh"
        private const val PROD_ZK_ON_CHAIN = "bitcoin:bc1q6745z6cy3u0k9nprurh3x804c4r7u3u8vxca2n?branta_id=z15b5EsbP5LHJrFco38%2BFp%2BHVaiopAY676NCKek8e1Q%2B4a370TyYhvloS8uLCUHfJ4CzeI%2FbOFmFDGpAQszB0gu1pJ1HOQ%3D%3D&branta_secret=c6e9eb30-6258-4432-9847-bdcc4fd4b0db"
        private const val PROD_ZK_LIGHTNING = "lightning:lnbc17760n1p4r4flypp5k56kq3v2935rl3glkqu9vngfueud2zj87hjcff3t0kn0yrge0pfqdzjgfexzmn5vysz6gzyv4mx2mr0wpjhygzvd9nksarwd9hxwgz6v4ex7gztdehhwmr9v3nk2gz90psk6urvv5cqzzsxq97zvuqsp5hut3t0l0s5mvp9yr06v4253kqtf452z6c65s6g9sga445hc03v6s9qxpqysgqqm430zkk9uymjgvllr3aha88hc6q59etxasfqswn8r8pfm3dstlpp46azv906xtcj3wzprxup5fxn65a5wymt7zzq9sw9qdzx8rgdhcpk80nrg"
        private const val NOT_FOUND = "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"

        // Staging QR codes
        private const val STAGING_ON_CHAIN = "bitcoin:bc1qgw3dzmhnyvcswc9r0v0z0ajtp8ulm4nuyeahwr"
        private const val STAGING_LIGHTNING = "lightning:lnbc25830n1p4quq9ppp5zszvpgxtu6uwyur6sf7rayc0meqprqlkv30xjzclh6nzm7gavd8sdzh2d6xzemfdenjqsnjv9h8gcfq95sygetkv4kx7ur9wgsyc6t8dp6xu6twvusy27rpd4cxcefq9pfhgct8d9hxw2gcqzzsxqzursp5fcfx5st7x8rgxra42j47hskmzkcz96mx84xcnvs9lpsmjyzqhw2q9qxpqysgq06lxdc93jjpuqsal9unlfct6wuv0v53yxa8kksl85g3qdw7qks7z9jkq39c6wgzar72luwd38sfj0klyqv0zgns4rq7nafnd8qeuudcqql7at4"
        private const val STAGING_ZK_ON_CHAIN = PROD_ZK_ON_CHAIN
        private const val STAGING_ZK_LIGHTNING = "lightning:lnbc25840n1p4qml83pp5aztzddx4k87m0wkd6wmgxr9753400mcj7sa89sa392krmueqv9qqdz92d6xzemfdenjqsnjv9h8gcfq95s9xarpva5kueeqtf9jqsn0d36zqvf3ypzhsctdwpkx2cqzzsxqzursp5c6dt82gqpn5vucmqtctur0p3cuur6xqgc6348wtz7adtgug9uf2q9qxpqysgq5yt6x946w3664th4h02pug9yhgszpznqyfwzndjk2sxe0878slqkdhgce4mr5ky2ux4gy4yt0vsy536tencls8fvu5wdzyaq548yf4qqu0lyg7"
    }

    // region Production — Loose

    @Test
    fun `production loose - on-chain returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Loose).getPaymentsByQrCode(PROD_ON_CHAIN)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `production loose - lightning returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Loose).getPaymentsByQrCode(PROD_LIGHTNING)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `production loose - ZK on-chain returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Loose).getPaymentsByQrCode(PROD_ZK_ON_CHAIN)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `production loose - ZK lightning returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Loose).getPaymentsByQrCode(PROD_ZK_LIGHTNING)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `production loose - not found returns empty`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Loose).getPaymentsByQrCode(NOT_FOUND)
        assertTrue(result.payments.isEmpty())
    }

    // endregion

    // region Production — Strict

    @Test
    fun `production strict - plain on-chain returns empty`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Strict).getPaymentsByQrCode(PROD_ON_CHAIN)
        assertTrue(result.payments.isEmpty())
    }

    @Test
    fun `production strict - plain lightning returns empty`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Strict).getPaymentsByQrCode(PROD_LIGHTNING)
        assertTrue(result.payments.isEmpty())
    }

    @Test
    fun `production strict - ZK on-chain returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Strict).getPaymentsByQrCode(PROD_ZK_ON_CHAIN)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `production strict - ZK lightning returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Production, PrivacyMode.Strict).getPaymentsByQrCode(PROD_ZK_LIGHTNING)
        assertTrue(result.payments.isNotEmpty())
    }

    // endregion

    // region Staging — Loose

    @Test
    fun `staging loose - on-chain returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Staging, PrivacyMode.Loose).getPaymentsByQrCode(STAGING_ON_CHAIN)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `staging loose - lightning returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Staging, PrivacyMode.Loose).getPaymentsByQrCode(STAGING_LIGHTNING)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `staging loose - ZK on-chain returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Staging, PrivacyMode.Loose).getPaymentsByQrCode(STAGING_ZK_ON_CHAIN)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `staging loose - ZK lightning returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Staging, PrivacyMode.Loose).getPaymentsByQrCode(STAGING_ZK_LIGHTNING)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `staging loose - not found returns empty`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Staging, PrivacyMode.Loose).getPaymentsByQrCode(NOT_FOUND)
        assertTrue(result.payments.isEmpty())
    }

    // endregion

    // region Staging — Strict

    @Test
    fun `staging strict - plain on-chain returns empty`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Staging, PrivacyMode.Strict).getPaymentsByQrCode(STAGING_ON_CHAIN)
        assertTrue(result.payments.isEmpty())
    }

    @Test
    fun `staging strict - ZK on-chain returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Staging, PrivacyMode.Strict).getPaymentsByQrCode(STAGING_ZK_ON_CHAIN)
        assertTrue(result.payments.isNotEmpty())
    }

    @Test
    fun `staging strict - ZK lightning returns payment`() = runBlocking {
        val result = makeService(BrantaServerBaseUrl.Staging, PrivacyMode.Strict).getPaymentsByQrCode(STAGING_ZK_LIGHTNING)
        assertTrue(result.payments.isNotEmpty())
    }

    // endregion
}
