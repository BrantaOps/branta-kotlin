package pro.branta

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import pro.branta.enums.BrantaEnvironment
import pro.branta.enums.VerifyStatus

class ExampleQrCodesTest {
    private fun service(environment: BrantaEnvironment): BrantaService {
        val token = System.getenv("BRANTA_INTEGRATION_METRICS_TOKEN")
        val httpClient = if (token.isNullOrEmpty()) {
            OkHttpClient()
        } else {
            OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-Branta-Integration-Token", token).build())
            }.build()
        }
        return BrantaService(environment = environment, apiKey = System.getenv("BRANTA_API_KEY"), httpClient = httpClient)
    }

    @Test fun `production on-chain address returns unknown`() = expect(BrantaEnvironment.Production, PROD_ON_CHAIN, VerifyStatus.Unknown)
    @Test fun `production lightning returns a match`() = expect(BrantaEnvironment.Production, PROD_LIGHTNING, VerifyStatus.Match)
    @Test fun `production branta on-chain returns a match`() = expect(BrantaEnvironment.Production, PROD_BRANTA_ON_CHAIN, VerifyStatus.Match)
    @Test fun `production branta lightning returns a match`() = expect(BrantaEnvironment.Production, PROD_BRANTA_LIGHTNING, VerifyStatus.Match)
    @Test fun `production branta unified returns a match`() = expect(BrantaEnvironment.Production, PROD_BRANTA_UNIFIED, VerifyStatus.Match)
    @Test fun `production not found returns unknown`() = expect(BrantaEnvironment.Production, NOT_FOUND, VerifyStatus.Unknown)
    @Test fun `staging on-chain address returns unknown`() = expect(BrantaEnvironment.Staging, STAGING_ON_CHAIN, VerifyStatus.Unknown)
    @Test fun `staging bolt11 returns a match`() = expect(BrantaEnvironment.Staging, STAGING_BOLT11, VerifyStatus.Match)
    @Test fun `staging branta on-chain returns a match`() = expect(BrantaEnvironment.Staging, STAGING_BRANTA_ON_CHAIN, VerifyStatus.Match)
    @Test fun `staging ark returns a match`() = expect(BrantaEnvironment.Staging, STAGING_ARK, VerifyStatus.Match)
    @Test fun `staging silent payment returns a match`() = expect(BrantaEnvironment.Staging, STAGING_SILENT, VerifyStatus.Match)
    @Test fun `staging branta unified returns a match`() = expect(BrantaEnvironment.Staging, STAGING_UNIFIED, VerifyStatus.Match)
    @Test fun `staging testnet returns a match`() = expect(BrantaEnvironment.Staging, STAGING_TESTNET, VerifyStatus.Match)
    @Test fun `staging usdt polygon returns a match`() = expect(BrantaEnvironment.Staging, STAGING_USDT_POLYGON, VerifyStatus.Match)
    @Test fun `staging usdc polygon returns a match`() = expect(BrantaEnvironment.Staging, STAGING_USDC_POLYGON, VerifyStatus.Match)
    @Test fun `staging bare 0x returns unknown`() = expect(BrantaEnvironment.Staging, STAGING_BARE_OX, VerifyStatus.Unknown)
    @Test fun `staging not found returns unknown`() = expect(BrantaEnvironment.Staging, NOT_FOUND, VerifyStatus.Unknown)
    @Test fun `staging tampered returns tampered`() = expect(BrantaEnvironment.Staging, STAGING_TAMPERED, VerifyStatus.Tampered)

    private fun expect(environment: BrantaEnvironment, input: String, status: VerifyStatus) = runBlocking {
        assertEquals(status, service(environment).verify(input).status)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun skipUnlessOptIn() {
            assumeTrue(System.getenv("BRANTA_RUN_INTEGRATION") == "1", "Live lookups skipped unless BRANTA_RUN_INTEGRATION=1")
        }

        private const val PROD_ON_CHAIN = "bitcoin:bc1qu3k6geqdjncaarsu2vq56tt8php5vsug9kasmq"
        private const val PROD_LIGHTNING = "lightning:lnbc17760n1p4r4tqupp5yuapqmxldkc8smuwa6t8shkdg9gezulu0vc7htepfsvweph8kqfsdphgfexzmn5vysygetkv4kx7ur9wgsyc6t8dp6xu6twvusy27rpd4cxcegcqzzsxq97zvuqsp53564rg6w4xjqy7jamcfqxyy83a0j8nzfs0wpevs37t5ln49q6hrs9qxpqysgq47hpqmv34g25le8sceq9jdvul2nz7ucyu0vucv56nlfe40x7n3jsu8duxjrn6tgvdspt872crk9zeatafznm9c57m039z7wyx6g3njsqkchkdh"
        private const val PROD_BRANTA_ON_CHAIN = "bitcoin:bc1q6745z6cy3u0k9nprurh3x804c4r7u3u8vxca2n?branta_id=Qy4e3KJ9yRs%2BVWq59aSTC6o%2F6vbCnw0v7ZutEs1mxjpGM2i5DgVVl7G0TwFO9CqQ1ws3zNqogQu%2B2%2Bx3U%2BQ8%2FSZtwgjezw%3D%3D&branta_secret=f9b0fd20-b402-4cd2-b68f-3ae3db52dbf5"
        private const val PROD_BRANTA_LIGHTNING = "lightning:lnbc17760n1p4r4flypp5k56kq3v2935rl3glkqu9vngfueud2zj87hjcff3t0kn0yrge0pfqdzjgfexzmn5vysz6gzyv4mx2mr0wpjhygzvd9nksarwd9hxwgz6v4ex7gztdehhwmr9v3nk2gz90psk6urvv5cqzzsxq97zvuqsp5hut3t0l0s5mvp9yr06v4253kqtf452z6c65s6g9sga445hc03v6s9qxpqysgqqm430zkk9uymjgvllr3aha88hc6q59etxasfqswn8r8pfm3dstlpp46azv906xtcj3wzprxup5fxn65a5wymt7zzq9sw9qdzx8rgdhcpk80nrg"
        private const val PROD_BRANTA_UNIFIED = "bitcoin:bc1qu3k6geqdjncaarsu2vq56tt8php5vsug9kasmq?lightning=lnbc17760n1p4r4tqupp5yuapqmxldkc8smuwa6t8shkdg9gezulu0vc7htepfsvweph8kqfsdphgfexzmn5vysygetkv4kx7ur9wgsyc6t8dp6xu6twvusy27rpd4cxcegcqzzsxq97zvuqsp53564rg6w4xjqy7jamcfqxyy83a0j8nzfs0wpevs37t5ln49q6hrs9qxpqysgq47hpqmv34g25le8sceq9jdvul2nz7ucyu0vucv56nlfe40x7n3jsu8duxjrn6tgvdspt872crk9zeatafznm9c57m039z7wyx6g3njsqkchkdh&branta_id=%2BbcErHkjD6aDfvhegd1CPRgQQwes64aMIqPXCRBVKP88d982zJt7TUHTq%2B11O9ij9HFzXC%2BTvh%2ByFoECrvZKpv%2FVuGGLIA%3D%3D&branta_secret=c4ba0501-059e-459b-9b11-64d97f158291"
        private const val NOT_FOUND = "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
        private const val STAGING_ON_CHAIN = "bitcoin:bc1qgw3dzmhnyvcswc9r0v0z0ajtp8ulm4nuyeahwr"
        private const val STAGING_BOLT11 = "lightning:lnbc10u1p4w23tzpp50uc6g4020tt3ffp5xfpp83k0lcv2spytpymuupqrnsj0eth8jqgsdqhgfexzmn5vys8xarpva5kuecxq97zvuqsp5tj2a5fd84lhdddzfqz2lwmjqg7n9gp28yy5vtqd78muyzdt5tmsq9qypsqqcqpf6yv66fkcq609xzk0fcqx82hd7fsjhwrzj8xmq743v5lrtssrj95r94hyeumqeejgqvtxqy7ks7ec0uc5lc5zxg240xtwgzjtwkmh3aqqm8gyny"
        private const val STAGING_BRANTA_ON_CHAIN = "bitcoin:bc1qgw3dzmhnyvcswc9r0v0z0ajtp8ulm4nuyeahwr?branta_id=6SpYCe23r79x0t8rNvmjWqhrg8hen4xz%2FYSXhNJ9HLSYrTRPnm%2BSwAuf6NOKobnwE%2B3MQBNkyTnCtIWpKKX2E9ZUWVedig%3D%3D&branta_secret=05eb145d-b5cb-452a-9256-0bdfd9a15ffa"
        private const val STAGING_ARK = "ark1qztwuhx7vpytv4dcfeqjsepx3kxstnarelmry2smrfdgt2dv87fas7gtqkk"
        private const val STAGING_SILENT = "sp1qqgste7k9hx0qftg6qmwlkqtwuy6cycyavzmzj85c6qdfhjdpdjtdgqjuexzk6murw56suy3e0rd2cgqvycxttddwsvgxe2usfpxumr70xc9pkqwv"
        private const val STAGING_UNIFIED = "bitcoin:bc1q6745z6cy3u0k9nprurh3x804c4r7u3u8vxca2n?lightning=lnbc10u1p4w23tzpp5s6jc8gvun27un7g7q9kmh2vak78r83766heesd9fepa5s56hrpsqdqhgfexzmn5vys8xarpva5kuecxq97zvuqsp5gt4k9wwdgf85g066a05zrnlkh3jc2ng3mh9mw6yw7z03l4kmqzys9qypsqqcqpf7pk6xjfksy8sqhv24es2qgtp0e88wvt2gdv2zceealhjwh0memhzktgnfhdvzc9a6kt74w5jf5c8v9t20k38snawrgngr0sjw9a58fqqn9lwtq&branta_id=NxkrlFgIvGg6f8YZmFAf8qTUza8nXfn%2Bb9H3W7czFAo5Lp4cSpgRSmJgx7P9vaOYu7lkP6XXKvTshQfkXxxRrxJ%2BE7zdpA%3D%3D&branta_secret=6acf2637-511a-4b89-ba2c-f5914d7e669c"
        private const val STAGING_TESTNET = "bitcoin:tb1qshra2mlujc9wfscvn4d8aqpyqgxzl55853xg7s?branta_id=fa05KT1L6zIAYx2sHcImF3R5RilwMAXAvx%2F5u2YVBIdvnp30drV7bszFzvpdoA%2FeluN0PXbsxcsD38twzw4%2BL8MFoHxkGQ%3D%3D&branta_secret=0621110e-1ad6-483d-80b6-78697c9f6896"
        private const val STAGING_USDT_POLYGON = "pol:0x278297528c32eCa01295B0d2135984d3442ec963?value=50&user=send&currency=USD&branta_id=4WYE9J4TjNitdX7AL%2Fw7frpYdKGwSKHCS5zpFXl%2FlQGwpsQi4%2Fgv5MxgujPFzYMbIKqg2rtyYGpzJCllEA6Qudbi7lLkiA%3D%3D&branta_secret=c0fab1ee-1526-4efa-b991-9ee138a44cd2"
        private const val STAGING_USDC_POLYGON = "pol:0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B?value=50&user=send&currency=USD&branta_id=8rnGGWZcg%2Fg3He1OK6yZ6OLOEyusldwIDaoPImRzWjoUGguyXKotuF8ZlGbCZN9TRqx3PdXjvHDdwffdVvIZxmZ00xJvxA%3D%3D&branta_secret=0515039f-2c31-4467-b62a-4e919079a44e"
        private const val STAGING_BARE_OX = "0x278297528c32eCa01295B0d2135984d3442ec963"
        private const val STAGING_TAMPERED = "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?branta_id=6SpYCe23r79x0t8rNvmjWqhrg8hen4xz%2FYSXhNJ9HLSYrTRPnm%2BSwAuf6NOKobnwE%2B3MQBNkyTnCtIWpKKX2E9ZUWVedig%3D%3D&branta_secret=05eb145d-b5cb-452a-9256-0bdfd9a15ffa"
    }
}
