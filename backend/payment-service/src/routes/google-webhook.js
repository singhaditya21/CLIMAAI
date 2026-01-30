/**
 * Google Play Developer Notifications webhook handler
 */
const express = require('express');
const router = express.Router();

/**
 * Update subscription status in database
 */
async function updateSubscriptionStatus(db, purchaseToken, status, autoRenew = true) {
    const query = `
    UPDATE subscriptions
    SET status = $1,
        auto_renew = $2,
        last_validation_date = NOW(),
        updated_at = NOW()
    WHERE google_purchase_token = $3
    RETURNING *
  `;

    try {
        const result = await db.query(query, [status, autoRenew, purchaseToken]);
        return result.rows[0];
    } catch (error) {
        console.error('Database update error:', error);
        throw error;
    }
}

/**
 * Google Play webhook endpoint
 */
router.post('/', async (req, res) => {
    try {
        const { message } = req.body;

        if (!message || !message.data) {
            return res.status(400).json({ error: 'Invalid request format' });
        }

        // Decode base64 data
        const data = JSON.parse(Buffer.from(message.data, 'base64').toString());

        const { subscriptionNotification, testNotification } = data;

        // Handle test notifications
        if (testNotification) {
            console.log('Received Google test notification');
            return res.status(200).json({ status: 'test received' });
        }

        if (!subscriptionNotification) {
            return res.status(400).json({ error: 'Not a subscription notification' });
        }

        const {
            notificationType,
            purchaseToken,
            subscriptionId,
        } = subscriptionNotification;

        console.log(`Received Google notification: type ${notificationType} for subscription ${subscriptionId}`);

        // Handle notification types
        // https://developer.android.com/google/play/billing/rtdn-reference
        switch (notificationType) {
            case 1: // SUBSCRIPTION_RECOVERED
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'active');
                break;

            case 2: // SUBSCRIPTION_RENEWED
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'active');
                break;

            case 3: // SUBSCRIPTION_CANCELED
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'cancelled', false);
                break;

            case 4: // SUBSCRIPTION_PURCHASED
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'active');
                break;

            case 5: // SUBSCRIPTION_ON_HOLD
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'grace_period');
                break;

            case 6: // SUBSCRIPTION_IN_GRACE_PERIOD
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'grace_period');
                break;

            case 7: // SUBSCRIPTION_RESTARTED
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'active');
                break;

            case 8: // SUBSCRIPTION_PRICE_CHANGE_CONFIRMED
                // Price change accepted
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'active');
                break;

            case 9: // SUBSCRIPTION_DEFERRED
                // Payment deferred
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'grace_period');
                break;

            case 10: // SUBSCRIPTION_PAUSED
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'cancelled', false);
                break;

            case 11: // SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED
                // Pause schedule changed, keep current status
                break;

            case 12: // SUBSCRIPTION_REVOKED
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'cancelled', false);
                break;

            case 13: // SUBSCRIPTION_EXPIRED
                await updateSubscriptionStatus(req.app.locals.db, purchaseToken, 'expired', false);
                break;

            default:
                console.log(`Unhandled notification type: ${notificationType}`);
        }

        // Acknowledge receipt
        res.status(200).json({ status: 'received' });

    } catch (error) {
        console.error('Google webhook error:', error);
        // Return 200 to prevent retries
        res.status(200).json({ status: 'error', message: error.message });
    }
});

module.exports = router;
