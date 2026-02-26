"""
User management router.
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from ..database import get_db
from ..models import User
from ..schemas.user import UserCreate, UserLogin, UserUpdate, UserResponse, TokenResponse, ForgotPasswordRequest
from ..services.auth import hash_password, verify_password, create_access_token, get_current_user
from ..services.email import email_service
import uuid
from datetime import datetime, timedelta

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
async def register(user_data: UserCreate, db: AsyncSession = Depends(get_db)):
    """Register a new user."""
    # Check if user exists
    result = await db.execute(select(User).where(User.email == user_data.email))
    existing_user = result.scalar_one_or_none()
    
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered"
        )
    
    # Generate verification token
    verification_token = str(uuid.uuid4())

    # Create user
    user = User(
        email=user_data.email,
        password_hash=hash_password(user_data.password),
        full_name=user_data.full_name,
        platform=user_data.platform,
        device_token=user_data.device_token,
        is_active=True,
        is_verified=False,
        verification_token=verification_token,
        verification_token_expires=datetime.utcnow() + timedelta(hours=24)
    )
    
    db.add(user)
    await db.commit()
    await db.refresh(user)
    
    # Send verification email
    try:
        await email_service.send_verification_email(user.email, verification_token)
    except Exception:
        # Log error but don't fail registration
        pass

    # Create access token
    access_token = create_access_token(data={"sub": str(user.id)})
    
    return TokenResponse(
        access_token=access_token,
        user=UserResponse.model_validate(user)
    )


@router.get("/verify-email", status_code=status.HTTP_200_OK)
async def verify_email(token: str, db: AsyncSession = Depends(get_db)):
    """Verify email address with token."""
    result = await db.execute(select(User).where(User.verification_token == token))
    user = result.scalar_one_or_none()

    if not user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid verification token"
        )

    # Check expiry
    # Convert DB timestamp to naive UTC for comparison if it's aware
    expiry = user.verification_token_expires
    if expiry and expiry.tzinfo:
        expiry = expiry.replace(tzinfo=None)

    if expiry and expiry < datetime.utcnow():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Verification token expired"
        )

    user.is_verified = True
    user.verification_token = None
    user.verification_token_expires = None

    await db.commit()

    return {"message": "Email verified successfully"}


@router.post("/resend-verification", status_code=status.HTTP_200_OK)
async def resend_verification(
    request: ForgotPasswordRequest,
    db: AsyncSession = Depends(get_db)
):
    """Resend verification email."""
    result = await db.execute(select(User).where(User.email == request.email))
    user = result.scalar_one_or_none()

    if user and not user.is_verified:
        token = str(uuid.uuid4())
        user.verification_token = token
        user.verification_token_expires = datetime.utcnow() + timedelta(hours=24)

        await db.commit()

        try:
            await email_service.send_verification_email(user.email, token)
        except Exception:
            pass

    return {"message": "If the account exists and is unverified, a verification email has been sent."}


@router.post("/forgot-password", status_code=status.HTTP_200_OK)
async def forgot_password(
    request: ForgotPasswordRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    Request a password reset email.
    """
    # Check if user exists
    result = await db.execute(select(User).where(User.email == request.email))
    user = result.scalar_one_or_none()

    if user:
        # Generate token
        token = str(uuid.uuid4())

        # Save token
        user.reset_token = token
        # Using utcnow() is deprecated in newer python but safe here or use timezone aware
        user.reset_token_expires = datetime.utcnow() + timedelta(hours=1)

        await db.commit()

        # Log email (mock sending)
        print(f"📧 [Mock Email] Password reset for {request.email}. Token: {token}")
        print(f"   Link: https://climaai.app/reset-password?token={token}")

    # Always return success to prevent email enumeration
    return {"message": "If an account exists with this email, a password reset link has been sent."}


@router.post("/login", response_model=TokenResponse)
async def login(credentials: UserLogin, db: AsyncSession = Depends(get_db)):
    """Login user."""
    # Get user
    result = await db.execute(select(User).where(User.email == credentials.email))
    user = result.scalar_one_or_none()
    
    if not user or not verify_password(credentials.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Inactive user"
        )
    
    # Create access token
    access_token = create_access_token(data={"sub": str(user.id)})
    
    return TokenResponse(
        access_token=access_token,
        user=UserResponse.model_validate(user)
    )


@router.get("/me", response_model=UserResponse)
async def get_current_user_info(current_user: User = Depends(get_current_user)):
    """Get current user information."""
    return UserResponse.model_validate(current_user)


@router.put("/me", response_model=UserResponse)
async def update_user(
    user_update: UserUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Update current user."""
    if user_update.full_name is not None:
        current_user.full_name = user_update.full_name
    
    if user_update.preferences is not None:
        current_user.preferences = user_update.preferences.model_dump()
    
    if user_update.default_latitude is not None:
        current_user.default_latitude = user_update.default_latitude
    
    if user_update.default_longitude is not None:
        current_user.default_longitude = user_update.default_longitude
    
    if user_update.default_location_name is not None:
        current_user.default_location_name = user_update.default_location_name
    
    if user_update.device_token is not None:
        current_user.device_token = user_update.device_token
    
    await db.commit()
    await db.refresh(current_user)
    
    return UserResponse.model_validate(current_user)


@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_user(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Delete current user account."""
    await db.delete(current_user)
    await db.commit()
    return None
