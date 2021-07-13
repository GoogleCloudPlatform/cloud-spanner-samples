/*
description goes here...
 */

package com.google.finapp;

import com.google.cloud.ByteArray;
import com.google.cloud.spanner.*;

import java.math.BigDecimal;

public interface SpannerDaoInterface {

  void createCustomer(ByteArray customerId, String name, String address) throws SpannerException;

  void createAccount(
      ByteArray accountId, AccountType accountType, AccountStatus accountStatus, BigDecimal balance)
      throws SpannerException;

  void addAccountForCustomer(
      ByteArray customerId, ByteArray accountId, ByteArray roleId, String roleName)
      throws SpannerException;

  void moveAccountBalance(ByteArray fromAccountId, ByteArray toAccountId,
      BigDecimal amount) throws SpannerException;
}
