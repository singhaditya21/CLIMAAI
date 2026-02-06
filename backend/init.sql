-- ClimaAI Database Schema
-- PostgreSQL 15+

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE NOT NULL,
    device_token VARCHAR(500),
    platform VARCHAR(20),
    preferences JSONB DEFAULT '{"temperature_unit": "celsius", "wind_speed_unit": "kmh", "precipitation_unit": "mm", "time_format": "24h", "notifications_enabled": true, "theme": "auto"}'::jsonb,
    default_latitude VARCHAR(50),
    default_longitude VARCHAR(50),
    default_location_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Create index on email for faster lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_platform ON users(platform);

-- Subscriptions table
CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('apple', 'google', 'web')),
    plan VARCHAR(20) NOT NULL CHECK (plan IN ('monthly', 'annual')),
    status VARCHAR(20) DEFAULT 'trial' NOT NULL CHECK (status IN ('trial', 'active', 'expired', 'cancelled', 'grace_period')),
    trial_start_date TIMESTAMP WITH TIME ZONE,
    trial_end_date TIMESTAMP WITH TIME ZONE,
    is_trial_used BOOLEAN DEFAULT FALSE NOT NULL,
    subscription_start_date TIMESTAMP WITH TIME ZONE,
    subscription_end_date TIMESTAMP WITH TIME ZONE,
    auto_renew BOOLEAN DEFAULT TRUE NOT NULL,
    apple_transaction_id VARCHAR(500),
    apple_original_transaction_id VARCHAR(500),
    google_purchase_token VARCHAR(500),
    google_order_id VARCHAR(500),
    last_validation_date TIMESTAMP WITH TIME ZONE,
    receipt_data TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Create indexes for subscriptions
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_subscriptions_apple_txn ON subscriptions(apple_transaction_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_google_token ON subscriptions(google_purchase_token);

-- Weather History table
CREATE TABLE IF NOT EXISTS weather_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    latitude FLOAT NOT NULL,
    longitude FLOAT NOT NULL,
    temperature FLOAT,
    pressure_msl FLOAT,
    humidity FLOAT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_weather_history_loc_time ON weather_history(latitude, longitude, created_at);


-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers to auto-update updated_at
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_subscriptions_updated_at
    BEFORE UPDATE ON subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_weather_history_updated_at
    BEFORE UPDATE ON weather_history
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert a demo user (password: Test1234)
INSERT INTO users (email, password_hash, full_name, platform, is_active, is_verified)
VALUES (
    'demo@climaai.com',
    '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYzpLaEkKC2',
    'Demo User',
    'ios',
    TRUE,
    TRUE
) ON CONFLICT (email) DO NOTHING;

-- Create initial schema version table
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

INSERT INTO schema_version (version) VALUES (1) ON CONFLICT DO NOTHING;
