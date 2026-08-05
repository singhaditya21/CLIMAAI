/**
 * Google Play Developer Notifications webhook handler
 */
const express = require('express');
const { OAuth2Client } = require('google-auth-library');
const router = express.Router();

// Google Play RTDNs arrive as Cloud Pub/Sub push requests. Pub/Sub proves who
// it is with an OIDC token in the Authorization header and nothing else — the
// message body carries no signature — so without this check the endpoint takes
// subscription state changes from anyone who can reach it.
const PUBSUB_AUDIENCE = process.env.PUBSUB_AUDIENCE || '';
const PUBSUB_SERVICE_ACCOUNT_EMAIL = process.env.PUBSUB_SERVICE_ACCOUNT_EMAIL || '';
const EXPECTED_PACKAGE_NAME = process.env.GOOGLE_PACKAGE_NAME || '';

const authClient = new OAuth2Client();

class PushVerificationError extends Error {
    constructor(message) {
        super(message);
        this.name = 'PushVerificationError';
    }
}

const isConfigured = () => Boolean(PUBSUB_AUDIENCE && PUBSUB_SERVICE_ACCOUNT_EMAIL && EXPECTED_PACKAGE_NAME);

if (!isConfigured()) {
    console.error(
        'Google webhook DISABLED: set PUBSUB_AUDIENCE, PUBSUB_SERVICE_ACCOUNT_EMAIL and ' +
        'GOOGLE_PACKAGE_NAME, and configure the Pub/Sub push subscription to sign requests ' +
        'with that service account. Until then every notification is rejected.'
    );
}

/**
 * Verify the Pub/Sub push OIDC token.
 * Throws PushVerificationError if the caller is not our Pub/Sub subscription.
 */
async function verifyPushRequest(req) {
    const header = req.get('authorization') || '';
    const [scheme, token] = header.split(' ');

    if (!token || scheme.toLowerCase() !== 'bearer') {
        throw new PushVerificationError('no bearer token on the push request');
    }

    let claims;
    try {
        // Checks Google's signature, the issuer, expiry and our audience.
        const ticket = await authClient.verifyIdToken({ idToken: token, audience: PUBSUB_AUDIENCE });
        claims = ticket.getPayload();
    } catch (error) {
        throw new PushVerificationError(`OIDC token rejected: ${error.message}`);
    }

    // Any Google account can mint a token for our audience; only the service
    // account attached to our push subscription may act on it.
    if (!claims.email_verified || claims.email !== PUBSUB_SERVICE_ACCOUNT_EMAIL) {
        throw new PushVerificationError(`OIDC token belongs to ${claims.email}, not the configured push service account`);
    }
}

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
    if (!isConfigured()) {
        console.error('Google webhook: rejected a notification because push verification is not configured.');
        return res.status(503).json({ error: 'Notification verification is not configured' });
    }

    try {
        await verifyPushRequest(req);
    } catch (error) {
        if (error instanceof PushVerificationError) {
            // Loud on purpose: either the push subscription is misconfigured or
            // somebody is trying to change subscription state directly.
            console.error('Google webhook: REJECTED unverified notification:', error.message);
            return res.status(401).json({ error: 'Unauthenticated push request' });
        }
        // Express 4 does not catch rejections from an async handler, so an
        // unexpected failure has to be answered here or the request hangs.
        console.error('Google webhook: verification failed unexpectedly:', error);
        return res.status(500).json({ error: 'Could not verify notification' });
    }

    try {
        const { message } = req.body || {};

        if (!message || !message.data) {
            return res.status(400).json({ error: 'Invalid request format' });
        }

        // Decode base64 data
        let data;
        try {
            data = JSON.parse(Buffer.from(message.data, 'base64').toString());
        } catch (error) {
            return res.status(400).json({ error: 'Message data is not JSON' });
        }

        const { packageName, subscriptionNotification, testNotification } = data;

        // The push subscription is ours, but the message it carries still has to
        // be about our app.
        if (packageName !== EXPECTED_PACKAGE_NAME) {
            console.error(`Google webhook: notification for package ${packageName}, expected ${EXPECTED_PACKAGE_NAME}`);
            return res.status(400).json({ error: 'Notification is for a different package' });
        }

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

        const db = req.app.locals.db;
        let updated;

        // Handle notification types
        // https://developer.android.com/google/play/billing/rtdn-reference
        switch (notificationType) {
            case 1: // SUBSCRIPTION_RECOVERED
                updated = await updateSubscriptionStatus(db, purchaseToken, 'active');
                break;

            case 2: // SUBSCRIPTION_RENEWED
                updated = await updateSubscriptionStatus(db, purchaseToken, 'active');
                break;

            case 3: // SUBSCRIPTION_CANCELED
                updated = await updateSubscriptionStatus(db, purchaseToken, 'cancelled', false);
                break;

            case 4: // SUBSCRIPTION_PURCHASED
                updated = await updateSubscriptionStatus(db, purchaseToken, 'active');
                break;

            case 5: // SUBSCRIPTION_ON_HOLD
                updated = await updateSubscriptionStatus(db, purchaseToken, 'grace_period');
                break;

            case 6: // SUBSCRIPTION_IN_GRACE_PERIOD
                updated = await updateSubscriptionStatus(db, purchaseToken, 'grace_period');
                break;

            case 7: // SUBSCRIPTION_RESTARTED
                updated = await updateSubscriptionStatus(db, purchaseToken, 'active');
                break;

            case 8: // SUBSCRIPTION_PRICE_CHANGE_CONFIRMED
                // Price change accepted
                updated = await updateSubscriptionStatus(db, purchaseToken, 'active');
                break;

            case 9: // SUBSCRIPTION_DEFERRED
                // Payment deferred
                updated = await updateSubscriptionStatus(db, purchaseToken, 'grace_period');
                break;

            case 10: // SUBSCRIPTION_PAUSED
                updated = await updateSubscriptionStatus(db, purchaseToken, 'cancelled', false);
                break;

            case 11: // SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED
                // Pause schedule changed, keep current status
                return res.status(200).json({ status: 'received' });

            case 12: // SUBSCRIPTION_REVOKED
                updated = await updateSubscriptionStatus(db, purchaseToken, 'cancelled', false);
                break;

            case 13: // SUBSCRIPTION_EXPIRED
                updated = await updateSubscriptionStatus(db, purchaseToken, 'expired', false);
                break;

            default:
                console.log(`Unhandled notification type: ${notificationType}`);
                return res.status(200).json({ status: 'ignored' });
        }

        if (!updated) {
            console.warn(`Google webhook: no subscription row for purchase token ${purchaseToken}`);
            return res.status(200).json({ status: 'unknown purchase token' });
        }

        res.status(200).json({ status: 'received' });

    } catch (error) {
        // An authenticated notification we failed to apply must not be
        // acknowledged: 200 stops Pub/Sub retrying and the row stays stale.
        console.error('Google webhook error:', error);
        res.status(500).json({ error: 'Could not apply notification' });
    }
});

module.exports = router;
