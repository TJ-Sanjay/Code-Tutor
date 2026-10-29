from django.conf import settings
from django.db import models

from .managers import ProductQuerySet


class TimeStamped(models.Model):
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True


class Category(TimeStamped):
    name = models.CharField(max_length=80, unique=True)
    slug = models.SlugField()

    def __str__(self):
        return self.name


class Product(TimeStamped):
    name = models.CharField(max_length=120)
    description = models.TextField(blank=True)
    price = models.DecimalField(max_digits=8, decimal_places=2)
    stock = models.IntegerField(default=0)
    category = models.ForeignKey(Category, on_delete=models.CASCADE, related_name="products")
    tags = models.ManyToManyField("Tag", blank=True)

    objects = ProductQuerySet.as_manager()

    class Meta:
        ordering = ["name"]


class Tag(models.Model):
    label = models.CharField(max_length=40)


class Order(TimeStamped):
    class Status(models.TextChoices):
        NEW = "new"
        PAID = "paid"
        SHIPPED = "shipped"

    customer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    status = models.CharField(max_length=10, choices=Status.choices, default=Status.NEW)
    total = models.DecimalField(max_digits=10, decimal_places=2, default=0)


class OrderItem(models.Model):
    order = models.ForeignKey(Order, on_delete=models.CASCADE, related_name="items")
    product = models.ForeignKey(Product, on_delete=models.PROTECT)
    quantity = models.PositiveIntegerField(default=1)


class Invoice(models.Model):
    order = models.OneToOneField(Order, on_delete=models.CASCADE)
    number = models.CharField(max_length=20, unique=True)
