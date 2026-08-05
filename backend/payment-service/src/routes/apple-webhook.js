/**
 * Apple App Store Server Notifications V2 webhook handler
 */
const express = require('express');
const { JwsVerificationError, loadTrustAnchors, verifyAppleJws } = require('../security/apple-jws');
const router = express.Router();

// Anything but 'Production' here accepts notifications signed by Apple's
// sandbox, which anyone with a free sandbox tester account can generate at
// will. The chain check alone would happily let those through.
const EXPECTED_ENVIRONMENT = process.env.APPLE_ENVIRONMENT || 'Production';
const EXPECTED_BUNDLE_ID = process.env.APPLE_BUNDLE_ID || '';

/**
 * Pinned Apple root certificate(s), loaded once at boot.
 *
 * An empty list is not a degraded mode — it disables the endpoint entirely.
 * Granting entitlements from an unverified payload is the same as having no
 * paywall at all, so the only safe behaviour without a trust anchor is to
 * reject every notification.
 */
let trustAnchors = [];
try {
    trustAnchors = loadTrustAnchors();
} catch (error) {
    console.error('Apple webhook: could not read the configured root certificate:', error.message);
}

if (trustAnchors.length === 0) {
    console.error(
        'Apple webhook DISABLED: set APPLE_ROOT_CA_PATH (or APPLE_ROOT_CA_PEM) to the ' +
        'Apple Root CA - G3 certificate from https://www.apple.com/certificateauthority/. ' +
        'Until then every notification is rejected.'
    );
}
if (!EXPECTED_BUNDLE_ID) {
    console.error('Apple webhook DISABLED: APPLE_BUNDLE_ID is not set, so notifications cannot be attributed to this app.');
}

const isConfigured = () => trustAnchors.length > 0 && Boolean(EXPECTED_BUNDLE_ID);

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
 * Verify the outer notification and the transaction it carries.
 * Throws JwsVerificationError if anything about it is untrustworthy.
 */
function verifyNotification(signedPayload) {
    const payload = verifyAppleJws(signedPayload, { roots: trustAnchors });
    const data = payload.data || {};

    if (data.bundleId !== EXPECTED_BUNDLE_ID) {
        throw new JwsVerificationError(`notification is for bundle ${JSON.stringify(data.bundleId)}, not ${EXPECTED_BUNDLE_ID}`);
    }
    if (data.environment !== EXPECTED_ENVIRONMENT) {
        throw new JwsVerificationError(`notification is from the ${data.environment} environment, expected ${EXPECTED_ENVIRONMENT}`);
    }

    // V2 nests the transaction as its own JWS. The previous handler read
    // data.transactionInfo, a field Apple does not send, so the transaction id
    // was always undefined and every UPDATE matched zero rows.
    if (typeof data.signedTransactionInfo !== 'string') {
        throw new JwsVerificationError('notification carries no signedTransactionInfo');
    }
    const transactionInfo = verifyAppleJws(data.signedTransactionInfo, { roots: trustAnchors });

    if (transactionInfo.bundleId !== EXPECTED_BUNDLE_ID) {
        throw new JwsVerificationError('transaction belongs to a different app than the notification');
    }
    if (!transactionInfo.originalTransactionId) {
        throw new JwsVerificationError('transaction carries no originalTransactionId');
    }

    return { payload, transactionInfo };
}

/**
 * Apple webhook endpoint
 */
router.post('/', async (req, res) => {
    if (!isConfigured()) {
        console.error('Apple webhook: rejected a notification because verification is not configured.');
        return res.status(503).json({ error: 'Notification verification is not configured' });
    }

    const { signedPayload } = req.body || {};

    if (!signedPayload) {
        return res.status(400).json({ error: 'Missing signedPayload' });
    }

    let payload;
    let transactionInfo;
    try {
        ({ payload, transactionInfo } = verifyNotification(signedPayload));
    } catch (error) {
        if (error instanceof JwsVerificationError) {
            // Loud on purpose: a failure here is either a misconfiguration or
            // somebody trying to mint themselves a subscription.
            console.error('Apple webhook: REJECTED unverified notification:', error.message);
            return res.status(401).json({ error: 'Invalid signature' });
        }
        // Express 4 does not catch rejections from an async handler, so an
        // unexpected failure has to be answered here or the request hangs.
        console.error('Apple webhook: verification failed unexpectedly:', error);
        return res.status(500).json({ error: 'Could not verify notification' });
    }

    try {
        const { notificationType, subtype } = payload;
        const originalTransactionId = transactionInfo.originalTransactionId;

        console.log(`Received Apple notification: ${notificationType} - ${subtype}`);

        const db = req.app.locals.db;
        let updated;

        // Handle different notification types
        switch (notificationType) {
            case 'SUBSCRIBED':
                // New subscription
                updated = await updateSubscriptionStatus(db, originalTransactionId, 'active');
                break;

            case 'DID_RENEW':
                // Subscription renewed
                updated = await updateSubscriptionStatus(db, originalTransactionId, 'active');
                break;

            case 'DID_CHANGE_RENEWAL_STATUS': {
                // Auto-renew status changed
                const autoRenew = subtype === 'AUTO_RENEW_ENABLED';
                updated = await updateSubscriptionStatus(db, originalTransactionId, 'active', autoRenew);
                break;
            }

            case 'EXPIRED':
                // Subscription expired
                updated = await updateSubscriptionStatus(db, originalTransactionId, 'expired', false);
                break;

            case 'DID_FAIL_TO_RENEW':
                // Renewal failed
                updated = await updateSubscriptionStatus(db, originalTransactionId, 'grace_period');
                break;

            case 'REVOKE':
                // Subscription revoked (refund)
                updated = await updateSubscriptionStatus(db, originalTransactionId, 'cancelled', false);
                break;

            default:
                console.log(`Unhandled notification type: ${notificationType}`);
                return res.status(200).json({ status: 'ignored' });
        }

        if (!updated) {
            console.warn(`Apple webhook: no subscription row for original transaction ${originalTransactionId}`);
            return res.status(200).json({ status: 'unknown transaction' });
        }

        res.status(200).json({ status: 'received' });

    } catch (error) {
        // A verified notification we failed to apply must not be acknowledged:
        // 200 would tell Apple to stop retrying and the row would stay stale.
        console.error('Apple webhook error:', error);
        res.status(500).json({ error: 'Could not apply notification' });
    }
});

module.exports = router;
