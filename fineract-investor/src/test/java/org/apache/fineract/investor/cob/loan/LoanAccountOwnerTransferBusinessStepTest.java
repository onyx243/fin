/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.investor.cob.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.investor.data.ExternalTransferStatus;
import org.apache.fineract.investor.data.ExternalTransferSubStatus;
import org.apache.fineract.investor.domain.ExternalAssetOwnerTransfer;
import org.apache.fineract.investor.domain.ExternalAssetOwnerTransferLoanMapping;
import org.apache.fineract.investor.domain.ExternalAssetOwnerTransferLoanMappingRepository;
import org.apache.fineract.investor.domain.ExternalAssetOwnerTransferRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
public class LoanAccountOwnerTransferBusinessStepTest {

    public static final LocalDate FUTURE_DATE_9999_12_31 = LocalDate.of(9999, 12, 31);
    private final LocalDate actualDate = LocalDate.now(ZoneId.systemDefault());
    @Mock
    private ExternalAssetOwnerTransferRepository externalAssetOwnerTransferRepository;
    @Mock
    private ExternalAssetOwnerTransferLoanMappingRepository externalAssetOwnerTransferLoanMappingRepository;
    private LoanAccountOwnerTransferBusinessStep underTest;

    @BeforeEach
    public void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, actualDate)));
        underTest = new LoanAccountOwnerTransferBusinessStep(externalAssetOwnerTransferRepository,
                externalAssetOwnerTransferLoanMappingRepository);
    }

    @Test
    public void givenLoanNoTransfer() {
        // given
        final Loan loanForProcessing = Mockito.mock(Loan.class);
        Long loanId = 1L;
        LocalDate settlementDate = actualDate;
        when(loanForProcessing.getId()).thenReturn(loanId);
        // when
        final Loan processedLoan = underTest.execute(loanForProcessing);
        // then
        verify(externalAssetOwnerTransferRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id")));
        assertEquals(processedLoan, loanForProcessing);
    }

    @Test
    public void givenLoanTooManyTransfer() {
        // given
        final Loan loanForProcessing = Mockito.mock(Loan.class);
        Long loanId = 1L;
        LocalDate settlementDate = actualDate;
        when(loanForProcessing.getId()).thenReturn(loanId);
        ExternalAssetOwnerTransfer firstResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        ExternalAssetOwnerTransfer secondResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        ExternalAssetOwnerTransfer thirdResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        List<ExternalAssetOwnerTransfer> response = List.of(firstResponseItem, secondResponseItem, thirdResponseItem);
        when(externalAssetOwnerTransferRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id"))))
                .thenReturn(response);
        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.execute(loanForProcessing));
        // then
        assertEquals("Found too many owner transfers(3) by the settlement date(" + actualDate + ")", exception.getMessage());
        verify(externalAssetOwnerTransferRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id")));

    }

    @Test
    public void givenLoanTwoTransferButInvalidSecondTransfer() {
        // given
        final Loan loanForProcessing = Mockito.mock(Loan.class);
        when(loanForProcessing.getId()).thenReturn(1L);
        ExternalAssetOwnerTransfer firstResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        ExternalAssetOwnerTransfer secondResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        when(secondResponseItem.getStatus()).thenReturn(ExternalTransferStatus.ACTIVE);
        List<ExternalAssetOwnerTransfer> response = List.of(firstResponseItem, secondResponseItem);
        when(externalAssetOwnerTransferRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id"))))
                .thenReturn(response);
        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.execute(loanForProcessing));
        // then
        assertEquals("Illegal transfer found. Expected BUYBACK, found: ACTIVE", exception.getMessage());
        verify(externalAssetOwnerTransferRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id")));
    }

    @Test
    public void givenLoanTwoTransferButInvalidFirstTransfer() {
        // given
        final Loan loanForProcessing = Mockito.mock(Loan.class);
        when(loanForProcessing.getId()).thenReturn(1L);
        ExternalAssetOwnerTransfer firstResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        ExternalAssetOwnerTransfer secondResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        when(firstResponseItem.getStatus()).thenReturn(ExternalTransferStatus.BUYBACK);
        when(secondResponseItem.getStatus()).thenReturn(ExternalTransferStatus.BUYBACK);
        List<ExternalAssetOwnerTransfer> response = List.of(firstResponseItem, secondResponseItem);
        when(externalAssetOwnerTransferRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id"))))
                .thenReturn(response);
        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.execute(loanForProcessing));
        // then
        assertEquals("Illegal transfer found. Expected PENDING or ACTIVE, found: BUYBACK", exception.getMessage());
        verify(externalAssetOwnerTransferRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id")));
    }

    @Test
    public void givenLoanTwoTransferSameDay() {
        // given
        final Loan loanForProcessing = Mockito.mock(Loan.class);
        when(loanForProcessing.getId()).thenReturn(1L);
        ExternalAssetOwnerTransfer firstResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        ExternalAssetOwnerTransfer secondResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        when(firstResponseItem.getStatus()).thenReturn(ExternalTransferStatus.PENDING);
        when(secondResponseItem.getStatus()).thenReturn(ExternalTransferStatus.BUYBACK);
        List<ExternalAssetOwnerTransfer> response = List.of(firstResponseItem, secondResponseItem);
        when(externalAssetOwnerTransferRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id"))))
                .thenReturn(response);
        ArgumentCaptor<ExternalAssetOwnerTransfer> externalAssetOwnerTransferArgumentCaptor = ArgumentCaptor
                .forClass(ExternalAssetOwnerTransfer.class);
        // when
        final Loan processedLoan = underTest.execute(loanForProcessing);
        // then
        verify(externalAssetOwnerTransferRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id")));
        verify(firstResponseItem).setEffectiveDateTo(actualDate);
        verify(externalAssetOwnerTransferRepository, times(4)).save(externalAssetOwnerTransferArgumentCaptor.capture());

        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(0).getOwner(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getOwner());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(0).getExternalId(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getExternalId());
        assertEquals(ExternalTransferStatus.CANCELLED, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getStatus());
        assertEquals(ExternalTransferSubStatus.SAMEDAY_TRANSFERS,
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getSubStatus());
        assertEquals(actualDate, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getSettlementDate());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(0).getLoanId(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getLoanId());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(0).getPurchasePriceRatio(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getPurchasePriceRatio());
        assertEquals(actualDate, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getEffectiveDateFrom());
        assertEquals(actualDate, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getEffectiveDateTo());

        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(2).getOwner(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getOwner());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(2).getExternalId(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getExternalId());
        assertEquals(ExternalTransferStatus.CANCELLED, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getStatus());
        assertEquals(ExternalTransferSubStatus.SAMEDAY_TRANSFERS,
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getSubStatus());
        assertEquals(actualDate, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getSettlementDate());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(2).getLoanId(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getLoanId());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(2).getPurchasePriceRatio(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getPurchasePriceRatio());
        assertEquals(actualDate, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getEffectiveDateFrom());
        assertEquals(actualDate, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(3).getEffectiveDateTo());

        assertEquals(processedLoan, loanForProcessing);
    }

    @Test
    public void givenLoanBuyback() {
        // given
        final Loan loanForProcessing = Mockito.mock(Loan.class);
        when(loanForProcessing.getId()).thenReturn(1L);
        ExternalAssetOwnerTransfer firstResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        ExternalAssetOwnerTransfer secondResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        when(firstResponseItem.getStatus()).thenReturn(ExternalTransferStatus.ACTIVE);
        when(secondResponseItem.getStatus()).thenReturn(ExternalTransferStatus.BUYBACK);
        List<ExternalAssetOwnerTransfer> response = List.of(firstResponseItem, secondResponseItem);
        when(externalAssetOwnerTransferRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id"))))
                .thenReturn(response);
        ArgumentCaptor<ExternalAssetOwnerTransfer> externalAssetOwnerTransferArgumentCaptor = ArgumentCaptor
                .forClass(ExternalAssetOwnerTransfer.class);
        when(externalAssetOwnerTransferRepository.save(firstResponseItem)).thenReturn(firstResponseItem);
        when(externalAssetOwnerTransferRepository.save(secondResponseItem)).thenReturn(secondResponseItem);
        // when
        final Loan processedLoan = underTest.execute(loanForProcessing);
        // then
        verify(externalAssetOwnerTransferRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id")));
        verify(firstResponseItem).setEffectiveDateTo(actualDate);
        verify(externalAssetOwnerTransferRepository, times(2)).save(externalAssetOwnerTransferArgumentCaptor.capture());
        verify(secondResponseItem).setEffectiveDateTo(secondResponseItem.getEffectiveDateFrom());
        verify(externalAssetOwnerTransferLoanMappingRepository, times(1)).deleteByLoanIdAndOwnerTransfer(1L, secondResponseItem);

        assertEquals(processedLoan, loanForProcessing);
    }

    @Test
    public void givenLoanOneTransferButInvalidTransfer() {
        // given
        final Loan loanForProcessing = Mockito.mock(Loan.class);
        when(loanForProcessing.getId()).thenReturn(1L);
        ExternalAssetOwnerTransfer firstResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        when(firstResponseItem.getStatus()).thenReturn(ExternalTransferStatus.ACTIVE);
        List<ExternalAssetOwnerTransfer> response = List.of(firstResponseItem);
        when(externalAssetOwnerTransferRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id"))))
                .thenReturn(response);
        // when
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.execute(loanForProcessing));
        // then
        assertEquals("Illegal transfer found. Expected PENDING, found: ACTIVE", exception.getMessage());
        verify(externalAssetOwnerTransferRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id")));
    }

    @Test
    public void givenLoanSale() {
        // given
        final Loan loanForProcessing = Mockito.mock(Loan.class);
        when(loanForProcessing.getId()).thenReturn(1L);
        ExternalAssetOwnerTransfer firstResponseItem = Mockito.mock(ExternalAssetOwnerTransfer.class);
        when(firstResponseItem.getStatus()).thenReturn(ExternalTransferStatus.PENDING);
        List<ExternalAssetOwnerTransfer> response = List.of(firstResponseItem);
        when(externalAssetOwnerTransferRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id"))))
                .thenReturn(response);
        ArgumentCaptor<ExternalAssetOwnerTransfer> externalAssetOwnerTransferArgumentCaptor = ArgumentCaptor
                .forClass(ExternalAssetOwnerTransfer.class);
        ArgumentCaptor<ExternalAssetOwnerTransferLoanMapping> externalAssetOwnerTransferLoanMappingArgumentCaptor = ArgumentCaptor
                .forClass(ExternalAssetOwnerTransferLoanMapping.class);
        ExternalAssetOwnerTransfer newTransfer = Mockito.mock(ExternalAssetOwnerTransfer.class);
        when(externalAssetOwnerTransferRepository.save(any())).thenReturn(firstResponseItem).thenReturn(newTransfer);
        LoanSummary loanSummary = Mockito.mock(LoanSummary.class);
        when(loanForProcessing.getLoanSummary()).thenReturn(loanSummary);
        when(loanSummary.getTotalOutstanding()).thenReturn(BigDecimal.ONE);
        // when
        final Loan processedLoan = underTest.execute(loanForProcessing);
        // then
        verify(externalAssetOwnerTransferRepository, times(1)).findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "id")));
        verify(firstResponseItem).setEffectiveDateTo(actualDate);
        verify(externalAssetOwnerTransferRepository, times(2)).save(externalAssetOwnerTransferArgumentCaptor.capture());

        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(0).getOwner(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getOwner());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(0).getExternalId(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getExternalId());
        assertEquals(ExternalTransferStatus.ACTIVE, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getStatus());
        assertEquals(actualDate, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getSettlementDate());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(0).getLoanId(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getLoanId());
        assertEquals(externalAssetOwnerTransferArgumentCaptor.getAllValues().get(0).getPurchasePriceRatio(),
                externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getPurchasePriceRatio());
        assertEquals(actualDate.plusDays(1), externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getEffectiveDateFrom());
        assertEquals(FUTURE_DATE_9999_12_31, externalAssetOwnerTransferArgumentCaptor.getAllValues().get(1).getEffectiveDateTo());
        verify(externalAssetOwnerTransferLoanMappingRepository, times(1))
                .save(externalAssetOwnerTransferLoanMappingArgumentCaptor.capture());
        assertEquals(1L, externalAssetOwnerTransferLoanMappingArgumentCaptor.getValue().getLoanId());
        assertEquals(newTransfer, externalAssetOwnerTransferLoanMappingArgumentCaptor.getValue().getOwnerTransfer());
        assertEquals(processedLoan, loanForProcessing);
    }

    @Test
    public void testGetEnumStyledNameSuccessScenario() {
        final String actualEnumName = underTest.getEnumStyledName();
        assertNotNull(actualEnumName);
        assertEquals("EXTERNAL_ASSET_OWNER_TRANSFER", actualEnumName);
    }

    @Test
    public void testGetHumanReadableNameSuccessScenario() {
        final String actualEnumName = underTest.getHumanReadableName();
        assertNotNull(actualEnumName);
        assertEquals("Execute external asset owner transfer", actualEnumName);
    }
}
