package io.github.mhmmtbg.mobileillustrator.platform

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.mhmmtbg.mobileillustrator.BuildConfig
import io.github.mhmmtbg.mobileillustrator.model.tr

/**
 * "Geliştiriciye kahve ısmarla": Google Play'de tek seferlik, tüketilmeyen bir ürün. Satın alınınca
 * reklamlar kalıcı olarak kalkar; satın alma Google hesabına bağlıdır ve yeni cihazda kendiliğinden geri gelir.
 *
 * Fiyat Play Console'da belirlenir; burada yalnızca mağazadan gelen fiyat metni gösterilir.
 */
class Monetization(private val app: Application) : PurchasesUpdatedListener {
    private val prefs = app.getSharedPreferences("monetization", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    /** Kahve ısmarlandı mı. Son bilinen durum saklanır; çevrimdışıyken de reklam gösterilmez. */
    var purchased by mutableStateOf(prefs.getBoolean(KEY_PURCHASED, false))
        private set

    /** Mağazadaki fiyat metni; mağazaya ulaşılamadıysa `null`. */
    var price by mutableStateOf<String?>(null)
        private set

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Mağazaya bağlanır, fiyatı ve önceki satın almayı sorgular. Yinelenen çağrılar zararsızdır. */
    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                }

                override fun onBillingServiceDisconnected() {
                    // Bağlantı gerektiğinde kendiliğinden yeniden kurulur.
                }
            })
        } catch (e: RuntimeException) {
            // Google Play olmayan cihaz: satın alma kullanılamaz, uygulama etkilenmez.
        }
    }

    private fun refresh() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BuildConfig.COFFEE_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, response ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val found = response.productDetailsList.firstOrNull()
                main.post {
                    details = found
                    price = found?.oneTimePurchaseOfferDetails?.formattedPrice
                }
            }
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // Mağazanın yanıtı esastır: iade edilen satın almada reklamlar geri gelir.
                val owned = accept(purchases)
                main.post { markPurchased(owned) }
            }
        }
    }

    /** Tamamlanmış satın almaları onaylar (onaylanmayan satın alma üç gün içinde iade edilir). */
    private fun accept(purchases: List<Purchase>): Boolean {
        var owned = false
        for (purchase in purchases) {
            if (BuildConfig.COFFEE_PRODUCT_ID !in purchase.products) continue
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            owned = true
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
            }
        }
        return owned
    }

    private fun markPurchased(value: Boolean) {
        if (purchased == value) return
        purchased = value
        prefs.edit().putBoolean(KEY_PURCHASED, value).apply()
    }

    /** Satın alma penceresini açar. */
    fun buy(activity: Activity) {
        val product = details
        if (product == null || !client.isReady) {
            start()
            toast(tr("Mağazaya ulaşılamadı. Satın alma için uygulamanın Google Play'den kurulmuş olması gerekir."))
            return
        }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refresh()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases ?: return
                val owned = accept(list)
                val pending = list.any { BuildConfig.COFFEE_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }
                main.post {
                    if (owned) {
                        markPurchased(true)
                        toast(tr("Kahven için teşekkürler! Reklamlar kaldırıldı."))
                    } else if (pending) {
                        toast(tr("Ödeme onaylanınca reklamlar kaldırılacak."))
                    }
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> main.post { toast(tr("Satın alma tamamlanamadı.")) }
        }
    }

    private fun toast(text: String) = Toast.makeText(app, text, Toast.LENGTH_LONG).show()

    private companion object {
        const val KEY_PURCHASED = "coffee"
    }
}
