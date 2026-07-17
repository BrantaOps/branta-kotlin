package pro.branta.v2

import pro.branta.v2.interfaces.ISecretGenerator
import java.util.UUID

class GuidSecretGenerator : ISecretGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
    override val deterministicNonce: Boolean = false
}
