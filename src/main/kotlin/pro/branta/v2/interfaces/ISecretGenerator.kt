package pro.branta.v2.interfaces

interface ISecretGenerator {
    fun generate(): String
    val deterministicNonce: Boolean
}
