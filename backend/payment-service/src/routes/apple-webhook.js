/**
 * Apple App Store Server Notifications V2 webhook handler
 */
const express = require('express');
const router = express.Router();
const { verifyAppleJWS } = require('../utils/apple-cert-verifier');

/**
 * Update subscription status in database
 */
async function updateSubscriptionStatus(db, transactionId, status, autoRenew = true) {
    const query = `
    UPDATE subscriptions
    SET status = $1,
        auto_renew = $2,
        last_validation_date = NOW(),
        updated_at = NOW()
    WHERE apple_transaction_id = $3
    RETURNING *
  `;

    try {
        const result = await db.query(query, [status, autoRenew, transactionId]);
        return result.rows[0];
    } catch (error) {
        console.error('Database update error:', error);
        throw error;
    }
}

/**
 * Apple webhook endpoint
 */
router.post('/', async (req, res) => {
    try {
        const { signedPayload } = req.body;

        if (!signedPayload) {
            return res.status(400).json({ error: 'Missing signedPayload' });
        }

        // Verify and decode the payload
        let payload;
        try {
            payload = verifyAppleJWS(signedPayload);
        } catch (error) {
            console.error('JWS verification error:', error.message);
            return res.status(400).json({ error: 'Invalid signature', details: error.message });
        }

        const { notificationType, subtype, data } = payload;
        const transactionInfo = data?.transactionInfo;
        const originalTransactionId = transactionInfo?.originalTransactionId;

        console.log(`Received Apple notification: ${notificationType} - ${subtype}`);

        // Handle different notification types
        switch (notificationType) {
            case 'SUBSCRIBED':
                // New subscription
                await updateSubscriptionStatus(req.app.locals.db, originalTransactionId, 'active');
                break;

            case 'DID_RENEW':
                // Subscription renewed
                await updateSubscriptionStatus(req.app.locals.db, originalTransactionId, 'active');
                break;

            case 'DID_CHANGE_RENEWAL_STATUS':
                // Auto-renew status changed
                const autoRenew = subtype === 'AUTO_RENEW_ENABLED';
                await updateSubscriptionStatus(req.app.locals.db, originalTransactionId, 'active', autoRenew);
                break;

            case 'EXPIRED':
                // Subscription expired
                await updateSubscriptionStatus(req.app.locals.db, originalTransactionId, 'expired', false);
                break;

            case 'DID_FAIL_TO_RENEW':
                // Renewal failed
                await updateSubscriptionStatus(req.app.locals.db, originalTransactionId, 'grace_period');
                break;

            case 'REVOKE':
                // Subscription revoked (refund)
                await updateSubscriptionStatus(req.app.locals.db, originalTransactionId, 'cancelled', false);
                break;

            default:
                console.log(`Unhandled notification type: ${notificationType}`);
        }

        // Always return 200 to acknowledge receipt
        res.status(200).json({ status: 'received' });

    } catch (error) {
        console.error('Apple webhook error:', error);
        // Still return 200 to prevent retries for errors we can't fix
        res.status(200).json({ status: 'error', message: error.message });
    }
});

module.exports = router;
