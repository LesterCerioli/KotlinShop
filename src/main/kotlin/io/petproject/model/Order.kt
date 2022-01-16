package io.petproject.model

import io.petproject.model.ProductType.DIGITAL
import io.petproject.model.ProductType.PHYSICAL
import io.petproject.model.ProductType.PHYSICAL_TAX_FREE
import io.petproject.model.ProductType.SUBSCRIPTION
import java.math.BigDecimal
import java.math.RoundingMode

interface Order {
    val items: List<Item>
    val feesAndDiscounts: Map<String, BigDecimal>
    val account: Account

    var paymentMethod: PaymentMethod
    var status: OrderStatus
    val type: OrderType

    fun subtotal(): BigDecimal {
        return items
            .map(Item::subtotal)
            .fold(BigDecimal.ZERO) { acc, value -> acc.plus(value) }
            .setScale(2, RoundingMode.HALF_UP)
    }

    fun feesAndDiscounts(): BigDecimal {
        return feesAndDiscounts
            .values
            .fold(BigDecimal.ZERO) { acc, value -> acc.plus(value) }
            .setScale(2, RoundingMode.HALF_UP)
    }

    fun grandTotal(): BigDecimal {
        return subtotal().plus(feesAndDiscounts())
    }

    fun withPaymentMethod(paymentMethod: PaymentMethod) = apply {
        this.paymentMethod = paymentMethod
    }

    fun place() = apply {
        require(items.isNotEmpty()) {
            "There must be at least one item to place the Order"
        }
    }

    fun pay() = apply {
        check(status.code >= OrderStatus.PENDING.code) {
            "Order must be placed before it can be payed"
        }
        check(status.code < OrderStatus.NOT_SHIPPED.code) {
            "Order Payment has been processed already"
        }
    }

    fun invoice(): Invoice {
        check((status.code < OrderStatus.NOT_SHIPPED.code).not()) {
            "Invoice can only be generated after payment is complete"
        }
        return Invoice(this)
    }

    fun fulfill() = apply {
        check(status.code >= OrderStatus.NOT_SHIPPED.code) {
            "Order must be placed and payed before it can be fulfilled"
        }
        check(status.code < OrderStatus.SHIPPED.code) {
            "Order Fulfillment has been processed already"
        }
    }

    fun complete() = apply {
        check(status.code >= OrderStatus.SHIPPED.code) {
            "Order must have been shipped/sent and confirmed, before it can be completed"
        }
        check(status.code < OrderStatus.DELIVERED.code) {
            "Order has been delivered already"
        }
    }
}

data class PhysicalOrder(override val items: List<Item>, override val account: Account) : Order {

    override val feesAndDiscounts = HashMap<String, BigDecimal>()
    override lateinit var paymentMethod: PaymentMethod
    override lateinit var status: OrderStatus

    override val type: OrderType = OrderType.PHYSICAL
    lateinit var shippingAddress: Address

    val parcels: () -> List<Parcel> = { Parcel.breakdown(items, shippingAddress) }

    init {
        require(items.none { it.product.type != PHYSICAL && it.product.type != PHYSICAL_TAX_FREE }) {
            "A Physical Order may only contain Physical items"
        }
    }
    fun withShippingAddress(address: Address) = apply { this.shippingAddress = address }

    override fun withPaymentMethod(paymentMethod: PaymentMethod) = apply {
        super.withPaymentMethod(paymentMethod)
    }

    override fun place() = apply {
        require(this::shippingAddress.isInitialized) {
            "Shipping Address must be informed for Orders with physical delivery"
        }
        require(this::paymentMethod.isInitialized) {
            "A Payment method must be informed to place the Order"
        }
        super.place()
        this.feesAndDiscounts["shippingAndHandling"] = Parcel.shippingCostsOf(parcels())
        this.status = OrderStatus.PENDING
    }

    override fun pay() = apply {
        check(this::status.isInitialized) { "Order must be placed before it can be payed" }
        super.pay()
        this.status = OrderStatus.NOT_SHIPPED
    }

    override fun fulfill() = apply {
        super.fulfill()
        this.status = OrderStatus.SHIPPED
    }

    override fun complete() = apply {
        super.complete()
        this.status = OrderStatus.DELIVERED
    }
}

data class DigitalOrder(override val items: List<Item>, override val account: Account) : Order {

    override val feesAndDiscounts = HashMap<String, BigDecimal>()
    override lateinit var paymentMethod: PaymentMethod
    override lateinit var status: OrderStatus

    override val type = OrderType.DIGITAL

    init {
        require(items.none { it.product.type != DIGITAL }) {
            "A Digital Order may only contain Digital items"
        }
    }

    override fun withPaymentMethod(paymentMethod: PaymentMethod) = apply {
        super.withPaymentMethod(paymentMethod)
    }

    override fun place() = apply {
        require(this::paymentMethod.isInitialized) {
            "A Payment method must be informed to place the Order"
        }
        super.place()
        this.feesAndDiscounts["Voucher"] = BigDecimal("-10")
        this.status = OrderStatus.PENDING
    }

    override fun pay() = apply {
        check(this::status.isInitialized) { "Order must be placed before it can be payed" }
        super.pay()
        this.status = OrderStatus.UNSENT
    }

    override fun fulfill() = apply {
        super.fulfill()
        this.status = OrderStatus.SENT
    }

    override fun complete() = apply {
        super.complete()
        this.status = OrderStatus.REDEEMED
    }
}

data class SubscriptionOrder(override val items: List<Item>,
                             override val account: Account) : Order {

    constructor(item: Item, account: Account) : this(listOf(item), account)

    override val feesAndDiscounts = HashMap<String, BigDecimal>()
    override lateinit var paymentMethod: PaymentMethod
    override lateinit var status: OrderStatus

    override val type = OrderType.SUBSCRIPTION

    init {
        require(items.none() { it.product.type != SUBSCRIPTION }) {
            "A Membership Order may only contain Membership items"
        }
        require(items.count() == 1) {
            "A Membership Order may only contain one Membership subscription"
        }
    }

    override fun withPaymentMethod(paymentMethod: PaymentMethod) = apply {
        super.withPaymentMethod(paymentMethod)
    }

    override fun place() = apply {
        require(this::paymentMethod.isInitialized) {
            "A Payment method must be informed to place the Order"
        }
        super.place()
        this.status = OrderStatus.PENDING
    }

    override fun pay() = apply {
        check(this::status.isInitialized) {
            "Order must be placed before it can be payed"
        }
        super.pay()
        this.status = OrderStatus.PENDING_ACTIVATION
    }

    override fun fulfill() = apply {
        super.fulfill()
        this.status = OrderStatus.ACTIVATED
    }

    override fun complete() = apply {}
}

enum class OrderType {
    PHYSICAL,
    DIGITAL,
    SUBSCRIPTION
}

enum class OrderStatus(val code: Int = 0) {
    PENDING(100),
    NOT_SHIPPED(200),
    UNSENT(200),
    PENDING_ACTIVATION(200),
    SHIPPED(300),
    SENT(300),
    DELIVERED(400),
    REDEEMED(400),
    ACTIVATED(400)
}
