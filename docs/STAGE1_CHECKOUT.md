# Trade Core — Confirmed Multi-line Checkout API (local)

## New endpoints

All order/checkout APIs require `Authorization: Bearer <Store login token>`.
Identity comes from the signed token and an existing Store user. `X-User-Id`
and the legacy list `userId` query parameter cannot select another identity.
Cross-user reads/cancellations return 403. Catalog/warehouse writes require ADMIN.
The existing React diagnostic UI forwards the login token.

The Storefront BFF forwards a Store-issued token obtained through
an explicit account-linking flow; its own JWT is not accepted by Store. Do not
share the Store signing secret with browser code or accept an unsigned identity header.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/checkouts` | body `{ "items":[{"skuId":1,"quantity":1}], "shippingAddress":"上海..." }` |
| PUT | `/api/checkouts/{id}` | update items/address → back to DRAFT |
| POST | `/api/checkouts/{id}/quote` | compute CNY subtotal + shipping (&lt;99 → 10) |
| POST | `/api/checkouts/{id}/confirm` | body `{ "quoteVersion":1 }` → CONFIRMED |
| POST | `/api/checkouts/{id}/complete` | body `{ "idempotencyKey":"checkout-unique-key", "quoteVersion":1 }`; optional `bankMock` for local fault injection |
| GET | `/api/checkouts/{id}` | |
| GET | `/api/products/search?q=&priceMax=&inStock=` | structured search |
| GET | `/api/payments/by-key/{idempotencyKey}` | **Bank** query for reconciler |

## Flow

```text
create → quote → confirm → complete
  → Order PAYMENT_PENDING + stock hold + PaymentAttempt
  → Bank pay / on timeout query(paymentAttemptId)
  → PAID | FAILED | stay PENDING (never release stock on UNKNOWN)
```

Cancel: unpaid only after Bank final; paid unshipped → refund record PENDING + MQ refund.

## Note

`complete` accepts 1–100 distinct SKU lines. The core creates one order and one payment
intent, reserving every line in the same transaction. Any line's stock failure rolls
back the entire cart before Bank is called. Duplicate SKU lines are rejected; merge
their quantities before creating/updating the checkout.

Orders snapshot shipping address, shipping fee, currency, quote version and unit prices.
Order responses include these fields and `items` (warehouse allocation lines, so a SKU
can appear more than once when fulfilled from multiple warehouses). Later catalog
price changes do not alter a completed order. Legacy orders may have null snapshot fields.

## Confirmation and recovery contract

- Confirm and complete must carry the version actually shown to the user.
- Item/address updates clear confirmation. Re-quoting increments the quote version.
- Complete compares current product price with the quote under a product read lock.
  Price changes or stale versions return 409, requiring a refreshed quote and new confirmation.
- Order, stock reservation, payment intent, completion key and checkout-to-order link
  commit in one local transaction, before any Bank request.
- Replaying the completed session with the same key/version returns its existing order,
  even after quote expiry. A different key returns 409.
- Payment transport/finalization errors leave the committed pending order available to
  scheduled recovery. They do not roll back an already-issued remote debit.
- Expired GET returns an EXPIRED view without taking a write lock or mutating the database.
- Use one persisted idempotency key per checkout; never mint a replacement on retry.

## Current boundary

Bank is a simulated payment service. The legacy direct order API remains for the React
diagnostic UI and does not implement the confirmation workflow; future Agent purchase
tools must use checkouts. This change does not authenticate legacy service callbacks,
gRPC or WebSocket subscriptions; keep the stack on the local demo network until those
channels are hardened. OldPhoneStore BFF, Agent Runtime and RAG
integration remain subsequent work. No public integrated deployment is claimed.

## Verification

`./gradlew.bat :store:test` covers JWT identity/role enforcement, ownership errors,
stale confirmation, changed prices, completion replay, and commit-before-payment ordering.
CheckoutTransactionTest additionally uses H2 with real JPA transactions to verify that
Bank is called outside the local transaction, sees committed order/payment/checkout state,
and is not called after an injected reservation failure. Replays do not reserve stock twice.
These checks do not establish real PostgreSQL/Bank/MQ integration.

Multi-line tests also cover later-line shortage rollback, all-line restock after explicit
payment failure, immutable order pricing, cross-checkout key conflicts, and concurrent
completion of the same cart. React's API client exports `checkoutsAPI`; the existing
diagnostic purchase screen still uses the legacy single-item flow.
