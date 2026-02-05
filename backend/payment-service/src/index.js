/**
 * ClimaAI Payment Webhook Service
 * Handles Apple App Store and Google Play subscription notifications
 */
const express = require('express');
const bodyParser = require('body-parser');
const { Pool } = require('pg');
require('dotenv').config();

const appleWebhook = require('./routes/apple-webhook');
const googleWebhook = require('./routes/google-webhook');

const app = express();
const port = process.env.PORT || 3000;

// Database connection pool
const pool = new Pool({
    host: process.env.DB_HOST || 'postgres',
    port: process.env.DB_PORT || 5432,
    user: process.env.DB_USER || 'climaai',
    password: process.env.DB_PASSWORD || 'climaai123',
    database: process.env.DB_NAME || 'climaai',
});

// Make pool available to routes
app.locals.db = pool;

// Middleware
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Health check
app.get('/health', (req, res) => {
    res.json({
        status: 'healthy',
        service: 'ClimaAI Payment Service',
        version: '1.0.0',
    });
});

// Webhook routes
app.use('/webhooks/apple', appleWebhook);
app.use('/webhooks/google', googleWebhook);

// Error handling
app.use((err, req, res, next) => {
    console.error('Error:', err);
    res.status(500).json({
        error: 'Internal server error',
        message: err.message,
    });
});

// Start server
if (require.main === module) {
    app.listen(port, () => {
        console.log(`🚀 Payment service listening on port ${port}`);
    });
}

// Graceful shutdown
process.on('SIGTERM', async () => {
    console.log('SIGTERM received, closing database pool...');
    await pool.end();
    process.exit(0);
});

module.exports = app;
