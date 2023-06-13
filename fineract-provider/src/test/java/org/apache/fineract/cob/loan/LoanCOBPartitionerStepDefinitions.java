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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.gson.Gson;
import io.cucumber.java8.En;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.cob.COBBusinessStepService;
import org.apache.fineract.cob.data.BusinessStepNameAndOrder;
import org.apache.fineract.cob.data.LoanCOBParameter;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.infrastructure.springbatch.PropertyService;
import org.mockito.Mockito;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.item.ExecutionContext;

public class LoanCOBPartitionerStepDefinitions implements En {

    PropertyService propertyService = mock(PropertyService.class);
    COBBusinessStepService cobBusinessStepService = mock(COBBusinessStepService.class);
    JobOperator jobOperator = mock(JobOperator.class);
    JobExplorer jobExplorer = mock(JobExplorer.class);
    private final Gson gson = GoogleGsonSerializerHelper.createSimpleGson();

    LoanCOBParameter loanIds;
    private LoanCOBPartitioner loanCOBPartitioner;

    private Set<BusinessStepNameAndOrder> cobBusinessSteps = new HashSet<>();

    private Map<String, ExecutionContext> resultItem;
    private String action;

    public LoanCOBPartitionerStepDefinitions() {
        Given("/^The LoanCOBPartitioner.partition method with action (.*)$/", (String action) -> {

            this.action = action;
            lenient().when(propertyService.getPartitionSize(LoanCOBConstant.JOB_NAME)).thenReturn(2);
            if ("empty steps".equals(action)) {
                lenient().when(cobBusinessStepService.getCOBBusinessSteps(LoanCOBBusinessStep.class, LoanCOBConstant.LOAN_COB_JOB_NAME))
                        .thenReturn(Collections.emptySet());
                lenient().when(jobExplorer.findRunningJobExecutions(JobName.LOAN_COB.name())).thenReturn(Set.of(new JobExecution(3L)));
                lenient().when(jobOperator.stop(3L)).thenReturn(Boolean.TRUE);
                loanIds = null;
            } else if ("empty loanIds".equals(action)) {
                cobBusinessSteps.add(new BusinessStepNameAndOrder("Business step", 1L));
                lenient().when(cobBusinessStepService.getCOBBusinessSteps(LoanCOBBusinessStep.class, LoanCOBConstant.LOAN_COB_JOB_NAME))
                        .thenReturn(cobBusinessSteps);
                loanIds = null;
            } else if ("good".equals(action)) {
                cobBusinessSteps.add(new BusinessStepNameAndOrder("Business step", 1L));
                lenient().when(cobBusinessStepService.getCOBBusinessSteps(LoanCOBBusinessStep.class, LoanCOBConstant.LOAN_COB_JOB_NAME))
                        .thenReturn(cobBusinessSteps);
                loanIds = new LoanCOBParameter(1L, 3L);
            }
            loanCOBPartitioner = new LoanCOBPartitioner(propertyService, cobBusinessStepService, jobOperator, jobExplorer, loanIds);
        });

        When("LoanCOBPartitioner.partition method executed", () -> {
            resultItem = this.loanCOBPartitioner.partition(2);
        });

        Then("LoanCOBPartitioner.partition result should match", () -> {
            if ("empty steps".equals(action)) {
                verify(jobOperator, Mockito.times(1)).stop(3L);
                assertTrue(resultItem.isEmpty());
            } else if ("good".equals(action)) {
                verify(jobOperator, Mockito.times(0)).stop(Mockito.anyLong());
                assertEquals(2, resultItem.size());
                assertTrue(resultItem.containsKey(LoanCOBPartitioner.PARTITION_PREFIX + "1"));
                Set<BusinessStepNameAndOrder> businessSteps = (Set<BusinessStepNameAndOrder>) resultItem
                        .get(LoanCOBPartitioner.PARTITION_PREFIX + "1").get(LoanCOBConstant.BUSINESS_STEPS);
                assertEquals(cobBusinessSteps.stream().findFirst().get().getStepOrder(),
                        businessSteps.stream().findFirst().get().getStepOrder());
                assertEquals(cobBusinessSteps.stream().findFirst().get().getStepName(),
                        businessSteps.stream().findFirst().get().getStepName());
                LoanCOBParameter loanCOBParameter = (LoanCOBParameter) resultItem.get(LoanCOBPartitioner.PARTITION_PREFIX + "1")
                        .get(LoanCOBConstant.LOAN_COB_PARAMETER);
                assertEquals(1, loanCOBParameter.getMaxLoanId() - loanCOBParameter.getMinLoanId());
                assertEquals(1L, loanCOBParameter.getMinLoanId());
                assertEquals(2L, loanCOBParameter.getMaxLoanId());
                assertTrue(resultItem.containsKey(LoanCOBPartitioner.PARTITION_PREFIX + "2"));
                assertEquals(cobBusinessSteps.stream().findFirst().get().getStepOrder(),
                        businessSteps.stream().findFirst().get().getStepOrder());
                assertEquals(cobBusinessSteps.stream().findFirst().get().getStepName(),
                        businessSteps.stream().findFirst().get().getStepName());
                loanCOBParameter = (LoanCOBParameter) resultItem.get(LoanCOBPartitioner.PARTITION_PREFIX + "2")
                        .get(LoanCOBConstant.LOAN_COB_PARAMETER);
                assertEquals(0, loanCOBParameter.getMaxLoanId() - loanCOBParameter.getMinLoanId());
                assertEquals(3L, loanCOBParameter.getMinLoanId());
                assertEquals(3L, loanCOBParameter.getMaxLoanId());
            } else if ("empty loanIds".equals(action)) {
                verify(jobOperator, Mockito.times(0)).stop(Mockito.anyLong());
                assertEquals(1, resultItem.size());
                assertTrue(resultItem.containsKey(LoanCOBPartitioner.PARTITION_PREFIX + "1"));
                Set<BusinessStepNameAndOrder> businessSteps = (Set<BusinessStepNameAndOrder>) resultItem
                        .get(LoanCOBPartitioner.PARTITION_PREFIX + "1").get(LoanCOBConstant.BUSINESS_STEPS);
                assertEquals(cobBusinessSteps.stream().findFirst().get().getStepOrder(),
                        businessSteps.stream().findFirst().get().getStepOrder());
                assertEquals(cobBusinessSteps.stream().findFirst().get().getStepName(),
                        businessSteps.stream().findFirst().get().getStepName());
                LoanCOBParameter loanCOBParameter = (LoanCOBParameter) resultItem.get(LoanCOBPartitioner.PARTITION_PREFIX + "1")
                        .get(LoanCOBConstant.LOAN_COB_PARAMETER);
                assertEquals(0L, loanCOBParameter.getMinLoanId());
                assertEquals(0L, loanCOBParameter.getMaxLoanId());
            }
        });
    }
}
