# <Problem>
Processor that receives EMIT signals from RazorPay system post
payment initiation at multiple stages from client/user

Sample event:
{
"event_id": "evt_123",
"payment_id": "pay_456",
"event_type": "payment.captured",
"amount": 10000,
"currency": "INR",
"created_at": "2026-10-30T10:00:00Z",
"signature": "..."
}

Payment states can be as follows:
- authorized / captured / failed / refunded
  Customer authorizes/initiates the payment - authorized state
  Bank receives/debits the amount from user account - captured state
- If the expected amount does not match with payment amount, post captured state, the payment will be moved to refunded state (processor system would not be aware of this beforehand)
- after authorised state, if the payment fails - system receives a failed event


There might be retries if there was a network failure in the events receival
There can be cases where the payments are unauthorized

Processor should be able to update our downstream services
Eg. notification service

Processor sends back an ack if the emitted event has been processed
It gracefully declines any valid duplicate events that it receives


# <Requirments>
To build a processor
- validates the incoming event data
- pass down the event for the following down streams
    - notification service
    - ledger service (for book-keeping)
- Maximum amount is 5 Lakhs, and min is 1 Rs
- Amount can have Paisa
- update / create payment storage data
- to send payload to our downstream system
- if the incoming event's event type is an invalid transition of payment's current state, throw exception and drop it
- if the incoming event's timestamp is older than the payment's last updated timestamp, throw exception and rop it

# <Out of Scope>
- Currency will only be in INR
- If any of the downstream system call fails, retries are not handled
- notication service / ledger service would just receive event data for now, user details are handles inside
- Deleting data post certain window
- Signature validation is ignored

# Rules and Validation
- null values are invalidated
- any data / type mismatch in values should be gracefully declined
- Valid state transitions:
    - authorized -> captured / failed / refunded
    - failed -> refunded
    - captured -> failed / refunded

# Storage
- We would store data (stream of events)
- Events will be linked to Payment
- Payment state change times should also be stored
- DLQueue for storing events (optional)

# Design
- Entity:
    - Payment
        - id (final)
        - amount (final)
        - currency (final)
        - created_at (final)
        - last_updated_at
        - current_stage
        - stages_timestamp (linkedhashmap)
    - Event
        - event_id (final)
        - payment (final)
        - event_type (final)
        - time (final)
        - signature (final)
- Enum:
    - Payment state / event type
- Seam (Interface)
    - DownstreamService (fire and forget) (SendPayload)
        - NotificationService
        - LedgerService
- Facade:
    - Processor
        - receiveSendEmit(Event)
        - ValidateEvent() -> if failed, GracefulDrop() with exception
        - sendAcknowledgementToUpstream()
        - persistPayment()
        - broadcastDownstreamServices()
- Storage (In-memory storage)
    - Payments
    - Events
- Exceptions
    - InvalidEventException
    - InvalidPaymentStateException
    - DuplicateEventException
    - InvalidAmountRangeException
    - OlderEventException

# Slices
    - Create payment and event entity. Add Enums and exceptions, storage
    - Receival and Validation of states in Processer
        : InvalidEventException (if any required field is missing or value is empty)
    - implement storage module, where payments are stored in a hashmap with payment id as key
    - Create processor with all the listed functions


Enums + exceptions + Payment/Event
In-memory PaymentStore / EventStore
Processor: validate fields + amount range + duplicates
State/time guards + persist
Downstream seam + broadcast + ack