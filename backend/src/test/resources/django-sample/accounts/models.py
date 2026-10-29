from django.contrib.auth.models import AbstractUser
from django.db import models


class User(AbstractUser):
    """Shop user: a customer or a member of staff."""

    phone = models.CharField(max_length=20, blank=True)
    loyalty_points = models.PositiveIntegerField(default=0)
