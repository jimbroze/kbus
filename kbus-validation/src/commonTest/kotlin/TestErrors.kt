package com.jimbroze.kbus.validation

sealed interface CustomerIdError {
    data object Blank : CustomerIdError

    data class TooShort(val minLength: Int) : CustomerIdError

    data class TooLong(val maxLength: Int) : CustomerIdError

    data object WrongLength : CustomerIdError

    data object NotUppercase : CustomerIdError
}

sealed interface PaymentMethodIdError {
    data object NotAPaymentMethodId : PaymentMethodIdError
}

sealed interface QuantityError {
    data object NoneOrdered : QuantityError

    data class TooFew(val minimum: Int) : QuantityError

    data object OverOrderLimit : QuantityError

    data class TooMany(val maximum: Int) : QuantityError
}

sealed interface PriceInPenceError {
    data object Free : PriceInPenceError

    data class NotAbove(val exclusiveMinimum: Int) : PriceInPenceError
}

sealed interface DiscountPercentError {
    data object WholePrice : DiscountPercentError

    data class NotBelow(val exclusiveMaximum: Int) : DiscountPercentError
}

sealed interface DeliveryWindowError {
    data object EarliestAfterLatest : DeliveryWindowError
}
