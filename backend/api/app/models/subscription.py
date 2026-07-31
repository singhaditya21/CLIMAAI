"""
Subscription model for tracking premium features.
"""
from sqlalchemy import Column, String, Boolean, DateTime, ForeignKey, Enum as SQLEnum, Uuid
from sqlalchemy.orm import relationship
import uuid
import enum
from ..database import Base


class SubscriptionStatus(str, enum.Enum):
    TRIAL = "trial"
    ACTIVE = "active"
    EXPIRED = "expired"
    CANCELLED = "cancelled"
    GRACE_PERIOD = "grace_period"


class SubscriptionPlatform(str, enum.Enum):
    APPLE = "apple"
    GOOGLE = "google"
    WEB = "web"


class SubscriptionPlan(str, enum.Enum):
    MONTHLY = "monthly"
    ANNUAL = "annual"


def _enum_column(enum_class):
    """Map a Python enum onto the schema's VARCHAR + CHECK columns.

    init.sql declares platform/plan/status as VARCHAR(20) with CHECK constraints
    holding the lowercase values. SQLAlchemy's default would instead expect a
    native Postgres ENUM type and persist member *names* ("APPLE"), so every
    insert failed — the type does not exist, and the name would violate the
    CHECK even if it did.
    """
    return SQLEnum(
        enum_class,
        native_enum=False,
        create_constraint=False,
        values_callable=lambda enum: [member.value for member in enum],
        length=20,
    )


class Subscription(Base):
    __tablename__ = "subscriptions"

    id = Column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)

    # Subscription details
    platform = Column(_enum_column(SubscriptionPlatform), nullable=False)
    plan = Column(_enum_column(SubscriptionPlan), nullable=False)
    status = Column(_enum_column(SubscriptionStatus), default=SubscriptionStatus.TRIAL, nullable=False, index=True)
    
    # Trial
    trial_start_date = Column(DateTime(timezone=True))
    trial_end_date = Column(DateTime(timezone=True))
    is_trial_used = Column(Boolean, default=False, nullable=False)
    
    # Billing
    subscription_start_date = Column(DateTime(timezone=True))
    subscription_end_date = Column(DateTime(timezone=True))
    auto_renew = Column(Boolean, default=True, nullable=False)
    
    # Platform-specific identifiers
    apple_transaction_id = Column(String(500))
    apple_original_transaction_id = Column(String(500))
    google_purchase_token = Column(String(500))
    google_order_id = Column(String(500))
    
    # Receipt validation
    last_validation_date = Column(DateTime(timezone=True))
    receipt_data = Column(String)  # Encrypted/encoded receipt
    
    # Relationships
    user = relationship("User", back_populates="subscriptions")
    
    def __repr__(self):
        return f"<Subscription {self.id} - {self.status}>"
    
    @property
    def is_active(self) -> bool:
        """Check if subscription is currently active."""
        return self.status in [SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL, SubscriptionStatus.GRACE_PERIOD]
