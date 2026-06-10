# AML Domain Protocols

Rules specific to casehub-aml's domain implementation.

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [aml-ledger-entry-tenancy-id-non-null.md](aml-ledger-entry-tenancy-id-non-null.md) | AML ledger entry writes must guarantee a non-null tenancyId | Any class writing an AML `LedgerEntry` subclass |
