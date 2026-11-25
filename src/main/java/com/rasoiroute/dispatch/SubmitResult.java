package com.rasoiroute.dispatch;

/**
 * What happened when someone tried to save an order.
 *
 * @param accepted      true if the order was saved
 * @param message       a plain sentence the screen can show
 * @param duplicateOfId when the order was refused as a repeat: the id of the order it repeats; otherwise null
 */
public record SubmitResult(boolean accepted, String message, String duplicateOfId) {
}
