from rest_framework.permissions import BasePermission


class IsOwner(BasePermission):
    """Only the customer who placed an order may see it."""

    def has_object_permission(self, request, view, obj):
        return obj.customer == request.user
