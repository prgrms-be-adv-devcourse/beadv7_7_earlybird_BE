package com.growmighty.lectures.firstday.settlement.application.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationResult;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformation;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException.FailureType;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationReader;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreatorPayoutProfileRegistrationServiceTest {

    private static final long CREATOR_ID = 7L;
    private CreatorPayoutProfileRepository profileRepository;
    private CreatorInformationReader creatorInformationReader;
    private TossSellerRegistrationGateway sellerRegistrationGateway;
    private CreatorPayoutProfileRegistrationService service;
    private CreatorPayoutProfile profile;

    @BeforeEach
    void setUp() {
        profileRepository = mock(CreatorPayoutProfileRepository.class);
        creatorInformationReader = mock(CreatorInformationReader.class);
        sellerRegistrationGateway = mock(TossSellerRegistrationGateway.class);
        service = new CreatorPayoutProfileRegistrationService(
                profileRepository, creatorInformationReader, sellerRegistrationGateway
        );
        profile = CreatorPayoutProfile.awaitingRegistration(CREATOR_ID);
        when(profileRepository.findByCreatorId(CREATOR_ID)).thenReturn(Optional.of(profile));
        when(creatorInformationReader.read(CREATOR_ID)).thenReturn(creatorInformation());
    }

    @Test
    void completesProfileOnlyAfterUserAndSellerRegistrationSucceed() {
        when(sellerRegistrationGateway.register(any())).thenReturn(new TossSellerRegistrationResult.Registered(
                "seller-7", CreatorPayoutStatus.PAYOUT_READY
        ));

        service.registerByAdmin(CREATOR_ID);

        assertThat(profile.status()).isEqualTo(CreatorPayoutStatus.PAYOUT_READY);
        assertThat(profile.tossSellerId()).isEqualTo("seller-7");
        verify(profileRepository).save(profile);
    }

    @Test
    void leavesProfilePendingAndSkipsSellerRegistrationWhenUserIsUnavailable() {
        when(creatorInformationReader.read(CREATOR_ID)).thenThrow(new CreatorInformationException(
                FailureType.AVAILABILITY, "User unavailable", null
        ));

        assertThatThrownBy(() -> service.registerByAdmin(CREATOR_ID))
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(SettlementErrorCode.CREATOR_INFORMATION_UNAVAILABLE));

        assertThat(profile.status()).isEqualTo(CreatorPayoutStatus.REGISTRATION_PENDING);
        verify(sellerRegistrationGateway, never()).register(any());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void leavesProfilePendingForDefinitiveSellerRejection() {
        when(sellerRegistrationGateway.register(any())).thenReturn(new TossSellerRegistrationResult.Rejected("REJECTED"));

        assertThatThrownBy(() -> service.registerByAdmin(CREATOR_ID))
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(SettlementErrorCode.SELLER_REGISTRATION_REJECTED));

        assertThat(profile.status()).isEqualTo(CreatorPayoutStatus.REGISTRATION_PENDING);
        verify(profileRepository, never()).save(any());
    }

    @Test
    void leavesProfilePendingAndDoesNotRetryUnknownSellerResult() {
        when(sellerRegistrationGateway.register(any())).thenThrow(new TossSellerRegistrationGatewayException("unknown"));

        assertThatThrownBy(() -> service.registerByAdmin(CREATOR_ID))
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(SettlementErrorCode.SELLER_REGISTRATION_RESULT_UNKNOWN));

        assertThat(profile.status()).isEqualTo(CreatorPayoutStatus.REGISTRATION_PENDING);
        verify(sellerRegistrationGateway).register(any());
        verify(profileRepository, never()).save(any());
    }

    private static CreatorInformation creatorInformation() {
        return new CreatorInformation("creator@example.com", "창작자", "01012345678");
    }
}
