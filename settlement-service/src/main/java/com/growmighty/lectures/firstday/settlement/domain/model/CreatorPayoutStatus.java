// TODO(settlement-plan): Retain only payout eligibility states used by the domain and map provider states in adapters.
package com.growmighty.lectures.firstday.settlement.domain.model;

public enum CreatorPayoutStatus {
    REGISTRATION_PENDING,
    APPROVAL_REQUIRED,
    KYC_REQUIRED,
    PAYOUT_READY,
    PAYOUT_UNAVAILABLE
}
