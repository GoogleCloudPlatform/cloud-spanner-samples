# Cloud Spanner Banking Design Discussion

## Purpose of this doc
This document outlines requirements for the sample banking app, gives an
overview of the components and discusses alternative schema designs.

## Requirements

Our banking application should model the following entities:
- *Customer*: End-user of the application
  - Relevant information: Name, address, etc...
- *Account*: A financial account, keeps track of balance. Customers have an
  n:m relationship with Accounts, e.g: an Account can be accessed by multiple
  Customers with different roles.
  - Relevant information: Balance, creation time, etc...
- *Financial Transaction*: A monetary transfer from one account in the system
  to another account that can be external or not.
  - Relevant information: Amount, credit or no, timestamp, etc...

### Entity Relation diagram

[](TODO(voulg): Insert pic here)

### Operations
The application should support the following operations:
- *CreateCustomer*: Add a new customer
- *CreateAccount*: Add a new account
- *CreateCustomerRole*: Associate a customer with a role for a specified
  account
- *CreateTransactionForAccount*: Create a financial transaction between a given
  account and an external entity
- *MoveAccountBalance*: A special transaction for moving a specified amount from
  one account to another
- *GetRecentTransactionsForAccount*: Get recent transactions for a specified
  account, it should support pagination.

## Schema Design discussion



This document outlines requirements for the sample banking app and goes over
alternative schema designs discussing pros / cons.
This is not a general Cloud Spanner schema design guide, instead the goal
is to highlight the importance of schema design with examples and show how
and why schema decisions significantly affect Cloud Spanner performance.
For a more complete list of best practices see [here](TODO(voulg): Link)


For the proposed schema see:
[](TODO(voulg): Link to SDL)

### UUIDs for keys: Discussion on hot-spotting


### TransactionHistory: Discussion on interleaving

### CustomerRoles: Modeling n:m relationships

### Getting recent transactions: Discussion on pagination

