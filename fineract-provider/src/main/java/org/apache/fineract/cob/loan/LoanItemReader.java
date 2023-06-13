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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.fineract.cob.common.CustomJobParameterResolver;
import org.apache.fineract.cob.data.LoanCOBParameter;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ExecutionContext;

public class LoanItemReader extends AbstractLoanItemReader {

    private final RetrieveLoanIdService retrieveLoanIdService;
    private final CustomJobParameterResolver customJobParameterResolver;

    public LoanItemReader(LoanRepository loanRepository, RetrieveLoanIdService retrieveLoanIdService,
            CustomJobParameterResolver customJobParameterResolver) {
        super(loanRepository);
        this.retrieveLoanIdService = retrieveLoanIdService;
        this.customJobParameterResolver = customJobParameterResolver;
    }

    @BeforeStep
    @SuppressWarnings({ "unchecked" })
    public void beforeStep(@NotNull StepExecution stepExecution) {
        ExecutionContext executionContext = stepExecution.getExecutionContext();
        LoanCOBParameter loanCOBParameter = (LoanCOBParameter) executionContext.get(LoanCOBConstant.LOAN_COB_PARAMETER);
        List<Long> loanIds;
        if (Objects.isNull(loanCOBParameter)
                || (Objects.isNull(loanCOBParameter.getMinLoanId()) && Objects.isNull(loanCOBParameter.getMaxLoanId()))
                || (loanCOBParameter.getMinLoanId().equals(0L) && loanCOBParameter.getMaxLoanId().equals(0L))) {
            loanIds = Collections.emptyList();
        } else {
            loanIds = retrieveLoanIdService.retrieveAllNonClosedLoansByLastClosedBusinessDateAndMinAndMaxLoanId(loanCOBParameter,
                    customJobParameterResolver.getCustomJobParameterById(stepExecution, LoanCOBConstant.IS_CATCH_UP_PARAMETER_NAME)
                            .map(Boolean::parseBoolean).orElse(false));
        }
        setRemainingData(new ArrayList<>(loanIds));
    }
}
