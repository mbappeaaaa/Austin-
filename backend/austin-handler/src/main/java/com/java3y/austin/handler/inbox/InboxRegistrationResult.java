package com.java3y.austin.handler.inbox;

import lombok.Value;

@Value
public class InboxRegistrationResult {
    InboxMessage message;
    boolean created;
}
