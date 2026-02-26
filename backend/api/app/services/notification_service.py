"""
Push notifications service for iOS (APNs) and Android (FCM).
"""
from typing import Optional, List
from datetime import datetime
import httpx
import jwt
import time
from sqlalchemy.ext.asyncio import AsyncSession
from ..config import get_settings

settings = get_settings()


class NotificationService:
    """Service for sending push notifications to iOS and Android devices."""
    
    def __init__(self):
        self.apns_key_id: Optional[str] = getattr(settings, 'APNS_KEY_ID', None)
        self.apns_team_id: Optional[str] = getattr(settings, 'APNS_TEAM_ID', None)
        self.apns_key_path: Optional[str] = getattr(settings, 'APNS_KEY_PATH', None)
        self.fcm_server_key: Optional[str] = getattr(settings, 'FCM_SERVER_KEY', None)
        self.apns_topic: Optional[str] = getattr(settings, 'APNS_TOPIC', 'com.climaai.app')
        
        # APNs URLs
        self.apns_production_url = "https://api.push.apple.com"
        self.apns_development_url = "https://api.development.push.apple.com"
        
        # FCM URL
        self.fcm_url = "https://fcm.googleapis.com/fcm/send"
    
    def _generate_apns_token(self) -> Optional[str]:
        """Generate APNs JWT token for authentication."""
        if not all([self.apns_key_id, self.apns_team_id, self.apns_key_path]):
            return None
        
        try:
            with open(self.apns_key_path, 'r') as f:
                apns_key = f.read()
            
            headers = {
                "alg": "ES256",
                "kid": self.apns_key_id
            }
            
            payload = {
                "iss": self.apns_team_id,
                "iat": int(time.time())
            }
            
            token = jwt.encode(payload, apns_key, algorithm="ES256", headers=headers)
            return token
        except Exception as e:
            print(f"Error generating APNs token: {e}")
            return None
    
    async def send_ios_notification(
        self,
        device_token: str,
        title: str,
        body: str,
        data: Optional[dict] = None,
        production: bool = True
    ) -> bool:
        """
        Send push notification to iOS device via APNs.
        """
        if not self.apns_key_id:
            print("APNs not configured, skipping iOS notification")
            return False
        
        token = self._generate_apns_token()
        if not token:
            return False
        
        url = self.apns_production_url if production else self.apns_development_url
        url = f"{url}/3/device/{device_token}"
        
        headers = {
            "authorization": f"bearer {token}",
            "apns-topic": self.apns_topic,
            "apns-push-type": "alert",
            "apns-priority": "10"
        }
        
        payload = {
            "aps": {
                "alert": {
                    "title": title,
                    "body": body
                },
                "sound": "default",
                "badge": 1
            }
        }
        
        if data:
            payload.update(data)
        
        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    url,
                    json=payload,
                    headers=headers,
                    timeout=10.0
                )
                
                if response.status_code == 200:
                    return True
                else:
                    print(f"APNs error: {response.status_code} - {response.text}")
                    return False
                    
        except Exception as e:
            print(f"Error sending iOS notification: {e}")
            return False
    
    async def send_android_notification(
        self,
        device_token: str,
        title: str,
        body: str,
        data: Optional[dict] = None
    ) -> bool:
        """
        Send push notification to Android device via FCM.
        """
        if not self.fcm_server_key:
            print("FCM not configured, skipping Android notification")
            return False
        
        headers = {
            "Authorization": f"key={self.fcm_server_key}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "to": device_token,
            "notification": {
                "title": title,
                "body": body,
                "sound": "default",
                "priority": "high"
            }
        }
        
        if data:
            payload["data"] = data
        
        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    self.fcm_url,
                    json=payload,
                    headers=headers,
                    timeout=10.0
                )
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get("success") == 1:
                        return True
                    else:
                        print(f"FCM error: {result}")
                        return False
                else:
                    print(f"FCM HTTP error: {response.status_code} - {response.text}")
                    return False
                    
        except Exception as e:
            print(f"Error sending Android notification: {e}")
            return False
    
    async def send_notification(
        self,
        platform: str,
        device_token: str,
        title: str,
        body: str,
        data: Optional[dict] = None
    ) -> bool:
        """
        Send push notification to specified platform.
        """
        if platform == "ios":
            return await self.send_ios_notification(device_token, title, body, data)
        elif platform == "android":
            return await self.send_android_notification(device_token, title, body, data)
        else:
            print(f"Unsupported platform: {platform}")
            return False
    
    async def send_to_user(
        self,
        user_id: int,
        title: str,
        body: str,
        data: Optional[dict] = None,
        db: AsyncSession = None
    ) -> int:
        """
        Send notification to all of a user's devices.
        Returns count of successful sends.
        """
        if not db:
            return 0
        
        try:
            # Get user's active device tokens
            query = f"""
                SELECT token, platform
                FROM device_tokens
                WHERE user_id = {user_id} AND is_active = TRUE
            """
            result = await db.execute(query)
            devices = result.fetchall()
            
            success_count = 0
            for device_token, platform in devices:
                success = await self.send_notification(platform, device_token, title, body, data)
                if success:
                    success_count += 1

                    # Update last_used_at
                    await db.execute(
                        f"""
                        UPDATE device_tokens
                        SET last_used_at = NOW()
                        WHERE token = '{device_token}'
                        """
                    )
            
            await db.commit()
            return success_count
            
        except Exception as e:
            print(f"Error sending notifications to user {user_id}: {e}")
            return 0
    
    async def send_weather_alert(
        self,
        user_id: int,
        alert_type: str,
        severity: str,
        message: str,
        db: AsyncSession
    ):
        """
        Send weather alert notification to user.
        """
        severity_emoji = {
            "LOW": "ℹ️",
            "MODERATE": "⚠️",
            "HIGH": "🚨",
            "VERY_HIGH": "🔴"
        }
        
        emoji = severity_emoji.get(severity, "⚠️")
        title = f"{emoji} Weather Alert"
        
        data = {
            "type": "weather_alert",
            "alert_type": alert_type,
            "severity": severity
        }
        
        await self.send_to_user(user_id, title, message, data, db)
    
    async def send_subscription_reminder(
        self,
        user_id: int,
        days_remaining: int,
        db: AsyncSession
    ):
        """
        Send trial ending reminder notification.
        """
        title = "🎁 Trial Ending Soon"
        body = f"Your ClimaAI free trial ends in {days_remaining} day{'s' if days_remaining != 1 else ''}. Subscribe to keep premium features!"
        
        data = {
            "type": "subscription_reminder",
            "days_remaining": days_remaining
        }
        
        await self.send_to_user(user_id, title, body, data, db)
    
    async def register_device_token(
        self,
        user_id: int,
        token: str,
        platform: str,
        device_info: Optional[dict],
        db: AsyncSession
    ):
        """
        Register or update a device token.
        """
        try:
            import json
            device_info_json = json.dumps(device_info) if device_info else '{}'
            
            query = f"""
                INSERT INTO device_tokens (user_id, token, platform, device_info)
                VALUES ({user_id}, '{token}', '{platform}', '{device_info_json}'::jsonb)
                ON CONFLICT (token)
                DO UPDATE SET 
                    user_id = EXCLUDED.user_id,
                    platform = EXCLUDED.platform,
                    device_info = EXCLUDED.device_info,
                    is_active = TRUE,
                    last_used_at = NOW()
            """
            await db.execute(query)
            await db.commit()
            return True
        except Exception as e:
            await db.rollback()
            print(f"Error registering device token: {e}")
            return False
    
    async def unregister_device_token(
        self,
        token: str,
        db: AsyncSession
    ):
        """
        Deactivate a device token.
        """
        try:
            query = f"""
                UPDATE device_tokens
                SET is_active = FALSE
                WHERE token = '{token}'
            """
            await db.execute(query)
            await db.commit()
            return True
        except Exception as e:
            await db.rollback()
            print(f"Error unregistering device token: {e}")
            return False
