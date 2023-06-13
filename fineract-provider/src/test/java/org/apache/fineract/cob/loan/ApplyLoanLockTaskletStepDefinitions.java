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
package org.apache.fineract.cob.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.cucumber.java8.En;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.cob.common.CustomJobParameterResolver;
import org.apache.fineract.cob.data.LoanCOBParameter;
import org.apache.fineract.cob.domain.LoanAccountLock;
import org.apache.fineract.cob.domain.LockOwner;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

public class ApplyLoanLockTaskletStepDefinitions implements En {

    ArgumentCaptor<List> valueCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<LockOwner> lockOwnerValueCaptor = ArgumentCaptor.forClass(LockOwner.class);
    private LoanLockingService loanLockingService = mock(LoanLockingService.class);
    private FineractProperties fineractProperties = mock(FineractProperties.class);
    private FineractProperties.FineractQueryProperties fineractQueryProperties = mock(FineractProperties.FineractQueryProperties.class);
    private RetrieveLoanIdService retrieveLoanIdService = mock(RetrieveLoanIdService.class);

    private CustomJobParameterResolver customJobParameterResolver = mock(CustomJobParameterResolver.class);
    private ApplyLoanLockTasklet applyLoanLockTasklet = new ApplyLoanLockTasklet(fineractProperties, loanLockingService,
            retrieveLoanIdService, customJobParameterResolver);
    private RepeatStatus resultItem;
    private StepContribution stepContribution;

    public ApplyLoanLockTaskletStepDefinitions() {
        Given("/^The ApplyLoanLockTasklet.execute method with action (.*)$/", (String action) -> {
            ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
            HashMap<BusinessDateType, LocalDate> businessDateMap = new HashMap<>();
            businessDateMap.put(BusinessDateType.COB_DATE, LocalDate.now(ZoneId.systemDefault()));
            ThreadLocalContextUtil.setBusinessDates(businessDateMap);
            StepExecution stepExecution = new StepExecution("test", null);
            ExecutionContext executionContext = new ExecutionContext();
            LoanCOBParameter loanCOBParameter = new LoanCOBParameter(1L, 4L);
            executionContext.put(LoanCOBConstant.LOAN_COB_PARAMETER, loanCOBParameter);
            lenient().when(
                    retrieveLoanIdService.retrieveAllNonClosedLoansByLastClosedBusinessDateAndMinAndMaxLoanId(loanCOBParameter, false))
                    .thenReturn(List.of(1L, 2L, 3L, 4L));
            stepExecution.setExecutionContext(executionContext);
            stepContribution = new StepContribution(stepExecution);

            if ("error".equals(action)) {
                lenient().when(fineractProperties.getQuery()).thenReturn(fineractQueryProperties);
                lenient().when(fineractQueryProperties.getInClauseParameterSizeLimit()).thenReturn(65000);
                lenient().when(loanLockingService.findAllByLoanIdIn(Mockito.anyList())).thenThrow(new RuntimeException("fail"));
            } else {
                LoanAccountLock lock1 = new LoanAccountLock(1L, LockOwner.LOAN_COB_CHUNK_PROCESSING, LocalDate.now(ZoneId.systemDefault()));
                LoanAccountLock lock3 = new LoanAccountLock(3L, LockOwner.LOAN_INLINE_COB_PROCESSING,
                        LocalDate.now(ZoneId.systemDefault()));
                List<LoanAccountLock> accountLocks = List.of(lock1, lock3);
                lenient().when(fineractProperties.getQuery()).thenReturn(fineractQueryProperties);
                lenient().when(fineractQueryProperties.getInClauseParameterSizeLimit()).thenReturn(65000);
                lenient().when(loanLockingService.findAllByLoanIdIn(Mockito.anyList())).thenReturn(accountLocks);
            }
            lenient().when(customJobParameterResolver.getCustomJobParameterSet(any())).thenReturn(Optional.empty());

        });

        When("ApplyLoanLockTasklet.execute method executed", () -> {
            resultItem = applyLoanLockTasklet.execute(stepContribution, null);
        });

        Then("ApplyLoanLockTasklet.execute result should match", () -> {
            assertEquals(RepeatStatus.FINISHED, resultItem);
            verify(loanLockingService, Mockito.times(1)).applyLock(valueCaptor.capture(), lockOwnerValueCaptor.capture());
            List<Long> values = valueCaptor.getValue();
            assertEquals(2L, values.get(0));
            assertEquals(LockOwner.LOAN_COB_CHUNK_PROCESSING, lockOwnerValueCaptor.getValue());
        });

        Then("throw exception ApplyLoanLockTasklet.execute method", () -> {
            assertThrows(RuntimeException.class, () -> {
                resultItem = applyLoanLockTasklet.execute(stepContribution, null);
            });
        });
    }
}
