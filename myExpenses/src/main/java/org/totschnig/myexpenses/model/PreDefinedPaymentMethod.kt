package org.totschnig.myexpenses.model

import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R

enum class PreDefinedPaymentMethod(
    val paymentType: Int,
    val isNumbered: Boolean,
    val resId: Int,
    val icon: String
) {
    CHEQUE(-1, true, R.string.pm_cheque, "money-check"),
    CREDITCARD(-1, false, R.string.pm_creditcard, "credit-card"),
    DEPOSIT(1, false, R.string.pm_deposit, "money-bill-transfer"),
    DIRECTDEBIT(-1, false, R.string.pm_directdebit, "money-bill-transfer");

    //TODO use proper context
    val localizedLabel: String
        get() =//TODO use proper context
            MyApplication.getInstance().getString(resId)
}