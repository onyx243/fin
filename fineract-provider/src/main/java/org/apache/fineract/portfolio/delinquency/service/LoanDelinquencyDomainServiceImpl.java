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
package org.apache.fineract.portfolio.delinquency.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.loanaccount.data.CollectionData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class LoanDelinquencyDomainServiceImpl implements LoanDelinquencyDomainService {

    @Override
    @Transactional(readOnly = true)
    public CollectionData getOverdueCollectionData(final Loan loan) {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();

        final MonetaryCurrency loanCurrency = loan.getCurrency();
        LocalDate overdueSinceDate = null;
        CollectionData collectionData = CollectionData.template();
        BigDecimal amountAvailable;
        BigDecimal outstandingAmount = BigDecimal.ZERO;
        boolean oldestOverdueInstallment = false;
        boolean overdueSinceDateWasSet = false;
        boolean firstNotYetDueInstallment = false;
        LoanRepaymentScheduleInstallment latestInstallment = loan.getLastLoanRepaymentScheduleInstallment();

        List<LoanTransaction> chargebackTransactions = loan.getLoanTransactions(LoanTransaction::isChargeback);

        log.debug("Loan id {} with {} installments", loan.getId(), loan.getRepaymentScheduleInstallments().size());
        // Get the oldest overdue installment if exists one
        for (LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (!installment.isObligationsMet()) {
                if (installment.getDueDate().isBefore(businessDate)) {
                    log.debug("Loan Id: {} with installment {} due date {}", loan.getId(), installment.getInstallmentNumber(),
                            installment.getDueDate());
                    outstandingAmount = outstandingAmount.add(installment.getTotalOutstanding(loanCurrency).getAmount());
                    if (!oldestOverdueInstallment) {
                        log.debug("Oldest installment {} {}", installment.getInstallmentNumber(), installment.getDueDate());
                        oldestOverdueInstallment = true;
                        overdueSinceDate = installment.getDueDate();
                        overdueSinceDateWasSet = true;

                        amountAvailable = installment.getTotalPaid(loanCurrency).getAmount();

                        boolean isLatestInstallment = Objects.equals(installment.getId(), latestInstallment.getId());
                        for (LoanTransaction loanTransaction : chargebackTransactions) {
                            boolean isLoanTransactionIsOnOrAfterInstallmentFromDate = loanTransaction.getTransactionDate().isEqual(
                                    installment.getFromDate()) || loanTransaction.getTransactionDate().isAfter(installment.getFromDate());
                            boolean isLoanTransactionIsBeforeNotLastInstallmentDueDate = !isLatestInstallment
                                    && loanTransaction.getTransactionDate().isBefore(installment.getDueDate());
                            boolean isLoanTransactionIsOnOrBeforeLastInstallmentDueDate = isLatestInstallment
                                    && (loanTransaction.getTransactionDate().isEqual(installment.getDueDate())
                                            || loanTransaction.getTransactionDate().isBefore(installment.getDueDate()));
                            if (isLoanTransactionIsOnOrAfterInstallmentFromDate && (isLoanTransactionIsBeforeNotLastInstallmentDueDate
                                    || isLoanTransactionIsOnOrBeforeLastInstallmentDueDate)) {
                                amountAvailable = amountAvailable.subtract(loanTransaction.getAmount());
                                if (amountAvailable.compareTo(BigDecimal.ZERO) < 0) {
                                    overdueSinceDate = loanTransaction.getTransactionDate();
                                    break;
                                }
                            }
                        }
                    }
                } else if (!firstNotYetDueInstallment) {
                    log.debug("Loan Id: {} with installment {} due date {}", loan.getId(), installment.getInstallmentNumber(),
                            installment.getDueDate());
                    firstNotYetDueInstallment = true;
                    amountAvailable = installment.getTotalPaid(loanCurrency).getAmount();
                    log.debug("Amount available {}", amountAvailable);
                    for (LoanTransaction loanTransaction : chargebackTransactions) {
                        boolean isLoanTransactionIsOnOrAfterInstallmentFromDate = loanTransaction.getTransactionDate().isEqual(
                                installment.getFromDate()) || loanTransaction.getTransactionDate().isAfter(installment.getFromDate());
                        boolean isLoanTransactionIsBeforeInstallmentDueDate = loanTransaction.getTransactionDate()
                                .isBefore(installment.getDueDate());
                        boolean isLoanTransactionIsBeforeBusinessDate = loanTransaction.getTransactionDate().isBefore(businessDate);
                        if (isLoanTransactionIsOnOrAfterInstallmentFromDate && isLoanTransactionIsBeforeInstallmentDueDate
                                && isLoanTransactionIsBeforeBusinessDate) {
                            log.debug("Loan CB Transaction: {} {} {}", loanTransaction.getId(), loanTransaction.getTransactionDate(),
                                    loanTransaction.getAmount());
                            amountAvailable = amountAvailable.subtract(loanTransaction.getAmount());
                            if (amountAvailable.compareTo(BigDecimal.ZERO) < 0 && !overdueSinceDateWasSet) {
                                overdueSinceDate = loanTransaction.getTransactionDate();
                                overdueSinceDateWasSet = true;
                            }
                        }
                    }

                    if (amountAvailable.compareTo(BigDecimal.ZERO) < 0) {
                        outstandingAmount = outstandingAmount.add(amountAvailable.abs());
                    }
                }
            }
        }

        Integer graceDays = 0;
        if (loan.getLoanProduct().getLoanProductRelatedDetail().getGraceOnArrearsAgeing() != null) {
            graceDays = loan.getLoanProduct().getLoanProductRelatedDetail().getGraceOnArrearsAgeing();
            if (graceDays == null) {
                graceDays = 0;
            }
        }
        log.debug("Loan id {} with overdue since date {} and outstanding amount {}", loan.getId(), overdueSinceDate, outstandingAmount);

        Long overdueDays = 0L;
        if (overdueSinceDate != null) {
            overdueDays = DateUtils.getDifferenceInDays(overdueSinceDate, businessDate);
            if (overdueDays < 0) {
                overdueDays = 0L;
            }
            collectionData.setPastDueDays(overdueDays);
            overdueSinceDate = overdueSinceDate.plusDays(graceDays.longValue());
            collectionData.setDelinquentDate(overdueSinceDate);
        }
        collectionData.setDelinquentAmount(outstandingAmount);
        collectionData.setDelinquentDays(0L);
        Long delinquentDays = overdueDays - graceDays;
        if (delinquentDays > 0) {
            collectionData.setDelinquentDays(delinquentDays);
        }

        log.debug("Result: {}", collectionData.toString());
        return collectionData;
    }

}
