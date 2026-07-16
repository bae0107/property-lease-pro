# Property Lease Spec (PUML) - Revised v2

This package is the unified corrected version based on the latest confirmed rules:

- DRAFT contracts do **not** lock rooms; multiple DRAFT contracts may reference the same room.
- Room locking happens when the contract is confirmed and enters `SIGN_BILL_PENDING`.
- Both `DRAFT` and `SIGN_BILL_PENDING` contracts can be cancelled.
- Assignment is a prerequisite for check-in.
- Assignment is created by STAFF only.
- Tenant self check-in is allowed only after assignment auto-creates/enables the tenant user capability.
- Check-in consumes assignment and creates the actual stay relationship.
- Active assignment count plus checked-in stay count cannot exceed `Room.maxOccupancy`.
- Enterprise sign bill payment moves contract to `READY_FOR_CHECK_IN`.
- Personal deposit is created on check-in, paid once, and does not need to be re-paid on transfer.
- Transfer may cross contracts.
- Room account belongs to room, not to meter device.
- Meter replacement does not reset room account or balances.
- Daily settlement deducts enterprise sub-balance first, then tenant sub-balances.
- Tenant unused room-account sub-balance migrates with the tenant on transfer.
