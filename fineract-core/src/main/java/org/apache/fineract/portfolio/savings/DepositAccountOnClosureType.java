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
package org.apache.fineract.portfolio.savings;

import java.util.ArrayList;
import java.util.List;

/**
 * An enumeration of different options available on account closure {@link FixedDepositAccount} &amp;
 * {@link RecurringDepositAccount}.
 */
public enum DepositAccountOnClosureType {

    INVALID(0, "depositAccountClosureType.invalid"), //
    WITHDRAW_DEPOSIT(100, "depositAccountClosureType.withdrawDeposit"), //
    TRANSFER_TO_SAVINGS(200, "depositAccountClosureType.transferToSavings"), //
    REINVEST_PRINCIPAL_AND_INTEREST(300, "depositAccountClosureType.reinvestPrincipalAndInterest"), REINVEST_PRINCIPAL_ONLY(400,
            "depositAccountClosureType.reinvestPrincipalOnly"); //

    private final Integer value;
    private final String code;

    DepositAccountOnClosureType(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    public static DepositAccountOnClosureType fromInt(final Integer closureTypeValue) {

        if (closureTypeValue == null) {
            return DepositAccountOnClosureType.INVALID;
        }

        DepositAccountOnClosureType accountOnClosureType = DepositAccountOnClosureType.INVALID;
        switch (closureTypeValue) {
            case 100:
                accountOnClosureType = DepositAccountOnClosureType.WITHDRAW_DEPOSIT;
            break;
            case 200:
                accountOnClosureType = DepositAccountOnClosureType.TRANSFER_TO_SAVINGS;
            break;
            case 300:
                accountOnClosureType = DepositAccountOnClosureType.REINVEST_PRINCIPAL_AND_INTEREST;
            break;
            case 400:
                accountOnClosureType = DepositAccountOnClosureType.REINVEST_PRINCIPAL_ONLY;
            break;
        }
        return accountOnClosureType;
    }

    public boolean isWithdarwDeposit() {
        return this.value.equals(DepositAccountOnClosureType.WITHDRAW_DEPOSIT.getValue());
    }

    public boolean isTransferToSavings() {
        return this.value.equals(DepositAccountOnClosureType.TRANSFER_TO_SAVINGS.getValue());
    }

    public boolean isReinvest() {
        return this.value.equals(DepositAccountOnClosureType.REINVEST_PRINCIPAL_AND_INTEREST.getValue())
                || this.value.equals(DepositAccountOnClosureType.REINVEST_PRINCIPAL_ONLY.getValue());
    }

    public boolean isReinvestPrincipal() {
        return this.value.equals(DepositAccountOnClosureType.REINVEST_PRINCIPAL_ONLY.getValue());
    }

    public boolean isReinvestPrincipalAndInterest() {
        return this.value.equals(DepositAccountOnClosureType.REINVEST_PRINCIPAL_AND_INTEREST.getValue());
    }

    public boolean isInvalid() {
        return this.value.equals(DepositAccountOnClosureType.INVALID.getValue());
    }

    public static Object[] integerValues() {
        final List<Integer> values = new ArrayList<>();
        for (final DepositAccountOnClosureType enumType : values()) {
            if (!enumType.isInvalid()) {
                values.add(enumType.getValue());
            }
        }

        return values.toArray();
    }
}
