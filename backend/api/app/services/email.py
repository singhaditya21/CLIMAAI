"""
Email service for sending notifications and verification emails.
"""
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from ..config import get_settings
import asyncio
import logging

logger = logging.getLogger(__name__)

class EmailService:
    def __init__(self):
        self.settings = get_settings()

    def send_email(self, to_email: str, subject: str, body: str):
        """
        Send an email using the configured backend.
        """
        if self.settings.EMAIL_BACKEND == "console":
            print(f"📧 [Mock Email] To: {to_email}")
            print(f"   Subject: {subject}")
            print(f"   Body: {body}")
            return

        if self.settings.EMAIL_BACKEND == "smtp":
            try:
                msg = MIMEMultipart()
                msg["From"] = self.settings.EMAIL_FROM
                msg["To"] = to_email
                msg["Subject"] = subject
                msg.attach(MIMEText(body, "plain"))

                with smtplib.SMTP(self.settings.SMTP_HOST, self.settings.SMTP_PORT) as server:
                    server.starttls()
                    if self.settings.SMTP_USER and self.settings.SMTP_PASSWORD:
                        server.login(self.settings.SMTP_USER, self.settings.SMTP_PASSWORD)
                    server.send_message(msg)
            except Exception as e:
                logger.error(f"Failed to send email to {to_email}: {e}")
                # We log but might not want to raise to prevent API failure on email error
                # depending on criticality. For verification, it's critical.
                raise

    async def send_email_async(self, to_email: str, subject: str, body: str):
        """
        Send an email asynchronously.
        """
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(None, self.send_email, to_email, subject, body)

    async def send_verification_email(self, to_email: str, token: str):
        """
        Send verification email with token.
        """
        subject = "Verify your email for ClimaAI"
        # In a real app, this would be a link to the frontend which then calls the API
        # Or a link to the API directly if handling verification server-side only
        # Assuming we want to link to a web verification page or deep link
        verification_link = f"https://climaai.app/verify-email?token={token}"

        body = f"""Welcome to ClimaAI!

Please verify your email address by clicking the link below:

{verification_link}

If you did not sign up for ClimaAI, please ignore this email.

Best regards,
The ClimaAI Team
"""
        await self.send_email_async(to_email, subject, body)

    async def send_password_reset_email(self, to_email: str, token: str):
        """
        Send password reset email.
        """
        subject = "Reset your password for ClimaAI"
        reset_link = f"https://climaai.app/reset-password?token={token}"

        body = f"""Hello,

We received a request to reset your password. Click the link below to choose a new password:

{reset_link}

If you did not request a password reset, please ignore this email.

Best regards,
The ClimaAI Team
"""
        await self.send_email_async(to_email, subject, body)

email_service = EmailService()
