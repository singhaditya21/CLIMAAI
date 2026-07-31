-- ClimaAI Database Schema Extensions (schema version 2)
-- Adds tables for favorite locations, device tokens, weather alerts.
--
-- Runs after init.sql via /docker-entrypoint-initdb.d. Every statement is
-- idempotent so this can also be applied by hand to an existing database:
--   psql -U climaai -d climaai -f 002_add_features.sql

-- Extension: notification preferences + password reset columns on users
ALTER TABLE users
ADD COLUMN IF NOT EXISTS notification_preferences JSONB
DEFAULT '{"weather_alerts": true, "daily_summary": false, "severe_weather": true}'::jsonb;

ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token_expires TIMESTAMP WITH TIME ZONE;

-- Table: Favorite Locations
CREATE TABLE IF NOT EXISTS favorite_locations (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_user_location UNIQUE (user_id, latitude, longitude)
);

CREATE INDEX IF NOT EXISTS idx_favorite_locations_user_id ON favorite_locations(user_id);
CREATE INDEX IF NOT EXISTS idx_favorite_locations_coords ON favorite_locations(latitude, longitude);

-- Table: Device Tokens for Push Notifications
CREATE TABLE IF NOT EXISTS device_tokens (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('ios', 'android', 'web')),
    device_info JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_device_tokens_platform ON device_tokens(platform);
CREATE INDEX IF NOT EXISTS idx_device_tokens_active ON device_tokens(is_active) WHERE is_active = TRUE;

-- Table: Weather Alerts
CREATE TABLE IF NOT EXISTS weather_alerts (
    id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    location_id INTEGER REFERENCES favorite_locations(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,  -- 'severe_weather', 'temperature_extreme', 'high_wind', 'heavy_rain', 'poor_aqi'
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MODERATE', 'HIGH', 'VERY_HIGH')),
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    metadata JSONB,
    is_read BOOLEAN DEFAULT FALSE,
    is_dismissed BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CHECK (user_id IS NOT NULL OR location_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_weather_alerts_user_id ON weather_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_weather_alerts_location_id ON weather_alerts(location_id);
CREATE INDEX IF NOT EXISTS idx_weather_alerts_type ON weather_alerts(alert_type);
CREATE INDEX IF NOT EXISTS idx_weather_alerts_created ON weather_alerts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_weather_alerts_unread ON weather_alerts(user_id, is_read) WHERE is_read = FALSE;

-- Triggers: reuse update_updated_at_column() defined in init.sql
DROP TRIGGER IF EXISTS favorite_locations_updated_at_trigger ON favorite_locations;
CREATE TRIGGER favorite_locations_updated_at_trigger
BEFORE UPDATE ON favorite_locations
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS device_tokens_updated_at_trigger ON device_tokens;
CREATE TRIGGER device_tokens_updated_at_trigger
BEFORE UPDATE ON device_tokens
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Demo data: Add a favorite location for demo user
INSERT INTO favorite_locations (user_id, name, latitude, longitude, is_default)
SELECT id, 'New York', 40.7128, -74.0060, TRUE
FROM users WHERE email = 'demo@climaai.com'
ON CONFLICT (user_id, latitude, longitude) DO NOTHING;

COMMENT ON TABLE favorite_locations IS 'User saved locations for quick weather access';
COMMENT ON TABLE device_tokens IS 'Device tokens for push notifications (APNs/FCM)';
COMMENT ON TABLE weather_alerts IS 'Weather alerts and warnings for users and locations';

INSERT INTO schema_version (version) VALUES (2) ON CONFLICT DO NOTHING;
