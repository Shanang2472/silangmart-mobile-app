package com.example.data.ai

import com.example.data.model.CartAction
import com.example.data.model.Product
import com.example.data.model.formatPhp
import kotlinx.coroutines.delay

data class AiReply(
    val text: String,
    val cartActions: List<CartAction> = emptyList(),
    val isPreviewMode: Boolean = false
)

interface AiRepository {
    val isLiveGeminiEnabled: Boolean
    suspend fun generateReply(
        role: String,
        userName: String,
        userMessage: String,
        products: List<Product>,
        activeBranch: String
    ): AiReply
}

/**
 * Preview implementation used until Firebase AI Logic is confirmed and provisioned.
 * Once Firebase is provisioned, swap this with FirebaseAiRepository powered by Firebase.ai.
 */
class PreviewAiRepository : AiRepository {
    override val isLiveGeminiEnabled: Boolean = false

    override suspend fun generateReply(
        role: String,
        userName: String,
        userMessage: String,
        products: List<Product>,
        activeBranch: String
    ): AiReply {
        delay(300)
        val lower = userMessage.lowercase()
        val cartActions = if (role == "customer") detectOrderIntent(lower, products) else emptyList()

        val reply = when (role) {
            "admin" -> {
                val lowStock = products.filter { it.stockForBranch(activeBranch) <= it.lowStockThreshold }
                if (lower.contains("low") || lower.contains("stock") || lower.contains("imbentaryo")) {
                    if (lowStock.isEmpty()) {
                        "Magandang balita, Manager $userName! Walang low-stock items sa $activeBranch branch sa ngayon (${products.size} products active)."
                    } else {
                        "May ${lowStock.size} low-stock items sa $activeBranch: " +
                            lowStock.take(5).joinToString(", ") { "${it.name} (${it.stockForBranch(activeBranch)} ${it.unit})" } +
                            ". Buksan ang Stocks & Inventory para mag-Quick Restock."
                    }
                } else {
                    "Kamusta Manager $userName! Nakikita ko ang ${products.size} produkto sa $activeBranch branch. " +
                        "I-accept ang Firebase setup prompt para makakuha ng live Gemini AI business insights!"
                }
            }
            "rider" -> {
                "Kuya $userName, laging i-check ang payment status at customer coordinates bago umalis sa $activeBranch branch, at kumuha ng Proof of Delivery pagdating. " +
                    "(Preview mode — i-enable ang Firebase para sa live Gemini route assistant.)"
            }
            else -> {
                if (cartActions.isNotEmpty()) {
                    val summary = cartActions.joinToString(", ") { "${it.quantity} ${it.unitName} ng ${it.productName}" }
                    "Nahanap ko po ang $summary! Pindutin ang Add to Cart button sa ibaba para maisama sa inyong delivery bag."
                } else if (products.isNotEmpty()) {
                    val sample = products.take(3).joinToString(", ") { "${it.name} (${formatPhp(it.price)})" }
                    "Kumusta po $userName! Meron tayong $sample at iba pang bilihin sa $activeBranch branch. " +
                        "Puwede po kayong mag-order direkta sa Shop o sabihin ang gusto ninyong bilhin."
                } else {
                    "Kumusta po $userName! Ako si Ate Digy. Magpapa-load ng stocks ang admin sa Inventory para makapag-browse kayo ng mga produkto."
                }
            }
        }

        return AiReply(text = reply, cartActions = cartActions, isPreviewMode = true)
    }

    private fun detectOrderIntent(normalized: String, products: List<Product>): List<CartAction> {
        val orderRegex = Regex("\\b(order|add|lagay|ilagay|pabili|bili|bilhin|kuha|kunin|gusto)\\b|\\d+\\s*(?:kilos?|kgs?|kilo|pcs?|packs?|cans?)\\b")
        if (!orderRegex.containsMatchIn(normalized)) return emptyList()
        val qtyMatch = Regex("(\\d{1,2})\\s*(?:kilos?|kgs?|kilo|pcs?|packs?|cans?)?").find(normalized)
        val qty = qtyMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 99) ?: 1
        return products.filter { p ->
            normalized.contains(p.name.lowercase()) ||
                (p.tagalogName.isNotBlank() && normalized.contains(p.tagalogName.lowercase()))
        }.take(3).map { p ->
            CartAction(productId = p.id, quantity = qty, productName = p.name, unitName = p.unit)
        }
    }
}
