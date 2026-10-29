from django.db import transaction

from .models import Order, OrderItem, Product


class OrderService:
    """Business rules for placing orders."""

    def place_order(self, customer, lines):
        with transaction.atomic():
            order = Order.objects.create(customer=customer)
            total = 0
            for product_id, qty in lines:
                product = Product.objects.get(pk=product_id)
                OrderItem.objects.create(order=order, product=product, quantity=qty)
                total += product.price * qty
            order.total = total
            order.save()
            return order

    def cancel(self, order):
        order.status = Order.Status.NEW
        order.save()
